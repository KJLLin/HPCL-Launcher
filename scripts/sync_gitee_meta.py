#!/usr/bin/env python3
"""把仓库元信息、标签、里程碑同步到 Gitee（只增改，不删除）。"""
import os
import sys

import requests

GH_API = "https://api.github.com"
GITEE_API = "https://gitee.com/api/v5"

GH_REPO = os.getenv("GH_REPO", "KJLLin/HPCL-Launcher")
GITEE_OWNER = os.getenv("GITEE_OWNER", "kjllin")
GITEE_REPO = os.getenv("GITEE_REPO", "hpcl-launcher")
GITEE_TOKEN = os.getenv("GITEE_TOKEN", "")
GH_TOKEN = os.getenv("GITHUB_TOKEN", "")
SYNC_DESCRIPTION = os.getenv("SYNC_DESCRIPTION", "true").lower() == "true"
DESCRIPTION_SUFFIX = os.getenv("GITEE_DESCRIPTION_SUFFIX", "").strip()

gh = requests.Session()
gh.headers["Accept"] = "application/vnd.github+json"
if GH_TOKEN:
    gh.headers["Authorization"] = f"Bearer {GH_TOKEN}"

errors = []


def gh_get(path, params=None):
    resp = gh.get(f"{GH_API}{path}", params=params, timeout=30)
    resp.raise_for_status()
    return resp.json()


def gh_list(path, params=None):
    items, page = [], 1
    while True:
        batch = gh_get(path, {**(params or {}), "per_page": 100, "page": page})
        if not batch:
            return items
        items += batch
        page += 1


def gitee(method, path, **kwargs):
    kwargs.setdefault("timeout", 30)
    resp = requests.request(method, f"{GITEE_API}{path}", **kwargs)
    if resp.status_code >= 400:
        raise RuntimeError(f"{method} {path} -> {resp.status_code} {resp.text[:200]}")
    return resp.json() if resp.content else {}


def sync_repo():
    src = gh_get(f"/repos/{GH_REPO}")
    dst = gitee(
        "GET", f"/repos/{GITEE_OWNER}/{GITEE_REPO}", params={"access_token": GITEE_TOKEN}
    )
    payload = {}

    # GitHub 描述 + Gitee 专属备注；若 GitHub 描述里已经写了这句，就不重复追加
    src_desc = (src.get("description") or "").strip()
    desired_desc = src_desc
    if src_desc and DESCRIPTION_SUFFIX and DESCRIPTION_SUFFIX not in src_desc:
        desired_desc = f"{src_desc}{DESCRIPTION_SUFFIX}"
    if SYNC_DESCRIPTION and desired_desc and desired_desc != (dst.get("description") or ""):
        payload["description"] = desired_desc

    src_home = src.get("homepage") or ""
    if src_home and src_home != (dst.get("homepage") or ""):
        payload["homepage"] = src_home

    if not payload:
        print("· 仓库元信息已一致")
        return
    gitee(
        "PATCH",
        f"/repos/{GITEE_OWNER}/{GITEE_REPO}",
        data={"access_token": GITEE_TOKEN, **payload},
    )
    print("· 仓库元信息已更新：" + "、".join(payload))


def sync_labels():
    src = gh_list(f"/repos/{GH_REPO}/labels")
    dst = gitee(
        "GET", f"/repos/{GITEE_OWNER}/{GITEE_REPO}/labels", params={"access_token": GITEE_TOKEN}
    )
    existing = {l.get("name"): l for l in (dst if isinstance(dst, list) else [])}

    created = 0
    for label in src:
        name = label["name"]
        color = (label.get("color") or "ededed").lstrip("#")
        if name in existing:
            have = (existing[name].get("color") or "").lstrip("#").upper()
            if have and have != color.upper():
                print(f"  ! 标签「{name}」颜色不同（Gitee {have} / GitHub {color}），接口不支持改色，跳过")
            continue
        gitee(
            "POST",
            f"/repos/{GITEE_OWNER}/{GITEE_REPO}/labels",
            data={"access_token": GITEE_TOKEN, "name": name, "color": color},
        )
        created += 1
        print(f"  + 新建标签「{name}」#{color}")
    print(f"· 标签：GitHub {len(src)} 个，新建 {created} 个")


def sync_milestones():
    src = gh_list(f"/repos/{GH_REPO}/milestones", {"state": "all"})
    dst = gitee(
        "GET",
        f"/repos/{GITEE_OWNER}/{GITEE_REPO}/milestones",
        params={"access_token": GITEE_TOKEN, "state": "all", "per_page": 100},
    )
    existing = {m.get("title"): m for m in (dst if isinstance(dst, list) else [])}

    created = updated = 0
    for ms in src:
        title = ms["title"]
        data = {
            "access_token": GITEE_TOKEN,
            "title": title,
            "state": ms.get("state") or "open",
            "description": ms.get("description") or "",
        }
        if ms.get("due_on"):
            data["due_on"] = ms["due_on"]

        if title not in existing:
            gitee("POST", f"/repos/{GITEE_OWNER}/{GITEE_REPO}/milestones", data=data)
            created += 1
            print(f"  + 新建里程碑「{title}」（{data['state']}）")
            continue

        cur = existing[title]
        if cur.get("state") != data["state"] or (cur.get("description") or "") != data["description"]:
            number = cur.get("number") or cur.get("id")
            gitee(
                "PATCH",
                f"/repos/{GITEE_OWNER}/{GITEE_REPO}/milestones/{number}",
                data=data,
            )
            updated += 1
            print(f"  ~ 更新里程碑「{title}」（{data['state']}）")
    print(f"· 里程碑：GitHub {len(src)} 个，新建 {created} 个，更新 {updated} 个")


def main():
    if not GITEE_TOKEN:
        print("未配置 GITEE_TOKEN，跳过元信息同步")
        return 0

    for task in (sync_repo, sync_labels, sync_milestones):
        try:
            task()
        except Exception as exc:
            errors.append(f"{task.__name__}: {exc}")
            print(f"  ! {task.__name__} 失败：{exc}")

    if errors:
        print(f"共 {len(errors)} 项失败")
        for item in errors:
            print(f"  - {item}")
        return 1
    print("元信息同步完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
