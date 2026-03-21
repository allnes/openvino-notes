#!/usr/bin/env python3
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


MetricCounts = dict[str, tuple[int, int]]


def read_counters(report: Path) -> MetricCounts | None:
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        return None

    counters: MetricCounts = {}
    for counter in root.findall("./counter"):
        metric = counter.attrib.get("type")
        if metric is None:
            continue
        counters[metric] = (
            int(counter.attrib.get("covered", "0")),
            int(counter.attrib.get("missed", "0")),
        )

    return counters or None


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

    module_rows: list[tuple[str, MetricCounts]] = []
    fallback_rows: list[tuple[str, MetricCounts]] = []

    for report in sorted(reports_root.glob("**/build/reports/kover/report.xml")):
        counts = read_counters(report)
        if counts is None:
            continue

        rel = report.relative_to(reports_root)
        parts = rel.parts

        if len(parts) >= 5 and parts[1:5] == ("build", "reports", "kover", "report.xml"):
            module_rows.append((parts[0], counts))
        else:
            fallback_rows.append((str(rel.parent), counts))

    rows = module_rows or fallback_rows
    total_line_covered = sum(counts.get("LINE", (0, 0))[0] for _, counts in rows)
    total_line_missed = sum(counts.get("LINE", (0, 0))[1] for _, counts in rows)
    total_instruction_covered = sum(counts.get("INSTRUCTION", (0, 0))[0] for _, counts in rows)
    total_instruction_missed = sum(counts.get("INSTRUCTION", (0, 0))[1] for _, counts in rows)
    total_lines = total_line_covered + total_line_missed
    total_instructions = total_instruction_covered + total_instruction_missed

    lines = [
        "## Coverage",
        "",
        (
            f"- Overall line coverage: {format_ratio(total_line_covered, total_line_missed)} "
            f"({total_line_covered} covered / {total_lines} total)"
        ),
        (
            f"- Overall instruction coverage: "
            f"{format_ratio(total_instruction_covered, total_instruction_missed)} "
            f"({total_instruction_covered} covered / {total_instructions} total)"
        ),
    ]

    if rows:
        lines.extend(
            [
                "",
                "| Module | Line Coverage | Line Covered | Line Total | Instruction Coverage | Instruction Covered | Instruction Total |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
            ]
        )

        for module, counts in sorted(rows):
            line_covered, line_missed = counts.get("LINE", (0, 0))
            instruction_covered, instruction_missed = counts.get("INSTRUCTION", (0, 0))
            line_total = line_covered + line_missed
            instruction_total = instruction_covered + instruction_missed
            lines.append(
                f"| `{module}` | {format_ratio(line_covered, line_missed)} | "
                f"{line_covered} | {line_total} | "
                f"{format_ratio(instruction_covered, instruction_missed)} | "
                f"{instruction_covered} | {instruction_total} |"
            )
    else:
        lines.extend(["", "- No Kover XML reports found"])

    output_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
