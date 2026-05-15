#!/usr/bin/env python3
from __future__ import annotations

import os
from pathlib import Path
from typing import Annotated

import typer

from openvino_android_prebuilds.release import publish_rolling_prerelease


def publish(
    tag: Annotated[str, typer.Option(help="GitHub release tag.")] = os.environ.get(
        "RELEASE_TAG",
        "openvino-android-prebuilds-debug",
    ),
    title: Annotated[str, typer.Option(help="GitHub release title.")] = os.environ.get(
        "RELEASE_TITLE",
        "OpenVINO Android Prebuilds Debug",
    ),
    artifacts_dir: Annotated[
        str,
        typer.Option(help="Directory containing prebuild zip artifacts."),
    ] = os.environ.get("ARTIFACTS_DIR", ""),
    notes_prefix: Annotated[str, typer.Option(help="First paragraph for generated release notes.")] = os.environ.get(
        "RELEASE_NOTES_PREFIX",
        "Rolling debug Android arm64 OpenVINO prebuilds.",
    ),
) -> None:
    if not artifacts_dir:
        raise SystemExit("ARTIFACTS_DIR or --artifacts-dir must be set.")

    publish_rolling_prerelease(
        tag=tag,
        title=title,
        artifacts_dir=Path(artifacts_dir),
        notes_prefix=notes_prefix,
    )


def main() -> None:
    typer.run(publish)


if __name__ == "__main__":
    main()
