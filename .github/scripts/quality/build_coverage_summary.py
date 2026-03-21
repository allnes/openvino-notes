#!/usr/bin/env python3
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def read_line_counter(report: Path) -> tuple[int, int] | None:
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        return None

    for counter in root.findall("./counter"):
        if counter.attrib.get("type") == "LINE":
            covered = int(counter.attrib.get("covered", "0"))
            missed = int(counter.attrib.get("missed", "0"))
            return covered, missed
    return None


def format_ratio(covered: int, missed: int) -> str:
    total = covered + missed
    if total == 0:
        return "0.00%"
    return f"{covered / total * 100:.2f}%"


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: build_coverage_summary.py <reports_root> <output_file>", file=sys.stderr)
        return 2

    reports_root = Path(sys.argv[1])
    output_file = Path(sys.argv[2])

    module_rows: list[tuple[str, int, int]] = []
    fallback_rows: list[tuple[str, int, int]] = []

    for report in sorted(reports_root.glob("**/build/reports/kover/report.xml")):
        counts = read_line_counter(report)
        if counts is None:
            continue

        covered, missed = counts
        rel = report.relative_to(reports_root)
        parts = rel.parts

        if len(parts) >= 5 and parts[1:5] == ("build", "reports", "kover", "report.xml"):
            module_rows.append((parts[0], covered, missed))
        else:
            fallback_rows.append((str(rel.parent), covered, missed))

    rows = module_rows or fallback_rows
    total_covered = sum(covered for _, covered, _ in rows)
    total_missed = sum(missed for _, _, missed in rows)
    total_lines = total_covered + total_missed

    lines = [
        "## Coverage",
        "",
        (
            f"- Overall line coverage: {format_ratio(total_covered, total_missed)} "
            f"({total_covered} covered / {total_lines} total)"
        ),
    ]

    if rows:
        lines.extend(
            [
                "",
                "| Module | Coverage | Covered | Total |",
                "| --- | ---: | ---: | ---: |",
            ]
        )

        for module, covered, missed in sorted(rows):
            total = covered + missed
            lines.append(
                f"| `{module}` | {format_ratio(covered, missed)} | {covered} | {total} |"
            )
    else:
        lines.extend(["", "- No Kover XML reports found"])

    output_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
