#!/usr/bin/env python3
from __future__ import annotations

import argparse
import http.client
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

CHUNK_SIZE = 8 * 1024 * 1024
PROGRESS_STEP = 64 * 1024 * 1024


def github_token() -> str:
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        return token

    try:
        return subprocess.check_output(["gh", "auth", "token"], text=True).strip()
    except (FileNotFoundError, subprocess.CalledProcessError) as exc:
        raise SystemExit("Set GITHUB_TOKEN/GH_TOKEN or authenticate GitHub CLI with `gh auth login`.") from exc


def request(url: str, token: str, timeout: int) -> urllib.request.Request:
    return urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "openvino-notes-prebuild-downloader",
        },
    )


def download_request(url: str, range_start: int = 0) -> urllib.request.Request:
    headers = {
        "User-Agent": "openvino-notes-prebuild-downloader",
    }
    if range_start > 0:
        headers["Range"] = f"bytes={range_start}-"

    return urllib.request.Request(
        url,
        headers=headers,
    )


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


def load_json(url: str, token: str, timeout: int) -> dict:
    with urllib.request.urlopen(request(url, token, timeout), timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def find_artifact_download_url(repo: str, run_id: str, artifact_name: str, token: str, timeout: int) -> str:
    data = load_json(f"https://api.github.com/repos/{repo}/actions/runs/{run_id}/artifacts", token, timeout)
    for artifact in data.get("artifacts", []):
        if artifact.get("name") == artifact_name and not artifact.get("expired", False):
            return artifact["archive_download_url"]

    available = ", ".join(artifact.get("name", "<unnamed>") for artifact in data.get("artifacts", []))
    raise SystemExit(f"Artifact '{artifact_name}' was not found in run {run_id}. Available artifacts: {available}")


def resolve_artifact_archive_url(url: str, token: str, timeout: int) -> str:
    opener = urllib.request.build_opener(NoRedirectHandler)

    try:
        with opener.open(request(url, token, timeout), timeout=timeout) as response:
            return response.url
    except urllib.error.HTTPError as exc:
        if exc.code in (301, 302, 303, 307, 308):
            location = exc.headers.get("Location")
            if location:
                return location
        raise


def stream_download(url: str, temp_path: Path, timeout: int) -> None:
    resume_from = temp_path.stat().st_size if temp_path.exists() else 0

    with urllib.request.urlopen(download_request(url, resume_from), timeout=timeout) as response:
        status = response.status
        append = resume_from > 0 and status == 206
        if resume_from > 0 and not append:
            print("Server did not accept byte-range resume; restarting download.", flush=True)
            resume_from = 0

        mode = "ab" if append else "wb"
        downloaded = resume_from
        next_report = ((downloaded // PROGRESS_STEP) + 1) * PROGRESS_STEP

        with temp_path.open(mode) as output:
            while True:
                chunk = response.read(CHUNK_SIZE)
                if not chunk:
                    break

                output.write(chunk)
                downloaded += len(chunk)

                if downloaded >= next_report:
                    print(f"Downloaded {downloaded // (1024 * 1024)} MiB...", flush=True)
                    next_report += PROGRESS_STEP


def download_with_retries(url: str, destination: Path, token: str, timeout: int, retries: int) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temp_path = destination.with_suffix(destination.suffix + ".tmp")

    for attempt in range(1, retries + 1):
        try:
            print(f"Downloading GitHub artifact archive, attempt {attempt}/{retries}")
            archive_url = resolve_artifact_archive_url(url, token, timeout)
            stream_download(archive_url, temp_path, timeout)
            temp_path.replace(destination)
            return
        except urllib.error.HTTPError as exc:
            if exc.code == 416 and temp_path.exists() and temp_path.stat().st_size > 0:
                temp_path.replace(destination)
                return
            if attempt == retries:
                raise
            delay_seconds = min(30, attempt * 5)
            print(f"Download failed: {exc}. Retrying in {delay_seconds}s.", file=sys.stderr)
            time.sleep(delay_seconds)
        except (http.client.IncompleteRead, urllib.error.URLError, TimeoutError, OSError) as exc:
            if attempt == retries:
                raise
            delay_seconds = min(30, attempt * 5)
            print(f"Download failed: {exc}. Retrying in {delay_seconds}s.", file=sys.stderr)
            time.sleep(delay_seconds)


def extract_inner_zip(artifact_archive: Path, inner_name: str, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    temp_output = output.with_suffix(output.suffix + ".tmp")

    if temp_output.exists():
        temp_output.unlink()

    with zipfile.ZipFile(artifact_archive) as archive:
        names = archive.namelist()
        member_name = inner_name if inner_name in names else None
        if member_name is None and len(names) == 1:
            member_name = names[0]
        if member_name is None:
            raise SystemExit(f"Artifact archive does not contain '{inner_name}'. Members: {', '.join(names)}")

        with archive.open(member_name) as source, temp_output.open("wb") as target:
            while True:
                chunk = source.read(CHUNK_SIZE)
                if not chunk:
                    break
                target.write(chunk)

    temp_output.replace(output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--artifact-name", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument("--retries", type=int, default=20)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.output.is_file() and args.output.stat().st_size > 0:
        print(f"Reusing existing prebuild archive: {args.output}")
        return

    token = github_token()
    download_url = find_artifact_download_url(args.repo, args.run_id, args.artifact_name, token, args.timeout)
    artifact_archive = args.output.with_suffix(args.output.suffix + ".github-artifact.zip")

    download_with_retries(download_url, artifact_archive, token, args.timeout, args.retries)
    extract_inner_zip(artifact_archive, args.artifact_name, args.output)
    print(f"Downloaded prebuild archive: {args.output}")


if __name__ == "__main__":
    main()
