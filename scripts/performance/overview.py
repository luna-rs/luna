#!/usr/bin/env python3
"""Render the publication infographic from the three archived benchmark snapshots."""
import json
from datetime import date
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPORTS = Path(__file__).resolve().parents[2] / "data/benchmarks"
SNAPSHOTS = (
    "benchmark-2026-03-26-run-20261122T030608176775Z",
    "benchmark-2026-10-21",
    "benchmark-2026-11-21-run-20261121T235914749522Z",
)
FONTS = Path("/usr/share/fonts/truetype/dejavu")
WIDTH, HEIGHT = 1160, 620
NAVY = "#142c43"
MUTED = "#466175"
TEAL = "#08786d"
BACKGROUND = "#f3f8f8"
LINE = "#d7e4e7"


def render():
    runs = []
    for name in SNAPSHOTS:
        directory = REPORTS / name
        summary = json.loads((directory / "summary.json").read_text())
        manifest = json.loads((directory / "manifest.json").read_text())
        if summary["status"] != "COMPLETE":
            raise ValueError(f"Overview requires a complete snapshot: {name}")
        runs.append((date.fromisoformat(manifest["code"]["date"]), summary))
    november = runs[-1][1]
    budget = november["tick_budget_ms"]
    if any(run["tick_budget_ms"] != budget for _, run in runs):
        raise ValueError("Overview uses a shared tick budget")

    image = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    canvas = ImageDraw.Draw(image)

    def write(x, y, message, size=19, *, bold=False, color=NAVY, anchor="lt"):
        family = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
        font = ImageFont.truetype(str(FONTS / family), size)
        canvas.text((x, y), message, font=font, fill=color, anchor=anchor)

    write(48, 42, f"Luna holds the {budget} ms cadence", 43, bold=True)
    write(50, 106, "Three one-hour snapshots with 800 in-process wandering bots per run", 20, color=MUTED)

    npc_count = november["metrics"]["npc_population"]["max"]
    latest_p99 = november["metrics"]["processing_ms"]["p99"]
    overruns = sum(run["metrics"]["over_budget_count"] for _, run in runs)
    cards = (
        (f"{npc_count:,}", "NPCs registered in November"),
        (f"{latest_p99:.2f} ms", "November p99 tick work"),
        (str(overruns), "Over-budget ticks across all runs"),
    )
    for i, (number, description) in enumerate(cards):
        x = 48 + i * 360
        canvas.rounded_rectangle((x, 151, x + 344, 269), radius=14, fill="white", outline=LINE, width=2)
        write(x + 19, 173, number, 39, color=TEAL, bold=True)
        write(x + 19, 231, description, 16, color=MUTED)

    write(49, 308, "p99 tick work by code date", 23, bold=True)
    write(1110, 313, f"Shared {budget} ms tick budget", 16, color=MUTED, anchor="rt")
    left, right = 230, 1110
    for i, (snapshot, run) in enumerate(runs):
        y = 352 + 57 * i
        tick_p99 = run["metrics"]["processing_ms"]["p99"]
        write(50, y + 7, f"{snapshot:%B} {snapshot.day}", 18, bold=True)
        canvas.rounded_rectangle((left, y, right, y + 31), radius=10, fill=LINE)
        end = left + round((right - left) * tick_p99 / budget)
        canvas.rounded_rectangle((left, y, end, y + 31), radius=8, fill=TEAL)
        write(end + 12, y + 7, f"{tick_p99:.2f} ms", 16, bold=True)
    write(left, 510, "0 ms", 15, color=MUTED)
    write(right, 510, f"{budget} ms", 15, color=MUTED, anchor="rt")

    canvas.line((48, 552, 1112, 552), fill=LINE, width=2)
    write(49, 574, "Movement and background world processing · November snapshot was provisional pre-beta", 16,
          color=MUTED)
    image.save(REPORTS / "overview.png", optimize=True)


if __name__ == "__main__":
    render()
