"""Plot the paired tick-work percentiles against Luna's 600 ms budget."""
import json
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import MultipleLocator

root = Path(sys.argv[1])
modes = ("live", "wandering")
data = {
    mode: json.loads((root / mode / "summary.json").read_text())["metrics"]["processing_ms"]
    for mode in modes
}
labels = ("Mean", "99th percentile", "Slowest tick")
keys = ("mean", "p99", "max")
fig, ax = plt.subplots(figsize=(9, 4.35), layout="constrained")
colors = ("#264d91", "#14756e")
for offset, mode, title, color in zip(
    (-0.17, 0.17), modes, ("Autonomous", "Wandering only"), colors
):
    values = [data[mode][key] for key in keys]
    bars = ax.barh([index + offset for index in range(len(keys))], values, height=0.29, color=color, label=title)
    ax.bar_label(bars, labels=[f"{value:.1f} ms" for value in values], padding=5, fontsize=10, color="#162944")

ax.set_yticks(range(len(labels)), labels)
ax.invert_yaxis()
ax.set_xlim(0, max(660, max(row["max"] for row in data.values()) * 1.14))
ax.xaxis.set_major_locator(MultipleLocator(100))
ax.xaxis.grid(True, color="#d9e1eb")
ax.set_axisbelow(True)
ax.axvline(600, linestyle="--", color="#8b4925", linewidth=1.5, label="600 ms budget")
ax.set_xlabel("Game-loop processing time (ms)")
ax.set_title("1,500 Luna bots in each one-hour run", loc="left", fontweight="bold", pad=12)
ax.legend(loc="upper left", bbox_to_anchor=(0, -0.15), frameon=False, ncol=3, borderaxespad=0)
ax.spines[["top", "right", "left"]].set_visible(False)
ax.tick_params(axis="y", length=0)
fig.savefig(root / "comparison.png", dpi=160, facecolor="white")
