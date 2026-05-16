from __future__ import annotations

import os
from pathlib import Path

from github import Auth, Github, GithubException, UnknownObjectException
from github.GitRelease import GitRelease
from github.Repository import Repository


DEFAULT_RELEASE_TAG = "openvino-android-prebuilds-nightly"
DEFAULT_RELEASE_TITLE = "OpenVINO Android Prebuilds Nightly"
DEFAULT_RELEASE_NOTES_PREFIX = "Rolling nightly Android arm64 OpenVINO prebuilds."


def _github_repository() -> Repository:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repository_name = os.environ.get("GITHUB_REPOSITORY")
    if not token:
        raise SystemExit("GH_TOKEN or GITHUB_TOKEN must be set to publish a release.")
    if not repository_name:
        raise SystemExit("GITHUB_REPOSITORY must be set to publish a release.")

    try:
        return Github(auth=Auth.Token(token)).get_repo(repository_name)
    except GithubException as error:
        raise SystemExit(f"Failed to open GitHub repository {repository_name}: {error}") from error


def _get_release(repository: Repository, tag: str) -> GitRelease | None:
    try:
        return repository.get_release(tag)
    except UnknownObjectException:
        return None
    except GithubException as error:
        raise SystemExit(f"Failed to read GitHub release {tag}: {error}") from error


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


def _upload_asset_clobber(release: GitRelease, prebuild: Path) -> None:
    try:
        for asset in release.get_assets():
            if asset.name == prebuild.name:
                print(f"Deleting existing release asset: {asset.name}", flush=True)
                asset.delete_asset()
        print(f"Uploading release asset: {prebuild}", flush=True)
        release.upload_asset(str(prebuild), name=prebuild.name, content_type="application/zip")
    except GithubException as error:
        raise SystemExit(f"Failed to upload release asset {prebuild}: {error}") from error


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
    notes = _release_notes(notes_prefix)
    notes_file.write_text(notes, encoding="utf-8")

    repository = _github_repository()
    release = _get_release(repository, tag)
    try:
        if release is None:
            print(f"Creating GitHub prerelease: {tag}", flush=True)
            release = repository.create_git_release(
                tag=tag,
                name=title,
                message=notes,
                prerelease=True,
                make_latest="false",
            )
        else:
            print(f"Updating GitHub prerelease: {tag}", flush=True)
            release = release.update_release(
                name=title,
                message=notes,
                draft=False,
                prerelease=True,
                tag_name=tag,
                make_latest="false",
            )
    except GithubException as error:
        raise SystemExit(f"Failed to create or update GitHub release {tag}: {error}") from error

    for prebuild in prebuilds:
        _upload_asset_clobber(release, prebuild)


def publish_release_from_env() -> None:
    artifacts_dir = os.environ.get("ARTIFACTS_DIR", "")
    if not artifacts_dir:
        raise SystemExit("ARTIFACTS_DIR must be set for publish-release stage.")

    publish_rolling_prerelease(
        tag=os.environ.get("RELEASE_TAG", DEFAULT_RELEASE_TAG),
        title=os.environ.get("RELEASE_TITLE", DEFAULT_RELEASE_TITLE),
        artifacts_dir=Path(artifacts_dir),
        notes_prefix=os.environ.get("RELEASE_NOTES_PREFIX", DEFAULT_RELEASE_NOTES_PREFIX),
    )
