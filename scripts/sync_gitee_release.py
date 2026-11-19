#!/usr/bin/env python3
"""把 GitHub Release 的页面信息（标题、说明、预发布标记）同步到 Gitee，不上传附件。"""
import os
import sys
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

gh = requests.Session()
gh.headers["Accept"] = "application/vnd.github+json"
if GH_TOKEN:
    gh.headers["Authorization"] = f"Bearer {GH_TOKEN}"

params = {"access_token": GITEE_TOKEN}


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


def compose_body(raw):
    body = (raw or "").strip()
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


def main():
    if not GITEE_TOKEN:
        print("未配置 GITEE_TOKEN，跳过发行版同步")
        return 0

    created = updated = unchanged = 0
    failures = []

    for release in gh_releases():
        tag = release["tag_name"]
        if ONLY_TAG and tag != ONLY_TAG:
            continue

        name = release.get("name") or tag
        body = compose_body(release.get("body"))
        prerelease = bool(release.get("prerelease"))

        try:
            target = gitee_release(tag)
            if target is None:
                create_release(tag, name, body, prerelease)
                created += 1
                print(f"  + 已创建发行版 {tag}", flush=True)
            elif update_release(target, tag, name, body, prerelease):
                updated += 1
                print(f"  ~ 已更新发行版 {tag}", flush=True)
            else:
                unchanged += 1
                print(f"  = {tag} 无变化", flush=True)
        except Exception as exc:
            failures.append(f"{tag}: {exc}")
            print(f"  ! {tag} 同步失败：{exc}", flush=True)

    print(
        f"共处理 {created + updated + unchanged} 个 Release："
        f"新建 {created}、更新 {updated}、无变化 {unchanged}，失败 {len(failures)} 项"
    )
    for item in failures:
        print(f"  - {item}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
