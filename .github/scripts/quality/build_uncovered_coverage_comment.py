#!/usr/bin/env python3
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def collect_uncovered_ranges(report: Path, repo_root: Path) -> list[tuple[str, list[str]]]:
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        return []

    module = report.parent.name
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


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "usage: build_uncovered_coverage_comment.py <reports_root> <output_file>",
            file=sys.stderr,
        )
        return 2

    reports_root = Path(sys.argv[1])
    output_file = Path(sys.argv[2])
    repo_root = Path.cwd()

    items: list[tuple[str, list[str]]] = []
    for report in sorted((reports_root / "modules").glob("*/report.xml")):
        items.extend(collect_uncovered_ranges(report, repo_root))

    lines = [
        "## Uncovered Code",
        "",
    ]

    if not items:
        lines.append("- No completely uncovered line ranges found in the published module reports.")
    else:
        lines.extend(
            [
                "| File | Uncovered Lines |",
                "| --- | --- |",
            ]
        )
        for file_path, ranges in items:
            lines.append(f"| `{file_path}` | `{', '.join(ranges)}` |")

    output_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
