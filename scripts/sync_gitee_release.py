#!/usr/bin/env python3
"""把 GitHub Release 的页面信息与安装包附件同步到 Gitee。

默认只同步最新一个稳定版（非 pre-release）的 ``.exe`` 附件，避免一次性占满 Gitee
附件额度；用 ``SYNC_ALL_VERSIONS=true`` 可以放开到全部 Release，用 ``ASSET_SUFFIX``
调整要同步的扩展名。
"""
import os
import shutil
import sys
import tempfile
import time

import requests

GH_API = "https://api.github.com"
GITEE_API = "https://gitee.com/api/v5"

GITEE_OWNER = os.getenv("GITEE_OWNER", "kjllin")
GITEE_REPO = os.getenv("GITEE_REPO", "hpcl-launcher")
GITEE_TOKEN = os.getenv("GITEE_TOKEN", "")
GH_REPO = os.getenv("GH_REPO", "KJLLin/HPCL-Launcher")
GH_TOKEN = os.getenv("GITHUB_TOKEN", "")
ONLY_TAG = os.getenv("TAG", "").strip()
DOWNLOAD_NOTE = os.getenv("DOWNLOAD_NOTE", "").strip()
WIPE = os.getenv("WIPE_ATTACHMENTS", "").strip().lower() in ("1", "true", "yes")
SYNC_ASSETS = os.getenv("SYNC_ASSETS", "true").strip().lower() in ("1", "true", "yes")
SYNC_ALL_VERSIONS = os.getenv("SYNC_ALL_VERSIONS", "").strip().lower() in ("1", "true", "yes")
ASSET_SUFFIX = os.getenv("ASSET_SUFFIX", ".exe").strip().lower()

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
    for attempt in (1, 2, 3):
        try:
            resp = requests.request(method, f"{GITEE_API}{path}", **kwargs)
        except Exception as exc:
            if attempt == 3:
                raise
            print(f"  ~ 请求异常，重试 {attempt}/2：{type(exc).__name__}", flush=True)
            time.sleep(3)
            continue
        if resp.status_code >= 500 and attempt < 3:
            print(f"  ~ 服务端 {resp.status_code}，重试 {attempt}/2", flush=True)
            time.sleep(3)
            continue
        if resp.status_code >= 400:
            raise RuntimeError(f"{method} {path} -> {resp.status_code} {resp.text[:200]}")
        return resp.json() if resp.content else {}
    return {}


def list_attachments(release_id):
    """release.assets 不含 id/size，附件必须走专用接口。"""
    page, items = 1, []
    while True:
        batch = gitee(
            "GET",
            f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release_id}/attach_files",
            params={**params, "per_page": 100, "page": page},
        )
        if not batch or not isinstance(batch, list):
            return items
        items += batch
        page += 1


def delete_attachment(release_id, attachment_id):
    gitee(
        "DELETE",
        f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release_id}/attach_files/{attachment_id}",
        params=params,
    )


def wipe_attachments(release_id):
    removed = 0
    for item in list_attachments(release_id):
        if not item.get("id"):
            continue
        delete_attachment(release_id, item["id"])
        removed += 1
        print(f"  - 已删除附件 {item['name']}（{mb(item.get('size') or 0)}）", flush=True)
    return removed


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


LEGACY_NOTE = "Gitee 侧仅保留发行说明"


def compose_body(raw):
    body = "\n".join(ln for ln in (raw or "").splitlines() if LEGACY_NOTE not in ln).strip()
    if DOWNLOAD_NOTE and DOWNLOAD_NOTE not in body:
        body = f"{body}\n\n{DOWNLOAD_NOTE}".strip() if body else DOWNLOAD_NOTE
    return body


def create_release(tag, name, body, prerelease):
    return gitee(
        "POST",
        f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases",
        data={
            "access_token": GITEE_TOKEN,
            "tag_name": tag,
            "name": name,
            "body": body,
            "prerelease": "true" if prerelease else "false",
            "target_commitish": "main",
        },
    )


def update_release(release, tag, name, body, prerelease):
    want_pre = "true" if prerelease else "false"
    if (
        (release.get("name") or "") == name
        and (release.get("body") or "").strip() == body
        and str(release.get("prerelease")).lower() == want_pre
    ):
        return False
    gitee(
        "PATCH",
        f"/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release['id']}",
        data={
            "access_token": GITEE_TOKEN,
            "tag_name": tag,
            "name": name,
            "body": body,
            "prerelease": want_pre,
        },
    )
    return True


def download_asset(asset, dest):
    with gh.get(asset["browser_download_url"], stream=True, timeout=(15, 180)) as resp:
        resp.raise_for_status()
        with open(dest, "wb") as handle:
            shutil.copyfileobj(resp.raw, handle)
    return os.path.getsize(dest)


def upload_file(release_id, path, name):
    """单文件 POST，超时给足；返回 True 表示服务端已存在完整文件。"""
    size = os.path.getsize(path)
    url = f"{GITEE_API}/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release_id}/attach_files"
    for attempt in (1, 2):
        print(f"  上传 {name}（{mb(size)}）第 {attempt} 次", flush=True)
        try:
            with open(path, "rb") as handle:
                resp = requests.post(
                    url,
                    data={"access_token": GITEE_TOKEN},
                    files={"file": (name, handle, "application/octet-stream")},
                    timeout=(15, 300),
                )
            print(f"  <- HTTP {resp.status_code}（{resp.elapsed.total_seconds():.1f}s）", flush=True)
            if resp.status_code < 400:
                data = resp.json() if resp.content else {}
                if (data.get("size") or 0) == size:
                    return True
                print(f"  ! 服务端大小不符：{data.get('size')} / {size}", flush=True)
                if data.get("id"):
                    delete_attachment(release_id, data["id"])
            else:
                print(f"  ! {resp.status_code} {resp.text[:150]}", flush=True)
        except Exception as exc:
            print(f"  ! 上传异常：{type(exc).__name__}: {exc}", flush=True)
    return False


def sync_assets(release_id, assets, tag):
    """把该 Release 的安装包补到 Gitee，已存在且大小一致则跳过。"""
    assets = [a for a in assets if a["name"].lower().endswith(ASSET_SUFFIX)]
    if not assets:
        return 0, 0
    existing = {item.get("name"): item for item in list_attachments(release_id)}
    added = skipped = 0
    with tempfile.TemporaryDirectory() as tmp:
        for asset in assets:
            have = existing.get(asset["name"])
            if have and (have.get("size") or 0) == (asset.get("size") or 0):
                skipped += 1
                print(f"  = 附件 {asset['name']} 已同步（{mb(have.get('size') or 0)}）", flush=True)
                continue
            if have and have.get("id"):
                print(f"  ~ 附件 {asset['name']} 大小不一致，先删除旧文件", flush=True)
                delete_attachment(release_id, have["id"])
            path = os.path.join(tmp, asset["name"])
            size = download_asset(asset, path)
            print(f"  下载 {asset['name']} 完成（{mb(size)}）", flush=True)
            if upload_file(release_id, path, asset["name"]):
                print(f"  + 已上传附件 {asset['name']}", flush=True)
                added += 1
            else:
                raise RuntimeError(f"{tag}/{asset['name']}: 上传失败")
    return added, skipped


def main():
    if not GITEE_TOKEN:
        print("未配置 GITEE_TOKEN，跳过发行版同步")
        return 0

    releases = list(gh_releases())
    latest_stable = next(
        (r["tag_name"] for r in releases if not r.get("prerelease") and not r.get("draft")),
        None,
    )
    print(f"最新稳定版：{latest_stable}；附件同步：{'开' if SYNC_ASSETS else '关'}", flush=True)

    created = updated = unchanged = wiped = assets_added = assets_skipped = 0
    failures = []

    for release in releases:
        tag = release["tag_name"]
        if ONLY_TAG and tag != ONLY_TAG:
            continue

        want_assets = SYNC_ASSETS and (ONLY_TAG or SYNC_ALL_VERSIONS or tag == latest_stable)
        name = release.get("name") or tag
        body = compose_body(release.get("body"))
        prerelease = bool(release.get("prerelease"))

        try:
            target = gitee_release(tag)
            if target is None:
                target = create_release(tag, name, body, prerelease)
                created += 1
                print(f"  + 已创建发行版 {tag}", flush=True)
            elif update_release(target, tag, name, body, prerelease):
                updated += 1
                print(f"  ~ 已更新发行版 {tag}", flush=True)
            else:
                unchanged += 1
                print(f"  = {tag} 无变化", flush=True)

            if WIPE and target.get("id"):
                wiped += wipe_attachments(target["id"])
            elif want_assets and target.get("id"):
                added, skipped = sync_assets(target["id"], release.get("assets") or [], tag)
                assets_added += added
                assets_skipped += skipped
        except Exception as exc:
            failures.append(f"{tag}: {exc}")
            print(f"  ! {tag} 同步失败：{exc}", flush=True)

    print(
        f"共处理 {created + updated + unchanged} 个 Release："
        f"新建 {created}、更新 {updated}、无变化 {unchanged}、删除附件 {wiped}，"
        f"附件新增 {assets_added}、已存在 {assets_skipped}，失败 {len(failures)} 项"
    )
    for item in failures:
        print(f"  - {item}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
