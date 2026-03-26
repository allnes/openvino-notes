#!/usr/bin/env python3
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


MetricCounts = dict[str, tuple[int, int]]
METRICS: list[tuple[str, str]] = [
    ("LINE", "Line"),
    ("INSTRUCTION", "Instruction"),
    ("BRANCH", "Branch"),
    ("METHOD", "Method"),
    ("CLASS", "Class"),
]


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
        return "n/a"
    return f"{covered / total * 100:.2f}%"


def format_metric_cell(counts: MetricCounts, metric: str) -> str:
    covered, missed = counts.get(metric, (0, 0))
    total = covered + missed
    if total == 0:
        return "n/a (0/0)"
    return f"{format_ratio(covered, missed)} ({covered}/{total})"


def render_table_row(cells: list[str]) -> str:
    return f"| {' | '.join(cells)} |"


def discover_reports(reports_root: Path) -> tuple[MetricCounts | None, list[tuple[str, MetricCounts]]]:
    aggregate_report = reports_root / "build" / "reports" / "kover" / "report.xml"
    if aggregate_report.exists():
        aggregate_counts = read_counters(aggregate_report)
        module_rows: list[tuple[str, MetricCounts]] = []
        for report in reports_root.glob("*/build/reports/kover/report.xml"):
            counts = read_counters(report)
            if counts is None:
                continue
            module_rows.append((report.relative_to(reports_root).parts[0], counts))
        return aggregate_counts, module_rows

    aggregate_counts = None
    module_rows = []
    fallback_rows = []

    for report in sorted(reports_root.glob("**/build/reports/kover/report.xml")):
        counts = read_counters(report)
        if counts is None:
            continue

        rel = report.relative_to(reports_root)
        parts = rel.parts

        if parts == ("build", "reports", "kover", "report.xml"):
            aggregate_counts = counts
        elif len(parts) >= 5 and parts[1:5] == ("build", "reports", "kover", "report.xml"):
            module_rows.append((parts[0], counts))
        else:
            fallback_rows.append((str(rel.parent), counts))

    return aggregate_counts, module_rows or fallback_rows


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: build_coverage_summary.py <reports_root> <output_file>", file=sys.stderr)
        return 2

    reports_root = Path(sys.argv[1])
    output_file = Path(sys.argv[2])

    aggregate_counts, rows = discover_reports(reports_root)

    if aggregate_counts is None and rows:
        aggregate_counts = {}
        for metric, _ in METRICS:
            covered = sum(counts.get(metric, (0, 0))[0] for _, counts in rows)
            missed = sum(counts.get(metric, (0, 0))[1] for _, counts in rows)
            aggregate_counts[metric] = (covered, missed)

    lines = [
        "## Coverage Details",
        "",
    ]

    if aggregate_counts is not None:
        lines.extend(
            [
                "",
                "### Overall",
                "",
                "| Metric | Coverage | Covered | Missed | Total |",
                "| --- | ---: | ---: | ---: | ---: |",
            ]
        )

        for metric, label in METRICS:
            covered, missed = aggregate_counts.get(metric, (0, 0))
            total = covered + missed
            lines.append(
                render_table_row([label, format_ratio(covered, missed), str(covered), str(missed), str(total)])
            )

    if rows:
        lines.extend(
            [
                "",
                "### By Module",
                "",
                "| Module | Line | Instruction | Branch | Method | Class |",
                "| --- | ---: | ---: | ---: | ---: | ---: |",
            ]
        )

        for module, counts in sorted(rows):
            lines.append(
                render_table_row(
                    [
                        f"`{module}`",
                        format_metric_cell(counts, "LINE"),
                        format_metric_cell(counts, "INSTRUCTION"),
                        format_metric_cell(counts, "BRANCH"),
                        format_metric_cell(counts, "METHOD"),
                        format_metric_cell(counts, "CLASS"),
                    ]
                )
            )
    elif aggregate_counts is None:
        lines.extend(["", "- No Kover XML reports found"])

    output_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
