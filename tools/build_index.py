#!/usr/bin/env python3
"""Regenerate index.min.json, index.pb and index-store.json for a multi-source
Mihon / Tachiyomi extension repo.

Metadata is read from `dist_meta.json` (a dict with a `repo` block and an
`extensions` list). If `--source-info <path>` is given (repeatable, in the same
order as the `extensions` list), version/code/pkg/source_id are taken from the
freshly built `keiyoushi-source-info.json` artifacts. `--apk <path>` (repeatable)
overrides the relative apk path per extension.

Usage:
    python build_index.py [--source-info a.json --source-info b.json ...] [--out .]
"""
import argparse
import json
import sys
from pathlib import Path

from google.protobuf import json_format

sys.path.insert(0, str(Path(__file__).parent))
import index_pb2  # noqa: E402

META_FILE = Path(__file__).parent / "dist_meta.json"


def build_legacy_entry(meta: dict) -> dict:
    return {
        "name": meta["name"],
        "pkg": meta["pkg"],
        "apk": meta["apk"],
        "lang": meta["lang"],
        "code": meta["code"],
        "version": meta["version"],
        "nsfw": meta.get("nsfw", 0),
        "hasReadme": meta.get("hasReadme", False),
        "hasChangelog": meta.get("hasChangelog", False),
        "sources": [
            {
                "name": meta["name"],
                "lang": meta["lang"],
                "id": str(meta["source_id"]),
                "baseUrl": meta["base_url"],
            }
        ],
    }


def build_store_ext(meta: dict, repo: dict) -> dict:
    return {
        "name": meta["name"],
        "packageName": meta["pkg"],
        "resources": {
            "apkUrl": f"{repo['store_base_url']}/{meta['apk']}",
            "iconUrl": repo["icon_url"],
        },
        "extensionLib": meta.get("extension_lib", "1.5"),
        "versionCode": meta["code"],
        "versionName": meta["version"],
        "contentWarning": meta.get("content_warning", "CONTENT_WARNING_SAFE"),
        "sources": [
            {
                "id": int(meta["source_id"]),
                "name": meta["name"],
                "language": meta["lang"],
                "homeUrl": meta["base_url"],
                "mirrorUrls": [],
                "message": None,
            }
        ],
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source-info", action="append", default=[],
                    help="keiyoushi-source-info.json path (repeatable, one per extension)")
    ap.add_argument("--apk", action="append", default=[],
                    help="apk path override relative to repo root (repeatable)")
    ap.add_argument("--store", action="store_true",
                    help="Also write index-store.json (new Mihon store format)")
    ap.add_argument("--out", default=None, help="Output dir (default: repo root)")
    args = ap.parse_args()

    data = json.loads(META_FILE.read_text(encoding="utf-8"))
    repo = data["repo"]
    metas = data["extensions"]

    pending_infos = list(args.source_info)
    for meta in metas:
        for path in list(pending_infos):
            info = json.loads(Path(path).read_text(encoding="utf-8"))
            if info["packageName"] == meta["pkg"]:
                meta["pkg"] = info["packageName"]
                meta["code"] = info["versionCode"]
                meta["version"] = info["versionName"]
                meta["source_id"] = str(info["sources"][0]["id"])
                meta["base_url"] = info["sources"][0]["baseUrl"]
                pending_infos.remove(path)
                break

    for i, meta in enumerate(metas):
        if i < len(args.apk) and args.apk[i]:
            meta["apk"] = args.apk[i]

    out = Path(args.out) if args.out else Path(__file__).parent.parent

    entries = [build_legacy_entry(m) for m in metas]

    (out / "index.min.json").write_text(
        json.dumps(entries, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    extensions = index_pb2.Extensions()
    json_format.Parse(json.dumps({"extensions": entries}, ensure_ascii=False), extensions)
    (out / "index.pb").write_bytes(extensions.SerializeToString())

    if args.store:
        store = {
            "name": repo["name"],
            "badgeLabel": repo["name"],
            "signingKey": repo["signing_key_fingerprint"],
            "contact": {
                "website": repo["website_url"],
                "discord": None,
            },
            "extensionList": {
                "extensions": [build_store_ext(m, repo) for m in metas],
            },
        }
        (out / "index-store.json").write_text(
            json.dumps(store, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        print("Wrote index-store.json in", out)

    print("Wrote index.min.json and index.pb in", out)


if __name__ == "__main__":
    main()