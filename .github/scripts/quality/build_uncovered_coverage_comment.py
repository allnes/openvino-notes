#!/usr/bin/env python3
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


MAX_FILES = 10
MAX_RANGES_PER_FILE = 5


def collect_uncovered_ranges(report: Path, module: str, repo_root: Path) -> list[tuple[str, list[str]]]:
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        return []

    uncovered: list[tuple[str, list[str]]] = []

    for package in root.findall("./package"):
        package_name = package.attrib.get("name", "").strip("/")
        for sourcefile in package.findall("./sourcefile"):
            source_name = sourcefile.attrib.get("name")
            if not source_name:
                continue

            lines = []
            for line in sourcefile.findall("./line"):
                missed = int(line.attrib.get("mi", "0"))
                covered = int(line.attrib.get("ci", "0"))
                if missed > 0 and covered == 0:
                    lines.append(int(line.attrib["nr"]))

            if not lines:
                continue

            file_path = resolve_source_path(repo_root, module, package_name, source_name)
            uncovered.append((file_path.as_posix(), collapse_ranges(sorted(lines))))

    return uncovered


def resolve_source_path(repo_root: Path, module: str, package_name: str, source_name: str) -> Path:
    package_path = Path(package_name) if package_name else Path()
    candidates = [
        repo_root / module / "src" / "main" / "java" / package_path / source_name,
        repo_root / module / "src" / "main" / "kotlin" / package_path / source_name,
        repo_root / module / "src" / "commonMain" / "kotlin" / package_path / source_name,
    ]

    for candidate in candidates:
        if candidate.exists():
            return candidate.relative_to(repo_root)

    return Path(module) / "src" / "main" / "java" / package_path / source_name


def collapse_ranges(lines: list[int]) -> list[str]:
    ranges: list[str] = []
    if not lines:
        return ranges

    start = lines[0]
    end = lines[0]

    for current in lines[1:]:
        if current == end + 1:
            end = current
            continue
        ranges.append(format_range(start, end))
        start = current
        end = current

    ranges.append(format_range(start, end))
    return ranges


def format_range(start: int, end: int) -> str:
    if start == end:
        return str(start)
    return f"{start}-{end}"


def build_blob_url(repo: str, sha: str, file_path: str, line_range: str) -> str:
    if "-" in line_range:
        start, end = line_range.split("-", 1)
        anchor = f"#L{start}-L{end}"
    else:
        anchor = f"#L{line_range}"
    return f"https://github.com/{repo}/blob/{sha}/{file_path}{anchor}"


def render_file_cell(repo: str, sha: str, file_path: str) -> str:
    if not repo or not sha:
        return f"`{file_path}`"
    return f"[`{file_path}`](https://github.com/{repo}/blob/{sha}/{file_path})"


def render_ranges_cell(repo: str, sha: str, file_path: str, ranges: list[str]) -> str:
    if not repo or not sha:
        return f"`{', '.join(ranges)}`"
    links = [f"[`{line_range}`]({build_blob_url(repo, sha, file_path, line_range)})" for line_range in ranges]
    return ", ".join(links)


def detect_language(file_path: str) -> str:
    suffix = Path(file_path).suffix.lower()
    if suffix == ".kt":
        return "kotlin"
    if suffix == ".java":
        return "java"
    return "text"


def parse_range(line_range: str) -> tuple[int, int]:
    if "-" in line_range:
        start, end = line_range.split("-", 1)
        return int(start), int(end)
    number = int(line_range)
    return number, number


def render_snippet(repo_root: Path, file_path: str, line_range: str) -> str:
    start, end = parse_range(line_range)
    file_on_disk = repo_root / file_path
    if not file_on_disk.exists():
        return ""

    content = file_on_disk.read_text(encoding="utf-8").splitlines()
    start_index = max(start - 1, 0)
    end_index = min(end, len(content))

    snippet_lines = [
        f"{line_number:>4} | {content[line_number - 1]}"
        for line_number in range(start_index + 1, end_index + 1)
    ]
    language = detect_language(file_path)
    return "\n".join([f"```{language}", *snippet_lines, "```"])


def main() -> int:
    if len(sys.argv) not in {3, 5}:
        print(
            "usage: build_uncovered_coverage_comment.py <reports_root> <output_file> [repo sha]",
            file=sys.stderr,
        )
        return 2

    reports_root = Path(sys.argv[1])
    output_file = Path(sys.argv[2])
    repo = sys.argv[3] if len(sys.argv) == 5 else ""
    sha = sys.argv[4] if len(sys.argv) == 5 else ""
    repo_root = Path.cwd()

    items: list[tuple[str, list[str]]] = []
    for report in sorted(reports_root.glob("*/build/reports/kover/report.xml")):
        module = report.relative_to(reports_root).parts[0]
        items.extend(collect_uncovered_ranges(report, module, repo_root))

    lines = [
        "## Uncovered Code",
        "",
    ]

    if not items:
        lines.append("- No completely uncovered line ranges found in the published module reports.")
    else:
        selected_items = items[:MAX_FILES]
        lines.extend(
            [
                "The links below point to the exact file and line ranges in the PR head commit.",
                "",
                "| File | Uncovered Lines |",
                "| --- | --- |",
            ]
        )
        for file_path, ranges in selected_items:
            limited_ranges = ranges[:MAX_RANGES_PER_FILE]
            lines.append(
                f"| {render_file_cell(repo, sha, file_path)} | "
                f"{render_ranges_cell(repo, sha, file_path, limited_ranges)} |"
            )

        lines.extend(["", "### Code Snippets", ""])
        for file_path, ranges in selected_items:
            limited_ranges = ranges[:MAX_RANGES_PER_FILE]
            lines.append(f"#### {render_file_cell(repo, sha, file_path)}")
            lines.append("")
            for line_range in limited_ranges:
                if repo and sha:
                    lines.append(f"- Range {render_ranges_cell(repo, sha, file_path, [line_range])}")
                else:
                    lines.append(f"- Range `{line_range}`")
                snippet = render_snippet(repo_root, file_path, line_range)
                if snippet:
                    lines.append("")
                    lines.append(snippet)
                    lines.append("")

    output_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
