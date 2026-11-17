#!/usr/bin/env python3
"""把 GitHub Release 同步到 Gitee：创建发行版并上传附件。"""
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

gh = requests.Session()
gh.headers["Accept"] = "application/vnd.github+json"
if GH_TOKEN:
    gh.headers["Authorization"] = f"Bearer {GH_TOKEN}"

gitee_params = {"access_token": GITEE_TOKEN}


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


def gitee_release(tag):
    resp = requests.get(
        f"{GITEE_API}/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/tags/{tag}",
        params=gitee_params,
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
    resp = requests.post(
        f"{GITEE_API}/repos/{GITEE_OWNER}/{GITEE_REPO}/releases",
        data=data,
        timeout=30,
    )
    resp.raise_for_status()
    return resp.json()


def upload(release_id, asset):
    size = asset.get("size") or 0
    if size > MAX_ASSET_MB * 1024 * 1024:
        print(f"  跳过 {asset['name']}：{size / 1048576:.1f} MB，超过 Gitee 单附件上限 {MAX_ASSET_MB} MB")
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
            resp = requests.post(
                f"{GITEE_API}/repos/{GITEE_OWNER}/{GITEE_REPO}/releases/{release_id}/attach_files",
                params=gitee_params,
                files={"file": (asset["name"], fh)},
                timeout=600,
            )
        resp.raise_for_status()
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
        uploaded_names = {a.get("name") for a in (target.get("assets") or [])}

        for asset in release.get("assets") or []:
            name = asset["name"]
            if name in uploaded_names:
                print(f"  已存在附件 {name}，跳过")
                continue
            try:
                if upload(release_id, asset):
                    print(f"  已上传 {name}")
            except Exception as exc:
                failures.append(f"{tag}/{name}: {exc}")
                print(f"  上传失败 {name}: {exc}")

        handled += 1

    print(f"共处理 {handled} 个 Release，失败 {len(failures)} 项")
    for item in failures:
        print(f"  - {item}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
