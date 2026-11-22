"""Shared, fixed-size graphical template for a Luna server benchmark."""
import hashlib
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from PIL import Image, ImageDraw, ImageFont, PngImagePlugin

WIDTH, HEIGHT = 1260, 1320
MARGIN = 58
NAVY = "#132c47"
SLATE = "#425d75"
TEAL = "#087c78"
PALE = "#edf4f8"
RULE = "#dce8ed"
WHITE = "#ffffff"
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
STATUS_COLORS = {
    "COMPLETE": ("#dff2ec", "#08675b"),
    "PARTIAL": ("#fff0d8", "#824b06"),
    "FAILED": ("#fbe6e8", "#8a1d38"),
    "BLOCKED": ("#f2e9fc", "#633a89"),
}


def value(number, unit="", decimals=2):
    return "N/A" if number is None else f"{number:,.{decimals}f}{unit}"


def duration(seconds):
    if seconds is None:
        return "N/A"
    minutes, rest = divmod(seconds, 60)
    return f"{int(minutes)}m {rest:04.1f}s"


def population(summary):
    online = summary["metrics"].get("online_bots")
    if online:
        if online["min"] == online["max"]:
            return f"{online['min']:,} measured"
        return f"{online['min']:,}–{online['max']:,} measured"
    peak = summary["peak_active_bots_any_phase"]
    return f"{peak:,} pre-measurement peak" if peak is not None else "N/A observed"


def toronto(timestamp):
    return datetime.fromisoformat(timestamp).astimezone(ZoneInfo("America/Toronto"))


def draw(folder, manifest, summary):
    img = Image.new("RGB", (WIDTH, HEIGHT), WHITE)
    drawing = ImageDraw.Draw(img)

    def font(size, bold=False):
        return ImageFont.truetype(str(FONT_DIR / ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf")), size)

    def label(x, y, words, size, color=NAVY, bold=False, max_width=None):
        style = font(size, bold)
        if max_width is not None and drawing.textlength(words, font=style) > max_width:
            raise ValueError(f"Label exceeds {max_width}px: {words!r}")
        drawing.text((x, y), words, font=style, fill=color, anchor="lt")

    def paragraph(x, y, words, size, width, max_lines=2, color=SLATE):
        style = font(size)
        lines = [""]
        for word in words.split():
            next_line = " ".join(item for item in (lines[-1], word) if item)
            if drawing.textlength(next_line, font=style) <= width:
                lines[-1] = next_line
            else:
                lines.append(word)
        if len(lines) > max_lines:
            raise ValueError(f"Paragraph exceeds {max_lines} lines: {words!r}")
        for index, line in enumerate(lines):
            label(x, y + index * (size + 9), line, size, color, max_width=width)

    def rule(y):
        drawing.line((MARGIN, y, WIDTH - MARGIN, y), fill=RULE, width=2)

    metrics = summary["metrics"]
    resources = metrics.get("resources", {})
    gc = metrics.get("gc") or {}
    proc = metrics.get("processing_ms") or {}
    cpu = resources.get("process_cpu_pct") or {}
    rss = resources.get("rss_mib") or {}
    heap = resources.get("heap_max_mib") or {}
    code = manifest["code"]
    date = toronto(manifest.get("server_started_utc") or manifest["attempt_started_utc"]).date()
    label(MARGIN, 53, f"Luna server benchmark - {code['date']}", 47, bold=True, max_width=WIDTH - 2 * MARGIN)
    paragraph(MARGIN, 124, f"{manifest['checkpoint']['label']}  •  {code['sha'][:8]}", 21, 875, 1)
    execution_label = "Executed" if "server_started_utc" in manifest else "Attempt recorded"
    label(MARGIN, 165, f"{execution_label} {date} Toronto   /   {manifest['checkpoint']['release']}", 22, SLATE)
    status_bg, status_fg = STATUS_COLORS[summary["status"]]
    drawing.rounded_rectangle((1010, 113, WIDTH - MARGIN, 157), radius=13, fill=status_bg)
    label(1027, 125, summary["status"], 19, status_fg, True, 159)
    rule(217)

    label(MARGIN, 243, "Wandering-only workload", 25, TEAL, True)
    beta = {"true": "effective", "false": "disabled"}.get(summary["effective_beta"], "N/A")
    label(MARGIN, 283, f"{summary['target_bots']:,} target  /  {population(summary)}  /  Beta mode {beta}", 23, SLATE)
    label(MARGIN, 317, f"Warm-up  {duration(summary['warmup_seconds'])}     Measurement  {duration(summary['duration_seconds'])}",
          23, SLATE)

    card_width = 366
    for index, (name, number) in enumerate((
        ("Mean tick processing time", value(proc.get("mean"), " ms")),
        ("p99 tick processing time", value(proc.get("p99"), " ms")),
        ("Mean process CPU usage", value(cpu.get("mean"), "%")),
    )):
        x = MARGIN + index * 390
        drawing.rounded_rectangle((x, 369, x + card_width, 501), radius=17, fill=PALE)
        label(x + 22, 390, name, 20, SLATE, True, card_width - 44)
        label(x + 22, 434, number, 41, NAVY, True, card_width - 44)

    label(MARGIN, 539, "Tick processing", 30, bold=True)
    label(885, 548, f"{summary['tick_budget_ms']} ms budget per tick", 21, TEAL, True)
    for index, (title, key) in enumerate(
        (("Mean", "mean"), ("Median", "median"), ("p95", "p95"), ("p99", "p99"), ("Maximum", "max"))
    ):
        x = MARGIN + 228 * index
        label(x + 2, 601, title, 19, SLATE)
        label(x + 2, 630, value(proc.get(key), " ms"), 27, bold=True, max_width=220)
    rule(681)

    label(MARGIN, 716, "Resources and reliability", 30, bold=True)
    measured = summary["duration_seconds"]
    count_note = f"Counts from {duration(measured)} of measurement" if measured else "Measurement counts unavailable"
    label(MARGIN, 761, count_note, 20, SLATE)
    for index, (left_name, left, right_name, right) in enumerate((
        ("Resident memory · mean / peak",
         f"{value(rss['mean'], '', 0)} / {value(rss['max'], ' MiB', 0)}" if rss else "N/A",
         "Completed world ticks", value(metrics.get("completed_world_ticks"), decimals=0)),
        ("JVM heap maximum", value(heap.get("max"), " MiB", 0),
         "Ticks over budget", value(metrics.get("over_budget_count"), decimals=0)),
        ("Longest GC pause", value(gc.get("longest_pause_ms"), " ms"),
         f"Tick gaps > {summary['gap_threshold_ms_strictly_greater_than']:g} ms",
         value(metrics.get("scheduling_gap_count"), decimals=0)),
    )):
        y = 804 + index * 45
        if index % 2 == 0:
            drawing.rounded_rectangle((MARGIN, y - 7, WIDTH - MARGIN, y + 33), radius=7, fill=PALE)
        label(MARGIN + 12, y, left_name, 19, SLATE)
        label(428, y, left, 20, NAVY, True, 209)
        label(657, y, right_name, 19, SLATE)
        label(1010, y, right, 20, NAVY, True, 180)

    label(MARGIN, 973, "Interpretation", 28, bold=True)
    paragraph(MARGIN, 1015, summary["graphic_note"], 21, WIDTH - 2 * MARGIN, 3)
    measurement_label = "Patched snapshot" if manifest.get("patch_sha256") else "Revision context"
    paragraph(MARGIN, 1118, f"{measurement_label}  •  {manifest['checkpoint']['context']}",
              19, WIDTH - 2 * MARGIN, 2)
    rule(1212)
    label(MARGIN, 1244, "Process CPU 100% = one fully occupied CPU core. N/A = unavailable (see report).", 19, SLATE)
    temporary = folder / "graphic.png.tmp"
    png_info = PngImagePlugin.PngInfo()
    png_info.add_text("Summary SHA-256", hashlib.sha256((folder / "summary.json").read_bytes()).hexdigest())
    png_info.add_text("Code SHA", code["sha"])
    img.save(temporary, format="PNG", optimize=True, pnginfo=png_info)
    temporary.replace(folder / "graphic.png")
