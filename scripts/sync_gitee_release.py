#!/usr/bin/env python3
"""同步 GitHub Release 到 Gitee；附件总量超额度时，清理最早版本并标注归档。"""
import os
import sys
import tempfile

import requests

GH_API = "https://api.github.com"
GITEE_API = "https://gitee.com/api/v5"

GITEE_OWNER = os.getenv("GITEE_OWNER", "kjllin")
GITEE_REPO = os.getenv("GITEE_REPO", "hpcl-launcher")
GITEE_TOKEN = os.getenv("GITEE_TOKEN", "")
GH_REPO = os.getenv("GH_REPO", "KJLLin/HPCL-Launcher")
GH_TOKEN = os.getenv("GITHUB_TOKEN", "")
ONLY_TAG = os.getenv("TAG", "").strip()
MAX_ASSET_MB = int(os.getenv("MAX_ASSET_MB", "100"))
MAX_TOTAL_MB = float(os.getenv("MAX_TOTAL_MB", "900"))
BUDGET = int(MAX_TOTAL_MB * 1024 * 1024)
KEEP_STABLE = int(os.getenv("KEEP_STABLE", "2"))
KEEP_PRERELEASE = int(os.getenv("KEEP_PRERELEASE", "2"))
ARCHIVE_NOTE = os.getenv("ARCHIVE_NOTE", "").strip()

gh = requests.Session()
gh.headers["Accept"] = "application/vnd.github+json"
if GH_TOKEN:
    gh.headers["Authorization"] = f"Bearer {GH_TOKEN}"

params = {"access_token": GITEE_TOKEN}


def mb(value):
    return f"{value / 1048576:.1f} MB"


def gh_releases():
    page = 1
    while True:
        resp = gh.get(
            f"{GH_API}/repos/{GH_REPO}/releases",
            params={"per_page": 100, "page": page},
            timeout=30,
        )
        resp.raise_for_status()
        batch = resp.json()
        if not batch:
            return
        yield from batch
        page += 1


def gitee(method, path, **kwargs):
    kwargs.setdefault("timeout", 30)
    resp = requests.request(method, f"{GITEE_API}{path}", **kwargs)
    if resp.status_code >= 400:
        raise RuntimeError(f"{method} {path} -> {resp.status_code} {resp.text[:200]}")
    return resp.json() if resp.content else {}


def gitee_releases():
    page, items = 1, []
    while True:
        batch = gitee(
            "GET",
            f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases",
            params={**params, "per_page": 100, "page": page},
        )
        if not batch:
            return items
        items += batch
        page += 1


def gitee_release(tag):
    resp = requests.get(
        f"{GITEE_API}/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/tags/{tag}",
        params=params,
        timeout=30,
    )
    if resp.status_code == 404:
        return None
    resp.raise_for_status()
    return resp.json()


def gitee_create(release):
    data = {
        "access_token": GITEE_TOKEN,
        "tag_name": release["tag_name"],
        "name": release.get("name") or release["tag_name"],
        "body": release.get("body") or "",
        "prerelease": "true" if release.get("prerelease") else "false",
        "target_commitish": "main",
    }
    return gitee("POST", f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases", data=data)


def delete_asset(release_id, asset_id):
    gitee(
        "DELETE",
        f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release_id}/attach_files/{asset_id}",
        params=params,
    )


def mark_archived(release):
    if not ARCHIVE_NOTE:
        return
    body = release.get("body") or ""
    if ARCHIVE_NOTE in body:
        return
    gitee(
        "PATCH",
        f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release['id']}",
        data={"access_token": GITEE_TOKEN, "body": f"{body}\n\n{ARCHIVE_NOTE}".strip()},
    )
    print(f"    ~ 已标注归档说明：{release.get('tag_name')}")


def total_bytes(releases):
    return sum(a.get("size") or 0 for r in releases for a in (r.get("assets") or []))


def protected_ids(releases):
    """最新的 N 个稳定版 + N 个测试版永不清理。"""
    keep = set()
    for prerelease, limit in ((False, KEEP_STABLE), (True, KEEP_PRERELEASE)):
        group = [r for r in releases if bool(r.get("prerelease")) == prerelease]
        group.sort(key=lambda r: r.get("created_at") or "", reverse=True)
        keep.update(r["id"] for r in group[:limit])
    return keep


def make_room(keep_release_id, incoming):
    releases = gitee_releases()
    total = total_bytes(releases)
    if total + incoming <= BUDGET:
        print(f"  附件额度 {mb(total)} / {MAX_TOTAL_MB:.0f} MB，无需清理")
        return

    keep = protected_ids(releases)
    print(
        f"  额度不足：现有 {mb(total)} + 新增 {mb(incoming)} > {MAX_TOTAL_MB:.0f} MB，"
        f"开始清理最早版本（保留最新 {KEEP_STABLE} 个正式版、{KEEP_PRERELEASE} 个测试版）"
    )
    removable = 0
    for release in sorted(releases, key=lambda r: r.get("created_at") or ""):
        if total + incoming <= BUDGET:
            break
        if release.get("id") == keep_release_id:
            continue
        if release.get("id") in keep:
            print(f"    = 保留 {release.get('tag_name')}（在保留名单内）")
            continue
        assets = release.get("assets") or []
        if not assets:
            continue
        removable += 1
        for asset in assets:
            delete_asset(release["id"], asset["id"])
            total -= asset.get("size") or 0
            print(f"    - 删除附件 {release.get('tag_name')}/{asset['name']}")
        mark_archived(release)

    if total + incoming > BUDGET:
        if removable:
            print("  ! 已无可清理的旧版本，额度仍然不足，本次上传可能失败")
        else:
            print(
                f"  ! 除保留名单（{KEEP_STABLE} 正式 + {KEEP_PRERELEASE} 测试）外没有可删的版本，"
                "额度仍然不足，本次上传可能失败"
            )


def upload(release_id, asset):
    size = asset.get("size") or 0
    if size > MAX_ASSET_MB * 1024 * 1024:
        print(f"  跳过 {asset['name']}：{mb(size)}，超过 Gitee 单附件上限 {MAX_ASSET_MB} MB")
        return False

    fd, path = tempfile.mkstemp()
    os.close(fd)
    try:
        with gh.get(asset["browser_download_url"], stream=True, timeout=60) as resp:
            resp.raise_for_status()
            with open(path, "wb") as fh:
                for chunk in resp.iter_content(262144):
                    fh.write(chunk)
        with open(path, "rb") as fh:
            gitee(
                "POST",
                f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release_id}/attach_files",
                params=params,
                files={"file": (asset["name"], fh)},
                timeout=600,
            )
        return True
    finally:
        if os.path.exists(path):
            os.remove(path)


def main():
    if not GITEE_TOKEN:
        print("未配置 GITEE_TOKEN，跳过发行版同步")
        return 0

    handled = 0
    failures = []

    for release in gh_releases():
        tag = release["tag_name"]
        if ONLY_TAG and tag != ONLY_TAG:
            continue

        print(f"{tag}: 检查")
        target = gitee_release(tag)
        if target is None:
            target = gitee_create(release)
            print(f"  已创建发行版 #{target.get('id')}")
        else:
            print(f"  已存在发行版 #{target.get('id')}")

        release_id = target["id"]

        # 已被额度策略归档的版本不再补传附件，避免"删了又传、传了又删"的死循环
        if ARCHIVE_NOTE and ARCHIVE_NOTE in (target.get("body") or ""):
            print("  已在 Gitee 归档，跳过附件同步")
            handled += 1
            continue

        existing = {a.get("name") for a in (target.get("assets") or [])}

        pending = []
        for asset in release.get("assets") or []:
            if asset["name"] in existing:
                print(f"  已存在附件 {asset['name']}，跳过")
            elif (asset.get("size") or 0) > MAX_ASSET_MB * 1024 * 1024:
                print(f"  跳过 {asset['name']}：{mb(asset.get('size') or 0)}，超过单附件上限")
            else:
                pending.append(asset)

        if pending:
            make_room(release_id, sum(a.get("size") or 0 for a in pending))
            for asset in pending:
                try:
                    if upload(release_id, asset):
                        print(f"  已上传 {asset['name']}")
                except Exception as exc:
                    failures.append(f"{tag}/{asset['name']}: {exc}")
                    print(f"  上传失败 {asset['name']}: {exc}")

        handled += 1

    print(f"共处理 {handled} 个 Release，失败 {len(failures)} 项")
    for item in failures:
        print(f"  - {item}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
