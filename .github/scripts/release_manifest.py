#!/usr/bin/env python3
"""정식 릴리스 매니페스트 `AgvCfgManager.json` 과 릴리스 노트를 만든다 (docs/DEVELOPMENT_PLAN.md 12장). CI 전용.

앱(UpdateManifest)이 읽는 키: version, versionCode, file, sha256, size, minSdk, published, page, notes.
notes 는 이전 버전 태그 이후 커밋의 제목 줄만 쓰고, Co-Authored-By · Claude-Session 줄은 뺀다.
"""
import argparse
import datetime
import json
import os
import re
import subprocess

TRAILER = re.compile(r"^(Co-Authored-By|Claude-Session):", re.IGNORECASE)


def read_min_sdk(gradle_path: str) -> int:
    with open(gradle_path, encoding="utf-8") as handle:
        match = re.search(r"minSdk\s*=\s*(\d+)", handle.read())
    if not match:
        raise SystemExit(f"{gradle_path} 에서 minSdk 를 찾지 못했습니다")
    return int(match.group(1))


def read_sha256(sha_file: str, apk_name: str) -> str:
    with open(sha_file, encoding="utf-8") as handle:
        parts = handle.read().split()
    if len(parts) < 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]) or parts[1].lstrip("*") != apk_name:
        raise SystemExit(f"{sha_file} 형식이 sha256sum 출력과 다릅니다")
    return parts[0]


def release_notes(sha: str, tag: str) -> list[str]:
    base = subprocess.run(
        ["git", "describe", "--tags", "--abbrev=0", "--match", "v*", "--exclude", tag, sha],
        capture_output=True, text=True,
    ).stdout.strip()
    span = f"{base}..{sha}" if base else sha
    raw = subprocess.run(
        ["git", "log", "--format=%B%x1e", span], capture_output=True, text=True, check=True,
    ).stdout
    lines: list[str] = []
    for entry in raw.split("\x1e"):
        kept = [line.strip() for line in entry.splitlines() if not TRAILER.match(line.strip())]
        subject = next((line for line in kept if line), "")
        if subject and not subject.startswith("Merge "):
            lines.append(f"- {subject}")
    return lines[:30] or ["- 변경 사항 요약이 없습니다."]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--sha", required=True, help="릴리스할 커밋 (git rev)")
    parser.add_argument("--repo", required=True, help="owner/name")
    parser.add_argument("--apk", default="AgvCfgManager.apk")
    parser.add_argument("--sha-file", default="AgvCfgManager.apk.sha256")
    parser.add_argument("--gradle", default="app/build.gradle.kts")
    parser.add_argument("--json-out", default="AgvCfgManager.json")
    parser.add_argument("--notes-out", required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"\d+\.\d{1,2}\.\d{1,2}", args.version):
        raise SystemExit(f"버전 형식이 X.Y.Z 가 아닙니다: {args.version}")
    if not os.path.isfile(args.apk):
        raise SystemExit(f"APK 가 없습니다: {args.apk}")

    notes = release_notes(args.sha, args.tag)
    with open(args.notes_out, "w", encoding="utf-8") as handle:
        handle.write("\n".join(notes) + "\n")

    manifest = {
        "version": args.version,
        "versionCode": args.code,
        "file": args.apk,
        "sha256": read_sha256(args.sha_file, args.apk),
        "size": os.path.getsize(args.apk),
        "minSdk": read_min_sdk(args.gradle),
        "published": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "page": f"https://github.com/{args.repo}/releases/tag/{args.tag}",
        "notes": "\n".join(notes),
    }
    with open(args.json_out, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(json.dumps({k: v for k, v in manifest.items() if k != "notes"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
