from __future__ import annotations

import shutil
from datetime import datetime, timezone
from pathlib import Path

from .common import BuildConfig, command_output, require_command, run


def clone_ref(repo_url: str, ref: str, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    run(
        [
            "git",
            "clone",
            "--recursive",
            "--depth",
            "1",
            "--shallow-submodules",
            "--jobs",
            "4",
            "--branch",
            ref,
            repo_url,
            str(destination),
        ]
    )


def checkout_sources(config: BuildConfig) -> None:
    require_command("git")
    config.src_dir.mkdir(parents=True, exist_ok=True)
    clone_ref(config.openvino_repo, config.openvino_ref, config.src_dir / "openvino")
    clone_ref(config.openvino_contrib_repo, config.openvino_contrib_ref, config.src_dir / "openvino_contrib")
    clone_ref(config.openvino_genai_repo, config.openvino_genai_ref, config.src_dir / "openvino.genai")
    clone_ref(config.onetbb_repo, config.onetbb_ref, config.src_dir / "oneTBB")


def record_source_manifest(config: BuildConfig) -> None:
    require_command("git")
    config.artifacts_dir.mkdir(parents=True, exist_ok=True)

    lines = [
        "OpenVINO Android prebuild source manifest",
        f"Generated at: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
        "",
        f"openvino_ref={config.openvino_ref}",
        f"openvino_commit={command_output(['git', '-C', str(config.src_dir / 'openvino'), 'rev-parse', 'HEAD'])}",
        f"openvino_genai_ref={config.openvino_genai_ref}",
        f"openvino_genai_commit={command_output(['git', '-C', str(config.src_dir / 'openvino.genai'), 'rev-parse', 'HEAD'])}",
        f"openvino_contrib_ref={config.openvino_contrib_ref}",
        f"openvino_contrib_commit={command_output(['git', '-C', str(config.src_dir / 'openvino_contrib'), 'rev-parse', 'HEAD'])}",
        f"onetbb_ref={config.onetbb_ref}",
        f"onetbb_commit={command_output(['git', '-C', str(config.src_dir / 'oneTBB'), 'rev-parse', 'HEAD'])}",
        "",
        f"android_abi={config.android_abi}",
        f"android_platform={config.android_platform}",
        f"android_ndk_version={config.android_ndk_version}",
    ]
    (config.artifacts_dir / "source-manifest.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    if shutil.which("ccache"):
        run(["ccache", "--zero-stats"])
