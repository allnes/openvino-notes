#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
from pathlib import Path

from openvino_android_prebuilds.release import publish_rolling_prerelease


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", default=os.environ.get("RELEASE_TAG", "openvino-android-prebuilds-debug"))
    parser.add_argument("--title", default=os.environ.get("RELEASE_TITLE", "OpenVINO Android Prebuilds Debug"))
    parser.add_argument(
        "--artifacts-dir",
        default=os.environ.get("ARTIFACTS_DIR", ""),
        help="Directory containing prebuild zip artifacts.",
    )
    parser.add_argument(
        "--notes-prefix",
        default=os.environ.get("RELEASE_NOTES_PREFIX", "Rolling debug Android arm64 OpenVINO prebuilds."),
    )
    args = parser.parse_args()

    if not args.artifacts_dir:
        raise SystemExit("ARTIFACTS_DIR or --artifacts-dir must be set.")

    publish_rolling_prerelease(
        tag=args.tag,
        title=args.title,
        artifacts_dir=Path(args.artifacts_dir),
        notes_prefix=args.notes_prefix,
    )


if __name__ == "__main__":
    main()
