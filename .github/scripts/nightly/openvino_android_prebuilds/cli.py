from __future__ import annotations

import argparse

from .build_steps import (
    build_onetbb,
    build_openvino_genai,
    build_openvino_java_api,
    build_openvino_runtime,
    configure_openvino,
    install_openvino,
)
from .common import BuildConfig
from .package import ccache_stats, package_prebuild
from .sources import checkout_sources, record_source_manifest
from .workspace import prepare


STAGES = {
    "prepare": prepare,
    "checkout-sources": checkout_sources,
    "record-source-manifest": record_source_manifest,
    "build-onetbb": build_onetbb,
    "configure-openvino": configure_openvino,
    "build-openvino-runtime": build_openvino_runtime,
    "build-openvino-genai": build_openvino_genai,
    "build-openvino-java-api": build_openvino_java_api,
    "install-openvino": install_openvino,
    "package-prebuild": package_prebuild,
    "ccache-stats": ccache_stats,
}


def run_all(config: BuildConfig) -> None:
    for stage in STAGES:
        if stage == "ccache-stats":
            continue
        STAGES[stage](config)
    ccache_stats(config)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("stage", nargs="?", default="all", choices=["all", *STAGES.keys()])
    args = parser.parse_args()

    config = BuildConfig.from_env()
    if args.stage == "all":
        run_all(config)
        return

    STAGES[args.stage](config)
