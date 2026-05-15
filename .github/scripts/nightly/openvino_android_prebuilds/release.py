from __future__ import annotations

import os
import subprocess
from pathlib import Path

from .common import run


def _release_exists(tag: str) -> bool:
    return subprocess.run(
        ["gh", "release", "view", tag],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    ).returncode == 0


def _release_notes(prefix: str) -> str:
    workflow_url = ""
    if os.environ.get("GITHUB_SERVER_URL") and os.environ.get("GITHUB_REPOSITORY") and os.environ.get("GITHUB_RUN_ID"):
        workflow_url = f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"

    lines = [
        prefix,
        "",
        "Source refs:",
        f"- OpenVINO: {os.environ.get('OPENVINO_REF', '')}",
        f"- OpenVINO GenAI: {os.environ.get('OPENVINO_GENAI_REF', '')}",
        f"- OpenVINO Contrib: {os.environ.get('OPENVINO_CONTRIB_REF', '')}",
        f"- oneTBB: {os.environ.get('ONETBB_REF', '')}",
        "",
        "Android target:",
        f"- ABI: {os.environ.get('ANDROID_ABI', '')}",
        f"- Platform: {os.environ.get('ANDROID_PLATFORM', '')}",
        f"- NDK: {os.environ.get('ANDROID_NDK_VERSION', '')}",
    ]
    if workflow_url:
        lines.extend(["", f"Workflow run: {workflow_url}"])
    return "\n".join(lines) + "\n"


def publish_rolling_prerelease(
    *,
    tag: str,
    title: str,
    artifacts_dir: Path,
    notes_prefix: str,
) -> None:
    prebuilds = sorted(artifacts_dir.glob("*.zip"))
    if not prebuilds:
        raise SystemExit(f"No prebuild zip artifacts found in {artifacts_dir}")

    notes_file = artifacts_dir / "release-notes.md"
    notes_file.write_text(_release_notes(notes_prefix), encoding="utf-8")

    if _release_exists(tag):
        run(
            [
                "gh",
                "release",
                "edit",
                tag,
                "--title",
                title,
                "--prerelease",
                "--notes-file",
                str(notes_file),
            ]
        )
    else:
        run(
            [
                "gh",
                "release",
                "create",
                tag,
                "--title",
                title,
                "--prerelease",
                "--notes-file",
                str(notes_file),
            ]
        )

    run(["gh", "release", "upload", tag, *[str(prebuild) for prebuild in prebuilds], "--clobber"])
