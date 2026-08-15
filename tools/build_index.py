#!/usr/bin/env python3
"""Regenerate index.min.json and index.pb for a custom Mihon/Tachiyomi extension repo.

Reads metadata from `dist_meta.json` (same dir). If `--source-info <path>` is given
(the `keiyoushi-source-info.json` produced by the build), version/code/pkg are taken
from the freshly built artifact automatically.

Usage:
    python build_index.py [--source-info path/to/keiyoushi-source-info.json] [--out .]
"""

import argparse
import json
import sys
from pathlib import Path

from google.protobuf import json_format

sys.path.insert(0, str(Path(__file__).parent))
import index_pb2  # noqa: E402

META_FILE = Path(__file__).parent / "dist_meta.json"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source-info", help="Path to keiyoushi-source-info.json from the build")
    ap.add_argument("--apk", help="Override the apk path in the index (relative to repo root)")
    ap.add_argument("--out", default=None, help="Output dir (default: repo root)")
    args = ap.parse_args()

    meta = json.loads(META_FILE.read_text(encoding="utf-8"))

    if args.source_info:
        info = json.loads(Path(args.source_info).read_text(encoding="utf-8"))
        meta["pkg"] = info["packageName"]
        meta["code"] = info["versionCode"]
        meta["version"] = info["versionName"]
        meta["source_id"] = str(info["sources"][0]["id"])

    if args.apk:
        meta["apk"] = args.apk

    out = Path(args.out) if args.out else Path(__file__).parent.parent

    entry = {
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

    (out / "index.min.json").write_text(
        json.dumps([entry], ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    extensions = index_pb2.Extensions()
    json_format.Parse(json.dumps({"extensions": [entry]}, ensure_ascii=False), extensions)
    (out / "index.pb").write_bytes(extensions.SerializeToString())

    print("Wrote index.min.json and index.pb in", out)


if __name__ == "__main__":
    main()