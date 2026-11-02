#!/usr/bin/env python3
"""Native desktop viewer for the local Luna Atlas service."""

import argparse
import hashlib
import re
from collections import OrderedDict
import io
import json
import math
import queue
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, ttk, font as tkfont

from PIL import Image, ImageDraw, ImageTk
import geometry as catalog_geometry
import labels as map_labels
from grid_ui import GridSupport


BG = "#111a20"
PANEL = "#18252d"
FIELD = "#20323b"
TEXT = "#e7e4d7"
MUTED = "#9aadb5"
GOLD = "#d5b56d"
CYAN = "#70c8cc"
GREEN = "#99c58d"
RED = "#e29a83"


def part_bounds(part):
    """Integer extent of a catalog part; author lare96."""
    if part.get("type") == "polygon":
        points = part.get("vertices", [])
        if not points:
            return None
        return {"minX": min(p[0] for p in points), "minY": min(p[1] for p in points),
                "maxX": max(p[0] for p in points), "maxY": max(p[1] for p in points)}
    if all(k in part for k in ("minX", "minY", "maxX", "maxY")):
        return {k: part[k] for k in ("minX", "minY", "maxX", "maxY")}
    return None


class CatalogModel:
    """Atlas-only visibility and geometry for an immutable source catalog.

    @author lare96
    """

    PALETTE = ("#65c5e0", "#d5ae6b", "#8dcf8a", "#c395da", "#e78d88", "#84b7ed", "#d6cf77")

    def __init__(self, entries=()):
        self.entries = {str(item["id"]): item for item in entries}
        self.visible = {key: item.get("activation", {}).get("status") in ("registered", "conditional")
                        for key, item in self.entries.items()}
        self.children = {key: [] for key in self.entries}
        for key, entry in self.entries.items():
            parent = entry.get("parentId")
            if parent in self.children and parent != key:
                self.children[parent].append(key)
        self._outlines = {}
        self.spatial = {}
        self.broad = {plane: set() for plane in range(4)}
        for key, entry in self.entries.items():
            for plane in range(4):
                for part in self.parts(entry, plane):
                    extent = part_bounds(part)
                    x0, x1 = max(0, int(extent["minX"]) // 64), min(255, int(extent["maxX"]) // 64)
                    y0, y1 = max(0, int(extent["minY"]) // 64), min(255, int(extent["maxY"]) // 64)
                    if (x1 - x0 + 1) * (y1 - y0 + 1) > 1024:
                        self.broad[plane].add(key)
                        continue
                    for rx in range(x0, x1 + 1):
                        for ry in range(y0, y1 + 1):
                            self.spatial.setdefault((plane, rx, ry), set()).add(key)

    def parts(self, entry, plane):
        return [part for part in catalog_geometry.parts_for_plane(entry, plane) if part_bounds(part)]

    def contains(self, entry, x, y, plane):
        return catalog_geometry.contains(entry, x, y, plane)

    def hits(self, x, y, plane, visible_only=True):
        keys = self.spatial.get((plane, int(x) // 64, int(y) // 64), set()) | self.broad.get(plane, set())
        hits = [self.entries[key] for key in keys
                if (not visible_only or self.visible.get(key)) and self.contains(self.entries[key], x, y, plane)]
        return sorted(hits, key=lambda item: (item.get("kind") != "subzone", self.area(item), item["name"]))

    @staticmethod
    def area(entry):
        return sum(max(0, b["maxX"] - b["minX"] + 1) * max(0, b["maxY"] - b["minY"] + 1)
                   for part in entry.get("parts", []) if (b := part_bounds(part)))

    def descendants(self, key):
        result, pending = [], [key]
        while pending:
            current = pending.pop()
            if current in result:
                continue
            result.append(current)
            pending.extend(self.children.get(current, []))
        return result

    def colour(self, entry):
        if hasattr(catalog_geometry, "catalog_colour"):
            return catalog_geometry.catalog_colour(entry)
        family = str(entry.get("parentId") or entry["id"])
        index = int(hashlib.sha256(family.encode()).hexdigest()[:8], 16) % len(self.PALETTE)
        return self.PALETTE[index]

    def outlines(self, entry, plane):
        key = str(entry["id"]), plane
        if key not in self._outlines:
            self._outlines[key] = tuple(((a, b), (c, d)) for a, b, c, d in catalog_geometry.outline_segments(entry, plane))
        return self._outlines[key]


class AsyncClient:
    """
    Fetch local service data without blocking the desktop event loop.

    @author lare96
    """

    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.pool = ThreadPoolExecutor(max_workers=6, thread_name_prefix="atlas-http")
        self.tile_pool = ThreadPoolExecutor(max_workers=3, thread_name_prefix="atlas-tiles")
        self.events = queue.Queue()

    def request(self, path, payload=None, binary=False, timeout=None):
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            self.base_url + path, data=data,
            headers={"Content-Type": "application/json"} if data is not None else {},
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout or (120 if path.startswith("/api/project/") else 30)) as response:
                body = response.read()
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", "replace")
            try:
                body = json.loads(body).get("error", body)
            except (ValueError, AttributeError):
                pass
            raise RuntimeError(str(body)[:400]) from error
        return body if binary else json.loads(body)

    def submit(self, work, callback, error_callback=None, *, tile=False):
        future = (self.tile_pool if tile else self.pool).submit(work)

        def finished(done):
            try:
                result = done.result()
                self.events.put((callback, result))
            except Exception as error:
                self.events.put((error_callback, str(error)))

        future.add_done_callback(finished)


class AtlasApp(GridSupport):
    """
    Inspect cache maps and prepare reviewable spatial drafts.

    @author lare96
    """

    def __init__(self, root, url):
        self.root = root
        self.client = AsyncClient(url)
        self.alive = True
        self.summary = {}
        self.regions = set()
        self.center_x, self.center_y = 3222.5, 3218.5
        self.scale = 4.0
        self.plane = tk.IntVar(value=0)
        self.mode = tk.StringVar(value="zones")
        self.grid = tk.BooleanVar(value=False)
        self.init_grid_support()
        self.show_existing = tk.BooleanVar(value=True)
        self.overlay_labels = tk.BooleanVar(value=True)
        self.subzone_labels = tk.BooleanVar(value=True)
        self.zone_boundaries = tk.BooleanVar(value=True)
        self.subzone_boundaries = tk.BooleanVar(value=True)
        self.controller_boundaries = tk.BooleanVar(value=True)
        self.npc_markers = tk.BooleanVar(value=True)
        self.label_styles = {key: dict(value) for key, value in map_labels.DEFAULT_STYLES.items()}
        self.label_fonts = {}
        self.label_metrics = OrderedDict()
        self.label_boxes = []
        self.npcs = []
        self.npc_buckets = {}
        self.npc_source = None
        self.npc_hit_points = []
        self.selected_entity = None
        self.entity_dialog = None
        self.overlay_opacity = tk.DoubleVar(value=18)
        self.catalog_query = tk.StringVar()
        self.catalog_info = tk.StringVar(value="Open a Luna directory to explore its zones.")
        self.catalog = CatalogModel()
        self.catalog_source = None
        self.selected_catalog = None
        self.catalog_photo_cache = OrderedDict()
        self.catalog_photo_bytes = 0
        self.catalog_visible_photos = []
        self.catalog_overlay_version = 0
        self.catalog_hover_tile = None
        self.catalog_hover_text = ""
        self.catalog_dialog = None
        self.project_identity = None
        self.dataset_identity = None
        self.project_opening = False
        self.preferences_ready = False
        self.last_preferences = {}
        self.catalog_restore_visibility = None
        self.pending_project_request = None
        self.preference_after = None
        self.sidebar_visible = True
        self.status = tk.StringVar(value="Connecting to the local cache service…")
        self.hover = tk.StringVar(value="Move over the map to inspect coordinates")
        self.header_detail = tk.StringVar(value="Reading cache information…")
        self.tiles = OrderedDict()
        self.tile_bytes = 0
        self.tile_budget = 64 * 1024 * 1024
        self.pending_tiles = set()
        self.failed_tiles = set()
        self.photo_tiles = OrderedDict()
        self.photo_bytes = 0
        self.photo_budget = 48 * 1024 * 1024
        self.canvas_tiles = {}
        self.desired_tiles = []
        self.metrics = {"draws": 0, "photoCreates": 0, "tileRequests": 0, "lastDrawMs": 0}
        self.zones = []
        self.selected_zone = None
        self.inspected = None
        self.search_points = []
        self.first_corner = None
        self.preview_corner = None
        self.drag = None
        self.undo_stack, self.redo_stack = [], []
        self.draw_after = None
        self.generations = {"summary": 0, "search": 0, "catalog": 0, "preferences": 0, "project": 0, "npcs": 0, "entity": 0}
        self.cache_generation = 0
        self.cache_identity = None
        self._style()
        self._build()
        self.root.after(40, self._poll)
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.root.bind("<Control-z>", lambda event: self.undo())
        self.root.bind("<Control-y>", lambda event: self.redo())
        self.root.bind("<Escape>", lambda event: self.cancel_rectangle())
        self.load_summary()

    def _style(self):
        self.root.title("Luna Atlas 377 · World workspace")
        self.root.geometry("1440x920")
        self.root.minsize(1100, 700)
        self.root.configure(bg=BG)
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure(".", background=PANEL, foreground=TEXT, font=("Segoe UI", 10))
        style.configure("TFrame", background=PANEL)
        style.configure("Top.TFrame", background=BG)
        style.configure("TLabel", background=PANEL, foreground=TEXT)
        style.configure("Muted.TLabel", foreground=MUTED)
        style.configure("Top.TLabel", background=BG, foreground=MUTED)
        style.configure("Brand.TLabel", background=BG, foreground=GOLD, font=("Segoe UI", 21, "bold"))
        style.configure("Title.TLabel", font=("Segoe UI", 12, "bold"), foreground=GOLD)
        style.configure("TButton", background=FIELD, foreground=TEXT, padding=(9, 6), borderwidth=0)
        style.map("TButton", background=[("active", "#36505a"), ("disabled", PANEL)], foreground=[("disabled", "#667b85")])
        style.configure("Accent.TButton", background="#75623b", foreground="#fff1c5")
        style.map("Accent.TButton", background=[("active", "#917847")])
        style.configure("TEntry", fieldbackground=FIELD, foreground=TEXT, insertcolor=TEXT, padding=5)
        style.configure("TSpinbox", fieldbackground=FIELD, foreground=TEXT, arrowcolor=GOLD)
        style.configure("TCheckbutton", background=PANEL, foreground=TEXT)
        style.map("TCheckbutton", background=[("active", PANEL)])
        style.configure("TRadiobutton", background=PANEL, foreground=TEXT)
        style.map("TRadiobutton", background=[("active", PANEL)])
        style.configure("TNotebook", background=PANEL, borderwidth=0)
        style.configure("TNotebook.Tab", background=FIELD, foreground=MUTED, padding=(11, 9))
        style.map("TNotebook.Tab", background=[("selected", PANEL)], foreground=[("selected", GOLD)])
        style.configure("Treeview", background=FIELD, fieldbackground=FIELD, foreground=TEXT, rowheight=28, borderwidth=0)
        style.configure("Treeview.Heading", background=PANEL, foreground=MUTED, padding=5)
        style.map("Treeview", background=[("selected", "#42606a")], foreground=[("selected", "#ffffff")])
        style.configure("TPanedwindow", background=BG)
        self.root.option_add("*TCombobox*Listbox.background", FIELD)
        self.root.option_add("*TCombobox*Listbox.foreground", TEXT)

    def _build(self):
        top = ttk.Frame(self.root, style="Top.TFrame", padding=(16, 10, 16, 8))
        top.pack(fill="x")
        ttk.Label(top, text="LUNA ATLAS", style="Brand.TLabel").pack(side="left")
        title = ttk.Frame(top, style="Top.TFrame", padding=(18, 0))
        title.pack(side="left", fill="x", expand=True)
        self.project_label = ttk.Label(title, text="Revision 377 · Map and zone workspace", style="Top.TLabel", font=("Segoe UI", 11, "bold"))
        self.project_label.pack(anchor="w")
        ttk.Label(title, textvariable=self.header_detail, style="Top.TLabel").pack(anchor="w", pady=(3, 0))
        ttk.Button(top, text="Open Luna Directory…", style="Accent.TButton", command=self.open_luna_directory).pack(side="right", padx=(8, 0))
        ttk.Button(top, text="Reload", command=self.reload_project).pack(side="right", padx=(8, 0))
        ttk.Button(top, text="Report", command=self.project_report).pack(side="right", padx=(8, 0))
        ttk.Button(top, text="Help", command=self.help).pack(side="right", padx=(8, 0))

        tools = ttk.Frame(self.root, padding=(12, 7))
        tools.pack(fill="x", padx=10, pady=(0, 8))
        self.sidebar_button = ttk.Button(tools, text="☰ Controls", command=self.toggle_sidebar)
        self.sidebar_button.pack(side="left", padx=(0, 14))
        for text, value in (("Zones", "zones"), ("Inspect tile", "inspect"), ("Draw area", "rectangle")):
            ttk.Radiobutton(tools, text=text, variable=self.mode, value=value, command=self.cancel_rectangle).pack(side="left", padx=(0, 12))
        ttk.Label(tools, text="Plane").pack(side="left", padx=(4, 6))
        ttk.Spinbox(tools, from_=0, to=3, width=3, textvariable=self.plane, command=self.change_plane, state="readonly").pack(side="left")
        self.build_grid_toolbar(tools)
        ttk.Checkbutton(tools, text="Zone overlays", variable=self.show_existing, command=self.display_changed).pack(side="left", padx=(0, 12))
        ttk.Checkbutton(tools, text="NPCs", variable=self.npc_markers, command=self.display_changed).pack(side="left", padx=8)
        navigation = ttk.Frame(self.root, padding=(12, 0, 12, 7))
        navigation.pack(fill="x", padx=10, pady=(0, 8))
        ttk.Button(navigation, text="−", width=3, command=lambda: self.zoom(.5)).pack(side="left")
        ttk.Button(navigation, text="+", width=3, command=lambda: self.zoom(2)).pack(side="left", padx=(4, 10))
        ttk.Button(navigation, text="Lumbridge", command=lambda: self.go_to(3222, 3218, 0)).pack(side="left")
        ttk.Button(navigation, text="Export map…", command=self.export_map).pack(side="right", padx=(8, 0))
        ttk.Button(navigation, text="Redo", command=self.redo).pack(side="right", padx=(5, 0))
        ttk.Button(navigation, text="Undo", command=self.undo).pack(side="right", padx=(5, 0))

        self.panes = ttk.Panedwindow(self.root, orient="horizontal")
        self.panes.pack(fill="both", expand=True, padx=10)
        self.sidebar = ttk.Frame(self.panes, width=360, padding=(0, 0, 10, 0))
        self.panes.add(self.sidebar, weight=0)
        self.notebook = ttk.Notebook(self.sidebar)
        self.notebook.pack(fill="both", expand=True)
        self.catalog_tab = ttk.Frame(self.notebook, padding=10)
        self.inspect_tab = ttk.Frame(self.notebook, padding=10)
        self.search_tab = ttk.Frame(self.notebook, padding=10)
        self.zones_tab = ttk.Frame(self.notebook, padding=10)
        self.code_tab = ttk.Frame(self.notebook, padding=10)
        for frame, label in ((self.catalog_tab, "Zones"), (self.inspect_tab, "Tile"), (self.search_tab, "Find"), (self.zones_tab, "Drafts"), (self.code_tab, "Code")):
            self.notebook.add(frame, text=label)
        self._catalog_panel()
        self._inspect_panel()
        self._search_panel()
        self._zone_panel()
        self._code_panel()

        map_frame = ttk.Frame(self.panes)
        self.panes.add(map_frame, weight=1)
        map_top = ttk.Frame(map_frame, padding=(10, 7))
        map_top.pack(fill="x")
        ttk.Label(map_top, textvariable=self.hover, foreground=CYAN, font=("Consolas", 10)).pack(side="left")
        self.scale_label = ttk.Label(map_top, text="4 px / tile", style="Muted.TLabel")
        self.scale_label.pack(side="right")
        self.canvas = tk.Canvas(map_frame, bg="#0b1217", highlightthickness=0, cursor="crosshair")
        self.canvas.pack(fill="both", expand=True)
        self.canvas.bind("<Configure>", lambda event: self.schedule_draw())
        self.canvas.bind("<Motion>", self.motion)
        self.canvas.bind("<ButtonPress-1>", self.press)
        self.canvas.bind("<B1-Motion>", self.drag_map)
        self.canvas.bind("<ButtonRelease-1>", self.release)
        for button in (2, 3):
            self.canvas.bind(f"<ButtonPress-{button}>", self.press_pan)
            self.canvas.bind(f"<B{button}-Motion>", self.drag_map)
            self.canvas.bind(f"<ButtonRelease-{button}>", self.release_pan)
        self.canvas.bind("<MouseWheel>", self.wheel)
        self.canvas.bind("<Button-4>", lambda e: self.zoom(2, e.x, e.y))
        self.canvas.bind("<Button-5>", lambda e: self.zoom(.5, e.x, e.y))
        legend = ttk.Frame(map_frame, padding=(10, 6))
        legend.pack(fill="x")
        ttk.Label(legend, text="▰ Source zones", foreground=CYAN).pack(side="left")
        ttk.Label(legend, text="▰ Area drafts", foreground=GOLD).pack(side="left", padx=12)
        ttk.Label(legend, text="● Inside tile", foreground=GREEN).pack(side="left")
        self.containment_label = ttk.Label(legend, text="", style="Muted.TLabel")
        self.containment_label.pack(side="right")
        footer = ttk.Frame(self.root, style="Top.TFrame", padding=(16, 9))
        footer.pack(fill="x")
        ttk.Label(footer, textvariable=self.status, style="Top.TLabel", wraplength=1350).pack(anchor="w")
        self.root.after(100, lambda: self.panes.sashpos(0, 360) if self.sidebar_visible else None)

    def _catalog_panel(self):
        self.catalog_tab.columnconfigure(0, weight=1)
        self.catalog_tab.rowconfigure(1, weight=1)
        header = ttk.Frame(self.catalog_tab)
        header.grid(row=0, column=0, sticky="ew")
        ttk.Label(header, text="Luna zones", style="Title.TLabel").pack(anchor="w")
        ttk.Label(header, text="Source registration · Atlas visibility only", style="Muted.TLabel").pack(anchor="w", pady=(3, 8))
        entry = ttk.Entry(header, textvariable=self.catalog_query)
        entry.pack(fill="x")
        entry.bind("<KeyRelease>", lambda event: self.refresh_catalog_tree())
        ttk.Label(header, text="Filter names, IDs, status or x,y[,plane]", style="Muted.TLabel", font=("Segoe UI", 9)).pack(anchor="w", pady=(3, 7))
        controls = ttk.Frame(header)
        controls.pack(fill="x")
        ttk.Button(controls, text="Show all", command=lambda: self.set_all_catalog(True)).pack(side="left", fill="x", expand=True)
        ttk.Button(controls, text="Hide all", command=lambda: self.set_all_catalog(False)).pack(side="left", fill="x", expand=True, padx=(6, 0))
        tree_frame = ttk.Frame(self.catalog_tab)
        tree_frame.grid(row=1, column=0, sticky="nsew", pady=(8, 5))
        self.catalog_tree = ttk.Treeview(tree_frame, columns=("visible", "status"), show="tree headings", selectmode="browse", height=10)
        self.catalog_tree.heading("#0", text="Zone / subzone")
        self.catalog_tree.column("#0", width=180, minwidth=100, stretch=True)
        self.catalog_tree.heading("visible", text="View")
        self.catalog_tree.column("visible", width=42, minwidth=38, stretch=False, anchor="center")
        self.catalog_tree.heading("status", text="Source status")
        self.catalog_tree.column("status", width=94, minwidth=65, stretch=False)
        self.catalog_tree.pack(side="left", fill="both", expand=True)
        scroll = ttk.Scrollbar(tree_frame, orient="vertical", command=self.catalog_tree.yview)
        self.catalog_tree.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        self.catalog_tree.bind("<<TreeviewSelect>>", self.select_catalog_tree)
        self.catalog_tree.bind("<Button-1>", self.catalog_tree_click)
        self.catalog_tree.bind("<Double-1>", lambda event: self.focus_catalog())
        self.catalog_tree.bind("<space>", lambda event: self.toggle_catalog_selected())
        footer = ttk.Frame(self.catalog_tab)
        footer.grid(row=2, column=0, sticky="ew")
        row = ttk.Frame(footer)
        row.pack(fill="x", pady=(0, 6))
        ttk.Button(row, text="Focus", command=self.focus_catalog).pack(side="left", fill="x", expand=True)
        ttk.Button(row, text="Group on", command=lambda: self.set_catalog_group(True)).pack(side="left", fill="x", expand=True, padx=(5, 0))
        ttk.Button(row, text="Group off", command=lambda: self.set_catalog_group(False)).pack(side="left", fill="x", expand=True, padx=(5, 0))
        style = ttk.Frame(footer)
        style.pack(fill="x")
        ttk.Checkbutton(style, text="Zone labels", variable=self.overlay_labels, command=self.display_changed).pack(side="left")
        ttk.Checkbutton(style, text="Subzone labels", variable=self.subzone_labels, command=self.display_changed).pack(side="left", padx=(8, 0))
        style = ttk.Frame(footer)
        style.pack(fill="x", pady=(5, 0))
        ttk.Button(style, text="Layers / label fonts…", command=self.label_settings).pack(side="left")
        ttk.Label(style, text="Opacity", style="Muted.TLabel").pack(side="left", padx=(15, 5))
        ttk.Scale(style, from_=0, to=65, variable=self.overlay_opacity, command=lambda value: self.opacity_changed()).pack(side="left", fill="x", expand=True)
        self.catalog_details = tk.Text(footer, height=6, bg=FIELD, fg=TEXT, relief="flat", wrap="word", font=("Segoe UI", 9), padx=9, pady=8)
        self.catalog_details.pack(fill="x", pady=(8, 0))
        self.set_text(self.catalog_details, "Choose a zone to inspect its actual geometry, registration status and source evidence.")
        ttk.Button(footer, text="Full details / source evidence…", command=self.catalog_details_dialog).pack(fill="x", pady=(6, 0))
        ttk.Label(footer, textvariable=self.catalog_info, style="Muted.TLabel", wraplength=330, font=("Segoe UI", 9)).pack(anchor="w", pady=(7, 0))

    def toggle_sidebar(self, remember=True):
        if self.sidebar_visible:
            self.panes.forget(self.sidebar)
        else:
            self.panes.insert(0, self.sidebar, weight=0)
            self.panes.sashpos(0, 360)
        self.sidebar_visible = not self.sidebar_visible
        self.sidebar_button.configure(text="☰ Controls" if self.sidebar_visible else "☰ Show controls")
        self.schedule_draw()
        if remember:
            self.queue_preferences()

    def display_changed(self):
        self.invalidate_catalog_fill()
        self.schedule_draw()
        self.queue_preferences()

    def layer_preferences(self):
        return {'zoneBoundaries': self.zone_boundaries.get(), 'subzoneBoundaries': self.subzone_boundaries.get(),
                'controllerBoundaries': self.controller_boundaries.get(), 'zoneLabels': self.overlay_labels.get(),
                'subzoneLabels': self.subzone_labels.get(), 'npcMarkers': self.npc_markers.get()}

    def boundary_enabled(self, kind):
        return {'zone': self.zone_boundaries, 'subzone': self.subzone_boundaries,
                'controller': self.controller_boundaries}.get(kind, self.zone_boundaries).get()

    def label_font(self, style):
        key = style['family'], style['size'], style['bold'], style['italic']
        if key not in self.label_fonts:
            self.label_fonts[key] = tkfont.Font(root=self.root, family=key[0], size=key[1],
                                               weight='bold' if key[2] else 'normal', slant='italic' if key[3] else 'roman')
        return self.label_fonts[key]

    def label_measure(self, text, style):
        key = text, style['family'], style['size'], style['bold'], style['italic']
        if key in self.label_metrics:
            self.label_metrics.move_to_end(key)
            return self.label_metrics[key]
        font = self.label_font(style)
        result = font.measure(text), font.metrics('linespace')
        self.label_metrics[key] = result
        while len(self.label_metrics) > 2000:
            self.label_metrics.popitem(last=False)
        return result

    def label_settings(self):
        dialog = tk.Toplevel(self.root)
        dialog.title('Map layers and label fonts')
        dialog.geometry('610x550')
        dialog.configure(bg=PANEL)
        dialog.transient(self.root)
        frame = ttk.Frame(dialog, padding=18)
        frame.pack(fill='both', expand=True)
        ttk.Label(frame, text='Independent map layers', style='Title.TLabel').pack(anchor='w')
        for title, variable in (('Zone boundaries', self.zone_boundaries), ('Subzone boundaries', self.subzone_boundaries),
                                ('Controller boundaries', self.controller_boundaries), ('Zone labels', self.overlay_labels),
                                ('Subzone labels', self.subzone_labels), ('NPC spawn markers', self.npc_markers)):
            ttk.Checkbutton(frame, text=title, variable=variable, command=self.display_changed).pack(anchor='w', pady=2)
        families = sorted(set(tkfont.families(self.root)))
        editing = {}
        for kind, title in (('zone', 'Zone names · prominent'), ('subzone', 'Subzone names · smaller')):
            section = ttk.LabelFrame(frame, text=title, padding=10)
            section.pack(fill='x', pady=(12, 0))
            style = self.label_styles[kind]
            values = {'family': tk.StringVar(value=style['family']), 'size': tk.IntVar(value=style['size']),
                      'bold': tk.BooleanVar(value=style['bold']), 'italic': tk.BooleanVar(value=style['italic'])}
            editing[kind] = values
            ttk.Combobox(section, textvariable=values['family'], values=families, width=25).pack(side='left')
            ttk.Spinbox(section, from_=7, to=32, textvariable=values['size'], width=4).pack(side='left', padx=8)
            ttk.Checkbutton(section, text='Bold', variable=values['bold']).pack(side='left')
            ttk.Checkbutton(section, text='Italic', variable=values['italic']).pack(side='left', padx=8)
        ttk.Label(frame, text='Labels stay anchored in actual areas. Overlaps and small subzones are suppressed at low zoom.\nThe selected visible area is given label priority.', style='Muted.TLabel', wraplength=560).pack(anchor='w', pady=12)
        def apply():
            try:
                for kind, values in editing.items():
                    self.label_styles[kind] = map_labels.normalise_style({key: value.get() for key, value in values.items()}, kind)
            except (ValueError, tk.TclError):
                self.error('Enter a font size from 7 to 32 points.')
                return
            self.label_fonts.clear()
            self.label_metrics.clear()
            self.display_changed()
            dialog.destroy()
        ttk.Button(frame, text='Apply fonts', command=apply).pack(anchor='e')

    def reveal_catalog_result(self, key):
        entry = self.catalog.entries.get(key)
        if not entry:
            self.error('This zone is not in the current source catalog. Refresh the project.')
            return
        self.selected_entity = None
        self.generations['entity'] += 1
        self.catalog.visible[key] = True
        self.show_existing.set(True)
        if entry.get('kind') == 'subzone':
            self.subzone_boundaries.set(True)
            self.subzone_labels.set(True)
        else:
            self.zone_boundaries.set(True)
            self.overlay_labels.set(True)
        self.select_catalog(key, from_map=True)
        self.focus_catalog()
        self.status.set('Focused ' + entry.get('name', key) + ' · revealed its boundary and label in Atlas.')

    def load_npcs(self):
        self.generations['entity'] += 1
        self.guarded_request('npcs', lambda: self.client.request('/api/npcs'), self.loaded_npcs)

    def loaded_npcs(self, data):
        if data.get('projectIdentity', self.project_identity) != self.project_identity:
            return
        self.npcs = data.get('spawns', [])
        self.npc_buckets = {}
        for item in self.npcs:
            if all(type(item.get(k)) is int for k in ('x', 'y', 'plane')):
                self.npc_buckets.setdefault((item['plane'], item['x'] // 64, item['y'] // 64), []).append(item)
        self.npc_source = data.get('sourceHash')
        if self.selected_entity and self.selected_entity.get('kind') == 'npc':
            key = self.entity_key(self.selected_entity)
            current = next((item for item in self.npcs if self.entity_key(item) == key), None)
            self.selected_entity = dict(current, kind='npc') if current else None
        self.schedule_draw()

    @staticmethod
    def entity_key(item):
        return str(item.get('instanceId', item.get('id', '')))

    def draw_npcs(self, bounds, plane):
        self.npc_hit_points = []
        if not self.npc_markers.get():
            return
        occupied = set()
        marker_count = 0
        spacing = 13 if self.scale < 2 else 5
        half_width, half_height = self.canvas.winfo_width() / 2, self.canvas.winfo_height() / 2
        scale, center_x, center_y = self.scale, self.center_x, self.center_y
        for rx in range(max(0, bounds['minX'] // 64), min(255, bounds['maxX'] // 64) + 1):
            for ry in range(max(0, bounds['minY'] // 64), min(255, bounds['maxY'] // 64) + 1):
                for item in self.npc_buckets.get((plane, rx, ry), ()):
                    if not (bounds['minX'] <= item['x'] <= bounds['maxX'] and bounds['minY'] <= item['y'] <= bounds['maxY']):
                        continue
                    x = (item['x'] + .5 - center_x) * scale + half_width
                    y = (center_y - item['y'] - .5) * scale + half_height
                    cell = round(x / spacing), round(y / spacing)
                    if marker_count >= 1200 and cell not in occupied:
                        continue
                    self.npc_hit_points.append((x, y, item))
                    if cell in occupied:
                        continue
                    occupied.add(cell)
                    marker_count += 1
                    radius = 2 if self.scale < 4 else 3
                    self.canvas.create_oval(x - radius, y - radius, x + radius, y + radius,
                                            fill='#f4de80', outline='#4b3d1a', tags='overlay')

    def draw_selected_entity(self, plane):
        item = self.selected_entity
        if not item or item.get('x') is None or item.get('plane') != plane:
            return
        x, y = self.screen(item['x'] + .5, item['y'] + .5)
        self.canvas.create_oval(x - 8, y - 8, x + 8, y + 8, outline='#ffffff', width=2, tags='overlay')
        self.canvas.create_text(x + 11, y - 12, text=item.get('name', 'Selected entity'), anchor='sw',
                                fill='#fff2c9', font=('Segoe UI', 10, 'bold'), tags='overlay')

    def select_npc_at(self, px, py):
        if not self.npc_markers.get():
            return False
        hits = [(distance, item) for x, y, item in self.npc_hit_points
                if (distance := (x - px) ** 2 + (y - py) ** 2) <= 49]
        if not hits:
            return False
        hits.sort(key=lambda value: (value[0], self.entity_key(value[1])))
        if len(hits) == 1:
            self.select_entity(dict(hits[0][1], kind='npc'), from_marker=True)
            return True
        dialog = tk.Toplevel(self.root)
        dialog.title('NPC placements at this marker')
        dialog.geometry('610x340')
        frame = ttk.Frame(dialog, padding=14)
        frame.pack(fill='both', expand=True)
        choices = tk.Listbox(frame, bg=FIELD, fg=TEXT, selectbackground='#42606a', font=('Segoe UI', 10))
        choices.pack(fill='both', expand=True)
        for _, item in hits[:100]:
            choices.insert('end', f"{item.get('name', 'NPC')} · ID {item.get('npcId', '?')} · {item['x']}, {item['y']}, {item['plane']}")
        choices.selection_set(0)
        def choose(event=None):
            selected = choices.curselection()
            if selected:
                self.select_entity(dict(hits[selected[0]][1], kind='npc'), from_marker=True)
                dialog.destroy()
        choices.bind('<Double-1>', choose)
        choices.bind('<Return>', choose)
        ttk.Button(frame, text='Inspect placement', command=choose).pack(anchor='e', pady=(8, 0))
        return True

    def opacity_changed(self):
        self.invalidate_catalog_fill()
        self.schedule_draw()
        self.queue_preferences()

    def invalidate_catalog_fill(self):
        self.catalog_overlay_version += 1
        self.catalog_photo_cache.clear()
        self.catalog_photo_bytes = 0
        self.catalog_visible_photos = []

    def load_catalog(self):
        self.guarded_request("catalog", lambda: self.client.request("/api/catalog"), self.loaded_catalog)

    def loaded_catalog(self, data):
        old_visible = self.catalog.visible
        self.catalog_source = data
        self.catalog = CatalogModel(data.get("entries", []))
        for key in self.catalog.entries:
            if key in old_visible:
                self.catalog.visible[key] = old_visible[key]
        if self.catalog_restore_visibility is not None:
            for key, value in self.catalog_restore_visibility.items():
                if key in self.catalog.entries and isinstance(value, bool):
                    self.catalog.visible[key] = value
            self.catalog_restore_visibility = None
        self.invalidate_catalog_fill()
        self.catalog_hover_tile = None
        counts = {}
        for item in self.catalog.entries.values():
            kind = item.get("kind", "zone")
            counts[kind] = counts.get(kind, 0) + 1
        diagnostics = data.get("diagnostics", [])
        self.catalog_info.set(" · ".join(f"{value} {key}s" for key, value in counts.items()) +
                              (f" · {len(diagnostics)} diagnostic(s); see Report" if diagnostics else ""))
        self.refresh_catalog_tree()
        self.schedule_draw()

    def refresh_catalog_tree(self):
        if not hasattr(self, "catalog_tree"):
            return
        tree = self.catalog_tree
        previous_selection = tree.selection()
        expanded = {iid for iid in self._tree_descendants("") if tree.item(iid, "open")}
        tree.delete(*tree.get_children())
        query = self.catalog_query.get().strip().casefold()
        coordinate = re.fullmatch(r"\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([0-3]))?\s*", query)
        included = set()
        for key, item in self.catalog.entries.items():
            source = item.get("source", {})
            haystack = " ".join((item.get("name", ""), key, item.get("kind", ""),
                                  item.get("activation", {}).get("label", ""),
                                  item.get("activation", {}).get("status", ""), str(source.get("path", "")))).casefold()
            match = not query or query in haystack
            if coordinate:
                match = self.catalog.contains(item, int(coordinate[1]), int(coordinate[2]),
                                              int(coordinate[3]) if coordinate[3] else self.plane.get())
            if match:
                included.add(key)
                if query and not coordinate and item.get("kind") == "zone":
                    included.update(self.catalog.descendants(key))
        for key in list(included):
            parent = self.catalog.entries[key].get("parentId")
            seen = {key}
            while parent in self.catalog.entries and parent not in seen:
                seen.add(parent)
                included.add(parent)
                parent = self.catalog.entries[parent].get("parentId")
        groups = (("zone", "Travel zones"), ("controller", "Gameplay controllers"),
                  ("dynamic", "Conditional / unresolved parents"))
        for key, label in groups:
            tree.insert("", "end", iid="@group/" + key, text=label, values=("", ""), open=True)
        inserted = set()
        pending = sorted(included, key=lambda key: self.catalog.entries[key].get("name", key))
        while pending:
            progress = False
            for key in list(pending):
                item = self.catalog.entries[key]
                parent = item.get("parentId")
                if parent in included and parent not in inserted and parent != key:
                    continue
                group = "controller" if item.get("kind") == "controller" else ("dynamic" if item.get("kind") == "subzone" and parent not in self.catalog.entries else "zone")
                parent_iid = "@entry/" + parent if parent in inserted else "@group/" + group
                iid = "@entry/" + key
                activation = item.get("activation", {})
                tree.insert(parent_iid, "end", iid=iid, text=item.get("name", key),
                            values=("✓" if self.catalog.visible.get(key) else "—", activation.get("label", activation.get("status", "Unknown"))),
                            open=bool(query) or iid in expanded)
                inserted.add(key)
                pending.remove(key)
                progress = True
            if not progress:
                # Cyclic or unresolved source relationships stay visible as unresolved roots.
                for key in pending:
                    item = self.catalog.entries[key]
                    tree.insert("@group/dynamic", "end", iid="@entry/" + key, text=item.get("name", key),
                                values=("✓" if self.catalog.visible.get(key) else "—", "Unresolved parent"))
                break
        for key, _ in groups:
            iid = "@group/" + key
            if not tree.get_children(iid):
                tree.delete(iid)
        preferred = previous_selection[0] if previous_selection else ("@entry/" + self.selected_catalog if self.selected_catalog else None)
        if preferred and tree.exists(preferred):
            tree.selection_set(preferred)
        elif self.selected_catalog and tree.exists("@entry/" + self.selected_catalog):
            tree.selection_set("@entry/" + self.selected_catalog)

    def _tree_descendants(self, iid):
        result = []
        for child in self.catalog_tree.get_children(iid):
            result.append(child)
            result.extend(self._tree_descendants(child))
        return result

    def catalog_tree_click(self, event):
        if self.catalog_tree.identify_column(event.x) != "#1":
            return
        iid = self.catalog_tree.identify_row(event.y)
        if iid:
            self.catalog_tree.selection_set(iid)
            self.toggle_catalog_selected()
            return "break"

    def toggle_catalog_selected(self):
        selection = self.catalog_tree.selection()
        if not selection:
            return "break"
        iid = selection[0]
        if iid.startswith("@entry/"):
            key = iid[7:]
            self.selected_catalog = key
            self.catalog.visible[key] = not self.catalog.visible.get(key, False)
        else:
            keys = self.catalog_group_keys(iid)
            value = not all(self.catalog.visible.get(key, False) for key in keys)
            for key in keys:
                self.catalog.visible[key] = value
        self.catalog_visibility_changed()
        return "break"

    def set_all_catalog(self, value):
        for key in self.catalog.entries:
            self.catalog.visible[key] = bool(value)
        self.catalog_visibility_changed()

    def set_catalog_group(self, value):
        selection = self.catalog_tree.selection()
        if not selection:
            self.error("Select a zone or group first.")
            return
        iid = selection[0]
        if iid.startswith("@entry/"):
            keys = self.catalog.descendants(iid[7:])
        else:
            keys = self.catalog_group_keys(iid)
        for key in keys:
            self.catalog.visible[key] = bool(value)
        self.catalog_visibility_changed()

    def catalog_group_keys(self, iid):
        wanted = iid.removeprefix("@group/")
        result = []
        for key, entry in self.catalog.entries.items():
            root = entry
            seen = {key}
            while root.get("parentId") in self.catalog.entries and root["parentId"] not in seen:
                seen.add(root["parentId"])
                root = self.catalog.entries[root["parentId"]]
            group = ("controller" if root.get("kind") == "controller" else
                     "dynamic" if root.get("kind") == "subzone" else "zone")
            if group == wanted:
                result.append(key)
        return result

    def catalog_visibility_changed(self):
        self.invalidate_catalog_fill()
        self.catalog_hover_tile = None
        self.refresh_catalog_tree()
        self.schedule_draw()
        self.queue_preferences()

    def select_catalog_tree(self, event=None):
        selection = self.catalog_tree.selection()
        if selection and selection[0].startswith("@entry/"):
            self.select_catalog(selection[0][7:], from_map=False)
        elif selection:
            self.selected_catalog = None
            count = len(self.catalog_group_keys(selection[0]))
            self.set_text(self.catalog_details, f"{self.catalog_tree.item(selection[0], 'text')}\n{count} definitions in this group.\n\nUse Group on / Group off to change Atlas visibility. This does not change Luna registration or activation.")
            self.schedule_draw()

    def select_catalog(self, key, from_map=False):
        entry = self.catalog.entries.get(key)
        if entry is None:
            return
        self.selected_catalog = key
        if from_map:
            self.catalog_query.set("")
            self.refresh_catalog_tree()
            iid = "@entry/" + key
            if self.catalog_tree.exists(iid):
                parent = self.catalog_tree.parent(iid)
                while parent:
                    self.catalog_tree.item(parent, open=True)
                    parent = self.catalog_tree.parent(parent)
                self.catalog_tree.selection_set(iid)
                self.catalog_tree.see(iid)
            if not self.sidebar_visible:
                self.toggle_sidebar()
            self.notebook.select(self.catalog_tab)
        self.set_text(self.catalog_details, self.catalog_detail_text(entry))
        self.schedule_draw()

    def catalog_detail_text(self, entry):
        activation = entry.get("activation", {})
        source = entry.get("source", {})
        planes = entry.get("planes")
        parent = self.catalog.entries.get(entry.get("parentId"), {})
        lines = [entry.get("name", entry["id"]), entry["id"],
                 f"Kind: {entry.get('kind', 'zone')}",
                 "Status: " + activation.get("label", activation.get("status", "Unknown")),
                 activation.get("detail", ""),
                 "Runtime: " + activation["runtime"] if activation.get("runtime") else "",
                 "Planes: " + ("all" if planes is None else ", ".join(map(str, planes))),
                 "Parent: " + parent.get("name", str(entry.get("parentId") or "none"))]
        if entry.get("parentOptions"):
            lines.append("Parent options: " + ", ".join(str(v) for v in entry["parentOptions"]))
        if entry.get("parentRelationship"):
            lines.append("Relationship: " + str(entry["parentRelationship"]))
        if entry.get("bounds"):
            lines.append("Extent: " + json.dumps(entry["bounds"]))
        lines.append(f"Geometry: {len(entry.get('parts', []))} actual part(s)")
        if entry.get("definedBounds"):
            lines.append("Source bounds: " + json.dumps(entry["definedBounds"]))
        if entry.get("registrationCoverage"):
            lines.append("Registration coverage: " + json.dumps(entry["registrationCoverage"]))
        if entry.get("inside"):
            lines.append("Inside: " + json.dumps(entry["inside"]))
        lines.append("Source: " + str(source.get("path", "unknown")) + (":" + str(source["line"]) if source.get("line") else ""))
        diagnostics = entry.get("diagnostics", [])
        if diagnostics:
            lines.append("Diagnostics:")
            lines.extend("• " + self.diagnostic_text(d) for d in diagnostics)
        if activation.get("evidence"):
            lines.append("Registration / activation evidence:")
            lines.extend("• " + self.diagnostic_text(d) for d in activation["evidence"])
        return "\n".join(line for line in lines if line)

    @staticmethod
    def diagnostic_text(value):
        if not isinstance(value, dict):
            return str(value)
        if not value.get("message") and not value.get("detail") and value.get("path"):
            return str(value["path"]) + (":" + str(value["line"]) if value.get("line") else "")
        return str(value.get("message") or value.get("detail") or json.dumps(value, ensure_ascii=False))

    def catalog_details_dialog(self):
        entry = self.catalog.entries.get(self.selected_catalog)
        if entry is None:
            self.error("Select a source zone first.")
            return
        dialog = tk.Toplevel(self.root)
        dialog.title("Zone details · " + entry.get("name", entry["id"]))
        dialog.geometry("760x560")
        frame = ttk.Frame(dialog, padding=16)
        frame.pack(fill="both", expand=True)
        text = self._text(frame)
        self.set_text(text, self.catalog_detail_text(entry))
        ttk.Button(frame, text="Close", command=dialog.destroy).pack(anchor="e", pady=(10, 0))

    def focus_catalog(self):
        entry = self.catalog.entries.get(self.selected_catalog)
        if entry is None:
            self.error("Select a source zone to focus.")
            return
        planes = entry.get("planes")
        if planes is not None and self.plane.get() not in planes and planes:
            self.plane.set(planes[0])
            self.change_plane()
        parts = self.catalog.parts(entry, self.plane.get())
        if not parts:
            self.error("This definition has no resolved boundary on the selected plane. See its source diagnostics.")
            return
        b = catalog_geometry.bounds_for_plane(entry, self.plane.get())
        self.center_x = (b["minX"] + b["maxX"] + 1) / 2
        self.center_y = (b["minY"] + b["maxY"] + 1) / 2
        fit = min(max(1, self.canvas.winfo_width() - 90) / (b["maxX"] - b["minX"] + 1),
                  max(1, self.canvas.winfo_height() - 90) / (b["maxY"] - b["minY"] + 1))
        self.scale = max(.25, min(16, 2 ** math.floor(math.log2(max(.25, fit)))))
        self.catalog.visible[entry["id"]] = True
        self.show_existing.set(True)
        if entry.get('kind') == 'subzone':
            self.subzone_boundaries.set(True)
            self.subzone_labels.set(True)
        elif entry.get('kind') == 'zone':
            self.zone_boundaries.set(True)
            self.overlay_labels.set(True)
        self.invalidate_catalog_fill()
        self.refresh_catalog_tree()
        self.schedule_draw()
        self.queue_preferences()
        self.status.set("Focused " + entry.get("name", entry["id"]) + ". Its multipart boundary and plane restrictions are preserved.")

    def select_catalog_at(self, x, y):
        hits = self.catalog.hits(x, y, self.plane.get()) if self.show_existing.get() else []
        if not hits:
            self.inspect(x, y)
            self.notebook.select(self.inspect_tab)
            return
        if len(hits) == 1:
            self.select_catalog(hits[0]["id"], from_map=True)
            return
        if self.catalog_dialog and self.catalog_dialog.winfo_exists():
            self.catalog_dialog.destroy()
        dialog = self.catalog_dialog = tk.Toplevel(self.root)
        dialog.title(f"Zones at {x}, {y}, {self.plane.get()}")
        dialog.geometry("590x360")
        frame = ttk.Frame(dialog, padding=14)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text="Overlapping areas · choose the boundary to inspect", style="Title.TLabel").pack(anchor="w", pady=(0, 10))
        choices = tk.Listbox(frame, bg=FIELD, fg=TEXT, selectbackground="#42606a", font=("Segoe UI", 10), activestyle="none")
        choices.pack(fill="both", expand=True)
        for item in hits:
            choices.insert("end", f"{item.get('name', item['id'])}  ·  {item.get('kind')}  ·  {item.get('activation', {}).get('label', 'Unknown')}")
        choices.selection_set(0)
        def choose(event=None):
            selected = choices.curselection()
            if selected:
                self.select_catalog(hits[selected[0]]["id"], from_map=True)
                dialog.destroy()
        choices.bind("<Double-1>", choose)
        choices.bind("<Return>", choose)
        ttk.Button(frame, text="Inspect selected", command=choose).pack(anchor="e", pady=(10, 0))

    def copy_position(self):
        position = self.inspected or (math.floor(self.center_x), math.floor(self.center_y), self.plane.get())
        self.root.clipboard_clear()
        self.root.clipboard_append("Position(%d, %d, %d)" % position)
        self.status.set("Copied Position(%d, %d, %d)." % position)

    def open_luna_directory(self):
        project = self.summary.get("project") or {}
        directory = filedialog.askdirectory(parent=self.root, title="Open Luna project directory", initialdir=project.get("root") or str(Path.home()))
        if directory:
            self.request_project_open(directory)

    def request_project_open(self, directory, overrides=None):
        if self.project_opening:
            return
        unsaved = [zone for zone in self.zones if not zone.get("_saved")]
        if unsaved:
            save = messagebox.askyesnocancel("Unsaved area drafts", f"This window contains {len(unsaved)} unsaved area draft(s).\n\nSave them before opening another Luna directory?\nYes saves first. No opens without these unsaved drafts. Cancel keeps this project open.", parent=self.root)
            if save is None:
                return
            if save:
                self.pending_project_request = directory, overrides
                self.save_zones(unsaved)
                return
        self._open_project(directory, overrides)

    def _open_project(self, directory, overrides=None):
        self.project_opening = True
        for key in ('search', 'entity', 'npcs', 'catalog'):
            self.generations[key] += 1
        self.cancel_preference_timer()
        self.status.set("Opening Luna directory and reading its cache and source catalog…")
        preferences = self.preferences_payload() if self.preferences_ready else None
        payload = {"directory": str(directory), "overrides": overrides or {}}
        def work():
            if preferences is not None:
                try:
                    self.client.request("/api/preferences", preferences)
                except Exception:
                    pass
            return self.client.request("/api/project/open", payload)
        def loaded(result):
            self.project_opening = False
            if not result.get("loaded"):
                self.status.set("The selected directory needs resource paths. The current project remains open.")
                self.resolve_project_paths(directory, result.get("project", result), overrides or {})
                return
            self.loaded_summary(result.get("summary") or {})
            self.status.set("Luna directory opened. Explore its registered zones and source diagnostics.")
        def failed(error):
            self.project_opening = False
            self.error(error)
        self.client.submit(work, loaded, failed)

    def reload_project(self):
        if self.project_opening:
            return
        self.project_opening = True
        for key in ('search', 'entity', 'npcs', 'catalog'):
            self.generations[key] += 1
        self.status.set("Reloading selected Luna resources and zone source…")
        def loaded(result):
            self.project_opening = False
            if result.get("loaded"):
                self.loaded_summary(result.get("summary") or {})
                self.status.set("Luna resources reloaded. Atlas visibility controls leave gameplay activation unchanged.")
            else:
                project = result.get("project") or {}
                self.resolve_project_paths(project.get("root") or (self.summary.get("project") or {}).get("root", ""), project, {})
        def failed(error):
            self.project_opening = False
            self.error(error)
        self.client.submit(lambda: self.client.request("/api/project/reload", {}), loaded, failed)

    def resolve_project_paths(self, directory, report, overrides):
        dialog = tk.Toplevel(self.root)
        dialog.title("Resolve Luna project resources")
        dialog.geometry("800x650")
        frame = ttk.Frame(dialog, padding=16)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text="Select the missing or ambiguous project resources", style="Title.TLabel").pack(anchor="w")
        ttk.Label(frame, text=str(directory), style="Muted.TLabel", wraplength=750).pack(anchor="w", pady=(6, 8))
        text = self._text(frame, height=9)
        self.set_text(text, self.project_report_text(report))
        fields = ttk.Frame(frame)
        fields.pack(fill="x", pady=(12, 8))
        resources = report.get("resources") or {}
        variables = {}
        for index, (key, label, folder) in enumerate((("cache", "Cache folder", True), ("zoneSource", "Zone source", False),
                                                     ("subzoneSource", "Subzone source", False), ("controllers", "Controllers source", False), ("config", "Luna config", False), ("npcSpawns", "NPC spawns JSON", False))):
            value = overrides.get(key) or resources.get(key) or ""
            if not isinstance(value, str):
                value = value.get("path", "") if isinstance(value, dict) else ""
            variable = variables[key] = tk.StringVar(value=value)
            ttk.Label(fields, text=label).grid(row=index, column=0, sticky="w", padx=(0, 10), pady=3)
            ttk.Entry(fields, textvariable=variable).grid(row=index, column=1, sticky="ew", pady=3)
            def browse(var=variable, is_folder=folder):
                selected = (filedialog.askdirectory(parent=dialog, title="Select project resource") if is_folder
                            else filedialog.askopenfilename(parent=dialog, title="Select project source or configuration"))
                if selected:
                    var.set(selected)
            ttk.Button(fields, text="Browse…", command=browse).grid(row=index, column=2, padx=(7, 0), pady=3)
        fields.columnconfigure(1, weight=1)
        actions = ttk.Frame(frame)
        actions.pack(fill="x")
        def retry():
            chosen = {key: var.get().strip() for key, var in variables.items() if var.get().strip()}
            dialog.destroy()
            self._open_project(directory, chosen)
        ttk.Button(actions, text="Keep current project", command=dialog.destroy).pack(side="right")
        ttk.Button(actions, text="Load selected resources", command=retry, style="Accent.TButton").pack(side="right", padx=(0, 8))

    def project_report_text(self, project=None):
        project = project or self.summary.get("project") or {}
        lines = ["Luna directory: " + str(project.get("root", "not selected")),
                 "Usable: " + str(project.get("usable", "unknown")), "", "Discovered resources:"]
        resources = project.get("relativePaths") or project.get("resources") or {}
        lines.extend(f"• {key}: {value}" for key, value in resources.items())
        choices = project.get("choices") or project.get("candidates")
        if choices:
            lines.extend(["", "Resource choices:", json.dumps(choices, indent=2, ensure_ascii=False)])
        diagnostics = list(project.get("diagnostics") or [])
        if self.catalog_source:
            diagnostics.extend(self.catalog_source.get("diagnostics") or [])
        lines.extend(["", "Diagnostics:"])
        lines.extend("• " + self.diagnostic_text(value) for value in diagnostics)
        if not diagnostics:
            lines.append("No project loading diagnostics.")
        if self.summary.get("imagery"):
            lines.extend(["", "Map imagery:", json.dumps(self.summary["imagery"], indent=2, ensure_ascii=False)])
        lines.extend(["", "Source discovery identifies registrations and unresolved runtime conditions. It does not observe a running game's occupants, quest state or per-bot travel conditions."])
        return "\n".join(lines)

    def project_report(self):
        dialog = tk.Toplevel(self.root)
        dialog.title("Luna project · Loading and source report")
        dialog.geometry("820x590")
        frame = ttk.Frame(dialog, padding=16)
        frame.pack(fill="both", expand=True)
        text = self._text(frame)
        self.set_text(text, self.project_report_text())
        ttk.Button(frame, text="Close", command=dialog.destroy).pack(anchor="e", pady=(10, 0))

    def load_preferences(self):
        self.preferences_ready = False
        identity = self.project_identity
        def loaded(data):
            if identity != self.project_identity or data.get("projectIdentity", identity) != identity:
                return
            preferences = data.get("preferences") or {}
            self.last_preferences = preferences
            self.apply_preferences(preferences)
            self.preferences_ready = True
        self.guarded_request("preferences", lambda: self.client.request("/api/preferences"), loaded)

    def apply_preferences(self, preferences):
        center = preferences.get("center")
        if isinstance(center, list) and len(center) == 2 and all(isinstance(v, (int, float)) and math.isfinite(v) for v in center):
            self.center_x, self.center_y = (max(0, min(16384, float(v))) for v in center)
        scale = preferences.get("scale")
        if isinstance(scale, (int, float)) and math.isfinite(scale):
            self.scale = max(.25, min(16, float(scale)))
        plane = preferences.get("plane")
        if type(plane) is int and 0 <= plane <= 3:
            self.plane.set(plane)
        for key, variable in (("showOverlays", self.show_existing),):
            if isinstance(preferences.get(key), bool):
                variable.set(preferences[key])
        self.apply_grid_preferences(preferences.get("grids"), legacyGrid=preferences.get("grid"))
        layers = preferences.get("layers") or {}
        if not isinstance(layers, dict):
            layers = {}
        for key, variable in (("zoneBoundaries", self.zone_boundaries), ("subzoneBoundaries", self.subzone_boundaries), ("controllerBoundaries", self.controller_boundaries), ("zoneLabels", self.overlay_labels), ("subzoneLabels", self.subzone_labels), ("npcMarkers", self.npc_markers)):
            variable.set(layers.get(key, True) if isinstance(layers.get(key, True), bool) else True)
        styles = preferences.get("labelStyles") or {}
        if not isinstance(styles, dict):
            styles = {}
        self.label_styles = {key: map_labels.normalise_style(styles.get(key), key) for key in map_labels.DEFAULT_STYLES}
        self.label_fonts.clear()
        self.label_metrics.clear()
        opacity = preferences.get("opacity")
        if isinstance(opacity, (int, float)) and math.isfinite(opacity):
            self.overlay_opacity.set(max(0, min(65, float(opacity))))
        visibility = preferences.get("zoneVisibility", {})
        if isinstance(visibility, dict):
            self.catalog_restore_visibility = visibility
            for key, value in visibility.items():
                if key in self.catalog.entries and isinstance(value, bool):
                    self.catalog.visible[key] = value
            if self.catalog.entries:
                self.catalog_restore_visibility = None
        sidebar = preferences.get("sidebarVisible")
        if isinstance(sidebar, bool) and sidebar != self.sidebar_visible:
            self.toggle_sidebar(remember=False)
        self.invalidate_catalog_fill()
        self.refresh_catalog_tree()
        self.schedule_draw()

    def preferences_payload(self):
        return {"projectIdentity": self.project_identity, "preferences": {
            "center": [self.center_x, self.center_y], "scale": self.scale, "plane": self.plane.get(),
            "grid": self.grid.get(), "showOverlays": self.show_existing.get(), "labels": self.overlay_labels.get(),
            "opacity": round(self.overlay_opacity.get(), 2), "sidebarVisible": self.sidebar_visible,
            "zoneVisibility": dict(self.catalog.visible), "grids": self.grid_preferences(),
            "layers": self.layer_preferences(), "labelStyles": self.label_styles}}

    def cancel_preference_timer(self):
        if self.preference_after is not None:
            self.root.after_cancel(self.preference_after)
            self.preference_after = None

    def queue_preferences(self):
        if not self.preferences_ready or self.project_opening or not self.project_identity:
            return
        self.last_preferences = self.preferences_payload()["preferences"]
        self.cancel_preference_timer()
        self.preference_after = self.root.after(550, self.save_preferences)

    def save_preferences(self):
        self.preference_after = None
        if self.preferences_ready and not self.project_opening:
            payload = self.preferences_payload()
            self.client.submit(lambda: self.client.request("/api/preferences", payload), lambda _: None,
                               lambda error: self.error("View preferences could not be saved: " + str(error)))

    def _inspect_panel(self):
        ttk.Label(self.inspect_tab, text="Tile inspector", style="Title.TLabel").pack(anchor="w")
        ttk.Label(self.inspect_tab, text="Click a tile to read cache objects and collision.\nDrag to move; scroll to zoom.", style="Muted.TLabel", justify="left").pack(anchor="w", pady=(6, 14))
        jump = ttk.Frame(self.inspect_tab)
        jump.pack(fill="x", pady=(0, 12))
        self.jump_x, self.jump_y = tk.StringVar(value="3222"), tk.StringVar(value="3218")
        ttk.Label(jump, text="X").pack(side="left")
        ttk.Entry(jump, textvariable=self.jump_x, width=8).pack(side="left", padx=(5, 9))
        ttk.Label(jump, text="Y").pack(side="left")
        ttk.Entry(jump, textvariable=self.jump_y, width=8).pack(side="left", padx=(5, 9))
        ttk.Button(jump, text="Go", command=self.jump).pack(side="left")
        ttk.Button(self.inspect_tab, text="Copy Position(x, y, plane)", command=self.copy_position).pack(fill="x", pady=(0, 8))
        self.inspect_text = self._text(self.inspect_tab, height=20)
        self.set_text(self.inspect_text, "Choose a tile to inspect.\n\nThe map is rendered from your local cache. Unknown or missing map data must be reviewed before using coordinates in game.")

    def _search_panel(self):
        ttk.Label(self.search_tab, text="Find places and entities", style="Title.TLabel").pack(anchor="w")
        ttk.Label(self.search_tab, text="Names and IDs · every placement has its own location", style="Muted.TLabel", wraplength=330).pack(anchor="w", pady=(6, 9))
        row = ttk.Frame(self.search_tab)
        row.pack(fill="x")
        self.search_kind = tk.StringVar(value="Zones")
        mode = ttk.Combobox(row, textvariable=self.search_kind, values=("Zones", "NPCs", "Objects"), state="readonly", width=9)
        mode.pack(side="left", padx=(0, 6))
        mode.bind("<<ComboboxSelected>>", self.search_kind_changed)
        self.search_name = tk.StringVar(value="")
        entry = ttk.Entry(row, textvariable=self.search_name)
        entry.pack(side="left", fill="x", expand=True)
        entry.bind("<Return>", lambda event: self.search())
        ttk.Button(row, text="Find", command=self.search).pack(side="left", padx=(6, 0))
        self.search_view_only = tk.BooleanVar(value=False)
        self.search_all_planes = tk.BooleanVar(value=True)
        self.search_unplaced = tk.BooleanVar(value=True)
        ttk.Checkbutton(self.search_tab, text="Visible map only", variable=self.search_view_only).pack(anchor="w", pady=(8, 0))
        ttk.Checkbutton(self.search_tab, text="All planes · switch on selection", variable=self.search_all_planes).pack(anchor="w")
        ttk.Checkbutton(self.search_tab, text="Include definitions without placements", variable=self.search_unplaced).pack(anchor="w", pady=(0, 8))
        split = ttk.Panedwindow(self.search_tab, orient='vertical')
        split.pack(fill='both', expand=True)
        result_frame, detail_frame = ttk.Frame(split), ttk.Frame(split)
        split.add(result_frame, weight=3)
        split.add(detail_frame, weight=2)
        self.results = self._tree(result_frame, ("name", "location"), ("Name — Zone → Subzone", "X, Y, plane"), (330, 125), height=6)
        horizontal = ttk.Scrollbar(result_frame, orient='horizontal', command=self.results.xview)
        self.results.configure(xscrollcommand=horizontal.set)
        horizontal.pack(fill='x')
        self.results.bind("<<TreeviewSelect>>", self.select_search)
        self.results.bind("<Return>", self.select_search)
        ttk.Label(detail_frame, text='Selected result', style='Muted.TLabel').pack(anchor='w', pady=(6, 4))
        self.search_details = self._text(detail_frame, height=7)
        self.set_text(self.search_details, "Find a zone, NPC or object. Selecting a placed result navigates to that exact instance. A hidden zone is explicitly revealed; definitions without a known placement cannot be navigated to.")

    def search_kind_changed(self, event=None):
        self.generations['search'] += 1
        self.generations['entity'] += 1
        self.selected_entity = None
        self.search_points = []
        self.results.delete(*self.results.get_children())
        self.set_text(self.search_details, 'Find a ' + self.search_kind.get().lower().rstrip('s') + ' by name or identifier to inspect its current source location.')
        self.schedule_draw()

    def _zone_panel(self):
        ttk.Label(self.zones_tab, text="Build subzones", style="Title.TLabel").pack(anchor="w")
        ttk.Label(self.zones_tab, text="Draw two corners, or propose clusters around\nmatching objects. Review and save local drafts.", style="Muted.TLabel", justify="left").pack(anchor="w", pady=(6, 12))
        row = ttk.Frame(self.zones_tab)
        row.pack(fill="x")
        self.propose_name = tk.StringVar(value="tree")
        ttk.Entry(row, textvariable=self.propose_name).pack(side="left", fill="x", expand=True)
        ttk.Button(row, text="Propose", style="Accent.TButton", command=self.propose).pack(side="left", padx=(7, 0))
        bounds = ttk.Frame(self.zones_tab)
        bounds.pack(fill="x", pady=10)
        self.bound_vars = {}
        for i, (key, label) in enumerate((("minX", "West X"), ("minY", "South Y"), ("maxX", "East X"), ("maxY", "North Y"))):
            col, line = i % 2, (i // 2) * 2
            ttk.Label(bounds, text=label, style="Muted.TLabel").grid(row=line, column=col, sticky="w", padx=(0, 10), pady=(4, 1))
            var = tk.StringVar()
            self.bound_vars[key] = var
            ttk.Entry(bounds, textvariable=var, width=15).grid(row=line + 1, column=col, sticky="ew", padx=(0, 10))
            bounds.columnconfigure(col, weight=1)
        ttk.Button(self.zones_tab, text="Use visible map bounds", command=self.use_view_bounds).pack(fill="x")
        self.zone_tree = self._tree(self.zones_tab, ("name", "state"), ("Name", "State"), (240, 95), height=7)
        self.zone_tree.bind("<<TreeviewSelect>>", self.select_zone)
        rename = ttk.Frame(self.zones_tab)
        rename.pack(fill="x", pady=(10, 7))
        self.zone_name = tk.StringVar()
        ttk.Entry(rename, textvariable=self.zone_name).pack(side="left", fill="x", expand=True)
        ttk.Button(rename, text="Rename", command=self.rename_zone).pack(side="left", padx=(7, 0))
        actions = ttk.Frame(self.zones_tab)
        actions.pack(fill="x")
        ttk.Button(actions, text="Save selected", command=self.save_selected).pack(side="left", fill="x", expand=True)
        ttk.Button(actions, text="Save all drafts", command=self.save_all).pack(side="left", fill="x", expand=True, padx=(7, 0))
        ttk.Button(self.zones_tab, text="Validation details…", command=self.zone_details).pack(fill="x", pady=(8, 0))
        self.zone_info = tk.StringVar(value="No area selected.")
        ttk.Label(self.zones_tab, textvariable=self.zone_info, style="Muted.TLabel", wraplength=350, justify="left").pack(anchor="w", pady=(12, 0))

    def _code_panel(self):
        ttk.Label(self.code_tab, text="Luna code export", style="Title.TLabel").pack(anchor="w")
        ttk.Label(self.code_tab, text="Select a subzone to preview its server code.\nRectangles are checked before saving a draft.", style="Muted.TLabel", justify="left").pack(anchor="w", pady=(6, 12))
        self.code_text = self._text(self.code_tab, height=20, fixed=True)
        self.set_text(self.code_text, "Select a subzone to preview its export.")
        row = ttk.Frame(self.code_tab)
        row.pack(fill="x", pady=(12, 0))
        ttk.Button(row, text="Copy code", command=self.copy_code).pack(side="left", fill="x", expand=True)
        ttk.Button(row, text="Export file…", command=self.export_code).pack(side="left", fill="x", expand=True, padx=(7, 0))

    def _text(self, parent, height=15, fixed=False):
        frame = ttk.Frame(parent)
        frame.pack(fill="both", expand=True)
        widget = tk.Text(frame, bg=FIELD, fg=TEXT, insertbackground=TEXT, relief="flat", padx=12, pady=12,
                         wrap="word", height=height, font=("Consolas" if fixed else "Segoe UI", 10), borderwidth=0)
        scroll = ttk.Scrollbar(frame, orient="vertical", command=widget.yview)
        widget.configure(yscrollcommand=scroll.set)
        widget.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        return widget

    def _tree(self, parent, columns, labels, widths, height=12):
        frame = ttk.Frame(parent)
        frame.pack(fill="both", expand=True, pady=(10, 0))
        tree = ttk.Treeview(frame, columns=columns, show="headings", selectmode="browse", height=height)
        for column, label, width in zip(columns, labels, widths):
            tree.heading(column, text=label)
            tree.column(column, width=width, minwidth=50, stretch=True)
        scroll = ttk.Scrollbar(frame, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=scroll.set)
        tree.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        return tree

    @staticmethod
    def set_text(widget, text):
        widget.configure(state="normal")
        widget.delete("1.0", "end")
        widget.insert("1.0", text)
        widget.configure(state="disabled")

    def _poll(self):
        if not self.alive:
            return
        for _ in range(80):
            try:
                callback, value = self.client.events.get_nowait()
            except queue.Empty:
                break
            if callback:
                try:
                    callback(value)
                except Exception as error:
                    self.error(str(error))
            else:
                self.error(value)
        self.root.after(40, self._poll)

    def error(self, text):
        self.status.set("Unable to complete request: " + str(text))

    def load_summary(self):
        self.status.set("Reading local cache information…")
        self.guarded_request("summary", lambda: self.client.request("/api/summary"), self.loaded_summary)

    def guarded_request(self, kind, work, callback):
        self.generations[kind] += 1
        generation = self.generations[kind]

        def current(result):
            if self.generations[kind] == generation:
                callback(result)

        def failed(error):
            if self.generations[kind] == generation:
                self.error(error)

        self.client.submit(work, current, failed)

    def loaded_summary(self, summary):
        initial = not self.summary
        project = summary.get("project") or {}
        identity = summary.get("projectIdentity") or project.get("identity") or summary.get("cache")
        switched = self.project_identity is not None and identity != self.project_identity
        self.summary = summary
        dataset = summary.get('datasetIdentity')
        if self.dataset_identity is not None and dataset != self.dataset_identity:
            for key in ('search', 'entity', 'npcs', 'catalog'):
                self.generations[key] += 1
            self.search_points = []
            self.results.delete(*self.results.get_children())
            self.selected_entity = None
            self.npcs = []
            self.npc_buckets = {}
            self.set_text(self.search_details, 'Project source data changed. Search again to inspect current placements and zone context.')
        self.dataset_identity = dataset
        self.project_identity = identity
        self.regions = {(int(r["rx"]), int(r["ry"])) for r in summary.get("regions", [])}
        self.project_label.configure(text=project.get("name") or "Revision 377 · Map and zone workspace")
        if initial or switched:
            center = summary.get("center", summary.get("initialCenter", {}))
            self.center_x = float(center.get("x", 3222)) + .5
            self.center_y = float(center.get("y", 3218)) + .5
            self.plane.set(int(center.get("plane", 0)))
        if switched:
            self.cancel_preference_timer()
            self.preferences_ready = False
            self.last_preferences = {}
            self.catalog_restore_visibility = None
            self.catalog = CatalogModel()
            self.invalidate_catalog_fill()
            self.catalog_source = None
            self.selected_catalog = None
            self.zones = []
            self.selected_zone = None
            self.inspected = None
            self.search_points = []
            self.npcs = []
            self.npc_buckets = {}
            self.npc_source = None
            self.selected_entity = None
            self.undo_stack, self.redo_stack = [], []
            self.cancel_rectangle()
            self.refresh_zones()
            self.results.delete(*self.results.get_children())
            for key in self.generations:
                self.generations[key] += 1
        fingerprint = summary.get("fingerprint", summary.get("cacheFingerprint", summary.get("fingerprints", "")))
        if isinstance(fingerprint, dict):
            fingerprint = fingerprint.get("main_file_cache.dat", fingerprint.get("sha256", fingerprint.get("value", "local")))
        counts = summary.get("counts", {})
        objects = counts.get("objects", summary.get("objects", "")) if isinstance(counts, dict) else ""
        objects = f"{objects:,}" if isinstance(objects, int) else str(objects)
        self.header_detail.set(f"{len(self.regions):,} regions · {objects or 'Local'} objects · 377 cache {str(fingerprint)[:12]}")
        cache_identity = json.dumps([identity, summary.get("fingerprints", fingerprint), summary.get("imagery", {})], sort_keys=True)
        if cache_identity != self.cache_identity:
            self.cache_identity = cache_identity
            self.cache_generation += 1
            self.pending_tiles.clear()
            self.failed_tiles.clear()
            self.tiles.clear()
            self.tile_bytes = 0
            self.photo_tiles.clear()
            self.photo_bytes = 0
            for record in self.canvas_tiles.values():
                self.canvas.delete(record["item"])
            self.canvas_tiles.clear()
        self.failed_tiles.clear()
        self.status.set("Ready. Choose a source zone, inspect a tile, or build a separate area draft.")
        self.use_view_bounds()
        self.schedule_draw()
        generation = self.cache_generation
        self.client.submit(lambda: self.client.request("/api/drafts"),
                           lambda data: self.loaded_drafts(data) if generation == self.cache_generation else None)
        self.load_catalog()
        self.load_npcs()
        if initial or switched:
            self.load_preferences()

    def loaded_drafts(self, data):
        known = {str(zone.get("id")) for zone in self.zones}
        for item in data.get("drafts", []):
            if str(item.get("id")) not in known:
                item["_saved"] = True
                self.zones.append(item)
        self.refresh_zones()

    def world(self, x, y):
        return (self.center_x + (x - self.canvas.winfo_width() / 2) / self.scale,
                self.center_y - (y - self.canvas.winfo_height() / 2) / self.scale)

    def screen(self, x, y):
        return ((x - self.center_x) * self.scale + self.canvas.winfo_width() / 2,
                (self.center_y - y) * self.scale + self.canvas.winfo_height() / 2)

    def view_bounds(self):
        left, top = self.world(0, 0)
        right, bottom = self.world(self.canvas.winfo_width(), self.canvas.winfo_height())
        return {"minX": math.floor(left), "minY": math.floor(bottom), "maxX": math.floor(right), "maxY": math.floor(top)}

    def use_view_bounds(self):
        for key, value in self.view_bounds().items():
            self.bound_vars[key].set(str(value))

    def schedule_draw(self):
        if self.draw_after is None:
            self.draw_after = self.root.after(15, self.draw)

    def draw(self):
        self.draw_after = None
        started = time.perf_counter()
        c = self.canvas
        c.delete("overlay")
        c.delete("grid")
        c.delete("preview")
        c.delete("empty")
        bounds = self.view_bounds()
        plane = self.plane.get()
        if not self.summary:
            c.create_text(c.winfo_width() / 2, c.winfo_height() / 2, text="Loading your local world…", fill=MUTED, font=("Segoe UI", 16), tags="empty")
            return
        visible = [(rx, ry) for rx, ry in self.regions
                   if bounds["minX"] // 64 <= rx <= bounds["maxX"] // 64
                   and bounds["minY"] // 64 <= ry <= bounds["maxY"] // 64]
        visible.sort(key=lambda r: ((r[0] * 64 + 32 - self.center_x) ** 2
                                    + (r[1] * 64 + 32 - self.center_y) ** 2))
        self.desired_tiles = [(plane, rx, ry) for rx, ry in visible]
        desired = set(self.desired_tiles)
        for key in list(self.canvas_tiles):
            if key not in desired:
                c.delete(self.canvas_tiles.pop(key)["item"])
        size = max(1, round(64 * self.scale))
        creates = 0
        photos_before_frame = self.metrics["photoCreates"]
        remaining_photos = False
        for key in self.desired_tiles:
            _, rx, ry = key
            px, py = self.screen(rx * 64, (ry + 1) * 64)
            record = self.canvas_tiles.get(key)
            photo = record["photo"] if record and record["photo"] is not None and record.get("size") == size else self._photo_for(key, size, allow_create=creates < 4)
            creates = self.metrics["photoCreates"] - photos_before_frame
            if photo is None and key in self.tiles:
                remaining_photos = True
            kind = "image" if photo is not None else "placeholder"
            if record is None or record["kind"] != kind:
                if record is not None:
                    c.delete(record["item"])
                if photo is not None:
                    item = c.create_image(round(px), round(py), image=photo, anchor="nw", tags="basemap")
                else:
                    item = c.create_rectangle(px, py, px + size, py + size, fill="#15232b", outline="#1b2d35", tags="basemap")
                record = {"item": item, "kind": kind, "photo": photo, "size": size}
                self.canvas_tiles[key] = record
            elif photo is not None:
                c.coords(record["item"], round(px), round(py))
                if record["photo"] is not photo:
                    c.itemconfigure(record["item"], image=photo)
                    record["photo"] = photo
                record["size"] = size
            else:
                c.coords(record["item"], px, py, px + size, py + size)
        c.tag_lower("basemap")
        self._pump_tile_requests(size)
        for item in self.search_points:
            if item.get("x") is not None and item.get("y") is not None and int(item.get("plane", plane) or 0) == plane:
                x, y = self.screen(item["x"] + .5, item["y"] + .5)
                c.create_oval(x - 3, y - 3, x + 3, y + 3, fill=CYAN, outline="#0a2527", tags="overlay")
        self.catalog_visible_photos = []
        self.label_boxes = []
        if self.show_existing.get():
            self.draw_catalog(bounds, plane, size)
        else:
            self.draw_grids()
        self.draw_npcs(bounds, plane)
        self.draw_selected_entity(plane)
        for zone in self.zones:
            b = zone.get("bounds", {})
            if int(b.get("plane", zone.get("plane", 0))) != plane or not self._intersects(b, bounds):
                continue
            x1, y1 = self.screen(b["minX"], b["maxY"] + 1)
            x2, y2 = self.screen(b["maxX"] + 1, b["minY"])
            selected = str(zone.get("id")) == self.selected_zone
            c.create_rectangle(x1, y1, x2, y2, outline="#fff0ad" if selected else GOLD, width=3 if selected else 2,
                               fill="#d5b56d", stipple="gray12", dash=() if zone.get("_saved") else (6, 3), tags="overlay")
            if self.scale >= 2:
                c.create_text(x1 + 5, y1 + 5, text=zone.get("name", "Subzone"), fill="#fff0b1", anchor="nw", font=("Segoe UI", 10, "bold"), tags="overlay")
            inside = zone.get("inside")
            if isinstance(inside, dict) and "x" in inside:
                x, y = self.screen(inside["x"] + .5, inside["y"] + .5)
                c.create_oval(x - 4, y - 4, x + 4, y + 4, fill=GREEN, outline="#132719", width=2, tags="overlay")
        if self.first_corner and self.preview_corner:
            self.draw_rectangle_preview()
        if self.inspected and self.inspected[2] == plane:
            x, y = self.screen(self.inspected[0], self.inspected[1] + 1)
            c.create_rectangle(x, y, x + self.scale, y + self.scale, outline="#ffffff", width=2, tags="overlay")
            cx, cy = x + self.scale / 2, y + self.scale / 2
            c.create_line(cx - 9, cy, cx + 9, cy, fill="#ffffff", tags="overlay")
            c.create_line(cx, cy - 9, cx, cy + 9, fill="#ffffff", tags="overlay")
        self.scale_label.configure(text=f"{self.scale:g} px / tile")
        self.metrics["draws"] += 1
        self.metrics["lastDrawMs"] = (time.perf_counter() - started) * 1000
        if remaining_photos:
            self.schedule_draw()

    def draw_catalog(self, bounds, plane, size):
        c = self.canvas
        visible = []
        for key, entry in self.catalog.entries.items():
            if not self.catalog.visible.get(key):
                continue
            parts = [part for part in self.catalog.parts(entry, plane)
                     if self._intersects(part_bounds(part), bounds)]
            if parts:
                visible.append((entry, parts))
        visible.sort(key=lambda item: (item[0]["id"] == self.selected_catalog, -self.catalog.area(item[0])))
        drawing_parts = [(entry, part) for entry, parts in visible if self.boundary_enabled(entry.get('kind')) for part in parts]
        self.catalog_visible_photos = []
        if self.overlay_opacity.get() > 0 and drawing_parts:
            for _, rx, ry in self.desired_tiles:
                photo = self.catalog_fill(plane, rx, ry, size, drawing_parts)
                if photo is not None:
                    px, py = self.screen(rx * 64, (ry + 1) * 64)
                    c.create_image(round(px), round(py), image=photo, anchor="nw", tags="overlay")
                    self.catalog_visible_photos.append(photo)
        self.draw_grids()
        for entry, parts in visible:
            if not self.boundary_enabled(entry.get('kind')):
                continue
            selected = entry["id"] == self.selected_catalog
            colour = "#fff2c9" if selected else self.catalog.colour(entry)
            status = entry.get("activation", {}).get("status")
            dash = (7, 4) if status == "conditional" else ((2, 4) if status in ("unresolved", "unregistered") else ())
            for a, b in self.catalog.outlines(entry, plane):
                extent = {"minX": min(a[0], b[0]), "minY": min(a[1], b[1]), "maxX": max(a[0], b[0]), "maxY": max(a[1], b[1])}
                if not self._intersects(extent, bounds):
                    continue
                x1, y1 = self.screen(*a)
                x2, y2 = self.screen(*b)
                c.create_line(x1, y1, x2, y2, fill="#172326", width=5 if selected else 3, tags="overlay")
                c.create_line(x1, y1, x2, y2, fill=colour, width=3 if selected else 1, dash=dash, tags="overlay")
        self.label_boxes = map_labels.layout([entry for entry, _ in visible], plane, bounds, self.screen,
            self.label_measure, self.label_styles, self.scale, (c.winfo_width(), c.winfo_height()),
            selected=self.selected_catalog,
            enabled={'zone': self.overlay_labels.get(), 'subzone': self.subzone_labels.get(), 'controller': False})
        for label in self.label_boxes:
            entry = self.catalog.entries[label['id']]
            colour = '#fff2c9' if label['selected'] else self.catalog.colour(entry)
            x, y = label['x'], label['y']
            font = self.label_font(label['style'])
            for dx, dy in ((-1,-1),(-1,1),(1,-1),(1,1)):
                c.create_text(x+dx,y+dy,text=label['text'],fill='#101a1f',font=font,tags='overlay')
            c.create_text(x,y,text=label['text'],fill=colour,font=font,tags='overlay')

    def catalog_fill(self, plane, rx, ry, size, parts):
        opacity = max(0, min(65, round(self.overlay_opacity.get())))
        key = self.catalog_overlay_version, plane, rx, ry, size, opacity
        if key in self.catalog_photo_cache:
            self.catalog_photo_cache.move_to_end(key)
            return self.catalog_photo_cache[key][0]
        region = {"minX": rx * 64, "minY": ry * 64, "maxX": rx * 64 + 63, "maxY": ry * 64 + 63}
        touching = [(entry, part) for entry, part in parts if self._intersects(part_bounds(part), region)]
        if not touching:
            self.catalog_photo_cache[key] = None, 0
            return None
        image = Image.new("RGBA", (size, size))
        draw = ImageDraw.Draw(image)
        scale = size / 64
        for entry, part in touching:
            value = self.catalog.colour(entry).lstrip("#")
            colour = tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (int(opacity * 2.55),)
            if part.get("type") == "polygon":
                points = [((x - rx * 64) * scale, ((ry + 1) * 64 - y) * scale) for x, y in part.get("vertices", [])]
                if len(points) >= 3:
                    draw.polygon(points, fill=colour)
            else:
                left = math.floor((max(part["minX"], region["minX"]) - rx * 64) * scale)
                top = math.floor(((ry + 1) * 64 - min(part["maxY"], region["maxY"]) - 1) * scale)
                right = math.ceil((min(part["maxX"], region["maxX"]) + 1 - rx * 64) * scale) - 1
                bottom = math.ceil(((ry + 1) * 64 - max(part["minY"], region["minY"])) * scale) - 1
                draw.rectangle((left, top, max(left, right), max(top, bottom)), fill=colour)
        photo = ImageTk.PhotoImage(image)
        cost = size * size * 4
        self.catalog_photo_cache[key] = photo, cost
        self.catalog_photo_bytes += cost
        while self.catalog_photo_bytes > 24 * 1024 * 1024 and len(self.catalog_photo_cache) > 1:
            _, (_, released) = self.catalog_photo_cache.popitem(last=False)
            self.catalog_photo_bytes -= released
        return photo

    @staticmethod
    def _intersects(a, b):
        return a["maxX"] >= b["minX"] and a["minX"] <= b["maxX"] and a["maxY"] >= b["minY"] and a["minY"] <= b["maxY"]

    def _photo_for(self, key, size, allow_create=True):
        photo_key = key + (size,)
        cached = self.photo_tiles.get(photo_key)
        if cached is not None:
            self.photo_tiles.move_to_end(photo_key)
            return cached[0]
        raw = self.tiles.get(key)
        if raw is None or not allow_create:
            return None
        self.tiles.move_to_end(key)
        try:
            with Image.open(io.BytesIO(raw)) as original:
                image = original.resize((size, size), Image.Resampling.NEAREST).convert("RGB")
        except (OSError, ValueError) as error:
            self.tile_bytes -= len(self.tiles.pop(key))
            self.tile_error(key, "Invalid region image: " + str(error))
            return None
        photo = ImageTk.PhotoImage(image)
        memory = size * size * 4
        self.photo_tiles[photo_key] = (photo, memory)
        self.photo_bytes += memory
        self.metrics["photoCreates"] += 1
        while self.photo_bytes > self.photo_budget and len(self.photo_tiles) > 1:
            _, (_, memory) = self.photo_tiles.popitem(last=False)
            self.photo_bytes -= memory
        return photo

    def _pump_tile_requests(self, size):
        for key in self.desired_tiles:
            if len(self.pending_tiles) >= 3:
                break
            if key in self.tiles or key + (size,) in self.photo_tiles or key in self.pending_tiles or key in self.failed_tiles:
                continue
            record = self.canvas_tiles.get(key)
            if record and record["photo"] is not None and record.get("size") == size:
                continue
            self.pending_tiles.add(key)
            self.metrics["tileRequests"] += 1
            generation = self.cache_generation
            self.client.submit(lambda k=key: self.client.request(f"/tiles/{k[0]}/{k[1]}/{k[2]}.png", binary=True),
                               lambda data, k=key, g=generation: self.loaded_tile(k, data, g),
                               lambda err, k=key, g=generation: self.tile_error(k, err, g), tile=True)

    def loaded_tile(self, key, data, generation=None):
        if generation is not None and generation != self.cache_generation:
            return
        self.pending_tiles.discard(key)
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            self.tile_error(key, "The map service did not return a PNG region.", generation)
            return
        previous = self.tiles.pop(key, None)
        if previous:
            self.tile_bytes -= len(previous)
        self.tiles[key] = data
        self.tile_bytes += len(data)
        while self.tile_bytes > self.tile_budget and len(self.tiles) > 1:
            _, old = self.tiles.popitem(last=False)
            self.tile_bytes -= len(old)
        self.schedule_draw()

    def tile_error(self, key, error, generation=None):
        if generation is not None and generation != self.cache_generation:
            return
        self.pending_tiles.discard(key)
        self.failed_tiles.add(key)
        if key in self.desired_tiles:
            self.status.set(f"Region {(key[1] << 8) | key[2]} could not be rendered: {str(error)[:150]}")
        self.schedule_draw()

    def motion(self, event):
        wx, wy = self.world(event.x, event.y)
        x, y = math.floor(wx), math.floor(wy)
        self.hover.set(f"X {x}  Y {y}  Plane {self.plane.get()}   ·   Region {(x // 64 << 8) | (y // 64)}")
        tile = x, y, self.plane.get(), self.catalog_overlay_version, self.show_existing.get()
        if tile != self.catalog_hover_tile:
            self.catalog_hover_tile = tile
            hits = self.catalog.hits(x, y, self.plane.get()) if self.show_existing.get() else []
            names = [entry.get("name", entry["id"]) for entry in hits]
            self.containment_label.configure(text=(" · ".join(names[:2]) + (f" +{len(names)-2} more" if len(names)>2 else ""))[:105])
        if self.first_corner:
            self.preview_corner = (x, y)
            self.canvas.delete("preview")
            self.draw_rectangle_preview()

    def draw_rectangle_preview(self):
        a, b = self.first_corner, self.preview_corner
        left, right = min(a[0], b[0]), max(a[0], b[0])
        bottom, top = min(a[1], b[1]), max(a[1], b[1])
        x1, y1 = self.screen(left, top + 1)
        x2, y2 = self.screen(right + 1, bottom)
        self.canvas.create_rectangle(x1, y1, x2, y2, outline=CYAN, width=2, dash=(5, 3), tags="preview")

    def press(self, event):
        self.drag = (event.x, event.y, self.center_x, self.center_y, False)

    def press_pan(self, event):
        self.press(event)
        self.canvas.configure(cursor="fleur")

    def drag_map(self, event):
        if not self.drag:
            return
        x, y, cx, cy, moved = self.drag
        moved = moved or abs(event.x - x) + abs(event.y - y) > 5
        self.drag = (x, y, cx, cy, moved)
        if moved:
            self.center_x = cx - (event.x - x) / self.scale
            self.center_y = cy + (event.y - y) / self.scale
            self.schedule_draw()

    def release(self, event):
        moved = self.drag and self.drag[4]
        self.drag = None
        if moved:
            self.queue_preferences()
            return
        x, y = self.world(event.x, event.y)
        x, y = math.floor(x), math.floor(y)
        if self.mode.get() == "rectangle":
            if self.first_corner is None:
                self.first_corner = self.preview_corner = (x, y)
                self.status.set("First corner selected. Click the opposite corner; Escape cancels.")
            else:
                self.add_rectangle(x, y)
        elif self.mode.get() in ("region", "chunk"):
            self.select_grid_at(x, y, self.mode.get())
        elif self.select_npc_at(event.x, event.y):
            return
        elif self.mode.get() == "zones":
            labelled = next((item for item in reversed(self.label_boxes) if item["box"][0] <= event.x <= item["box"][2] and item["box"][1] <= event.y <= item["box"][3]), None)
            if labelled:
                self.select_catalog(labelled["id"], from_map=True)
            else:
                self.select_catalog_at(x, y)
        else:
            self.inspect(x, y)
            self.notebook.select(self.inspect_tab)

    def release_pan(self, event):
        self.drag = None
        self.canvas.configure(cursor="crosshair")
        self.queue_preferences()

    def wheel(self, event):
        self.zoom(2 if event.delta > 0 else .5, event.x, event.y)

    def zoom(self, factor, x=None, y=None):
        x = self.canvas.winfo_width() / 2 if x is None else x
        y = self.canvas.winfo_height() / 2 if y is None else y
        before_x, before_y = self.world(x, y)
        self.scale = max(.25, min(16, self.scale * factor))
        after_x, after_y = self.world(x, y)
        self.center_x += before_x - after_x
        self.center_y += before_y - after_y
        self.schedule_draw()
        self.queue_preferences()

    def change_plane(self):
        self.cancel_rectangle()
        self.inspected = None
        self.generations["search"] += 1
        self.search_points = []
        self.results.delete(*self.results.get_children())
        self.set_text(self.inspect_text, f"Floor {self.plane.get()} selected. Click a tile to inspect it.")
        self.schedule_draw()
        self.catalog_hover_tile = None
        self.refresh_grid_plane()
        self.status.set(f"Plane {self.plane.get()} selected. Underground locations may use different world coordinates.")
        self.queue_preferences()

    def go_to(self, x, y, plane=None):
        self.center_x, self.center_y = float(x) + .5, float(y) + .5
        if plane is not None:
            self.plane.set(max(0, min(3, int(plane))))
        self.cancel_rectangle()
        self.schedule_draw()
        self.queue_preferences()

    def jump(self):
        try:
            x, y = int(self.jump_x.get()), int(self.jump_y.get())
        except ValueError:
            self.error("Enter whole numbers for X and Y.")
            return
        self.go_to(x, y)
        self.inspect(x, y)

    def inspect(self, x, y):
        plane = self.plane.get()
        self.inspected = (x, y, plane)
        self.jump_x.set(str(x))
        self.jump_y.set(str(y))
        self.set_text(self.inspect_text, f"Reading tile ({x}, {y}, {plane})…")
        self.schedule_draw()
        self.client.submit(lambda: self.client.request("/api/position?" + urllib.parse.urlencode({"x": x, "y": y, "plane": plane})),
                           lambda data, pos=(x, y, plane), generation=self.cache_generation: self.loaded_position(data, pos) if generation == self.cache_generation else None)

    def loaded_position(self, data, position):
        if self.inspected != position:
            return
        x, y, plane = position
        lines = [f"TILE  {x}, {y}, {plane}", f"Region {(x // 64 << 8) | (y // 64)} · local {x % 64}, {y % 64}", ""]
        static = data.get("staticCollision", {})
        if isinstance(static, dict) and static:
            lines.extend([f"Static walkable: {'yes' if static.get('walkable') else 'no'}",
                          f"Known collision: {'yes' if static.get('known') else 'no'}",
                          f"Collision mask: {static.get('mask', '?')}", ""])
        for key in ("known", "available", "walkable", "blocked", "collision", "flags", "underlay", "overlay", "bridge", "roof", "terrain", "warning"):
            if key in data:
                value = data[key]
                lines.append(f"{key.replace('_', ' ').capitalize()}: {json.dumps(value, ensure_ascii=False) if isinstance(value, (dict, list)) else value}")
        objects = data.get("objects", [])
        lines.extend(["", f"OBJECTS  ({len(objects)})"])
        for obj in objects:
            lines.extend([f"\n{obj.get('name') or 'Unnamed object'}  ·  ID {obj.get('id', '?')}",
                          f"Type {obj.get('type', '?')}  ·  rotation {obj.get('rotation', '?')}",
                          "Actions: " + ", ".join(str(a) for a in obj.get("actions", []) if a)])
        if not objects:
            lines.append("No cached objects reported on this tile.")
        lines.extend(["", "Collision is a static cache view. Doors, transforms, bridges and server scripts can change reachability."])
        self.set_text(self.inspect_text, "\n".join(lines))

    def search(self):
        name = self.search_name.get().strip()
        if not name:
            self.error("Enter a zone, NPC or object name or identifier.")
            return
        kind = {'Zones': 'zones', 'NPCs': 'npcs', 'Objects': 'objects'}[self.search_kind.get()]
        params = {"kind": kind, "name": name, "limit": 300}
        if not self.search_all_planes.get():
            params['plane'] = self.plane.get()
        if self.search_unplaced.get() and kind != 'zones':
            params['includeUnplaced'] = 1
        if self.search_view_only.get():
            bounds = self.view_bounds()
            if bounds["maxX"] - bounds["minX"] >= 512 or bounds["maxY"] - bounds["minY"] >= 512:
                self.error("Zoom in to search a view smaller than 512 × 512 tiles, or turn off visible map only.")
                return
            params.update(bounds)
        self.status.set(f"Finding {name}…")
        self.guarded_request("search", lambda: self.client.request("/api/search?" + urllib.parse.urlencode(params)), self.loaded_search)

    @staticmethod
    def location_label(item):
        context = item.get('context') or {}
        if context.get('label'):
            return str(context['label'])
        if item.get('locationLabel'):
            return str(item['locationLabel'])
        if item.get('definitionOnly') or item.get('x') is None and item.get('kind') not in ('zone', 'subzone'):
            return 'No known placement'
        return 'Zone membership unknown'

    def loaded_search(self, data):
        self.search_points = data.get("results", data.get("objects", []))
        self.results.delete(*self.results.get_children())
        for i, item in enumerate(self.search_points):
            kind = item.get('kind', 'object')
            badge = {'npc': 'NPC', 'object': 'Object', 'zone': 'Zone', 'subzone': 'Subzone'}.get(kind, kind.title())
            description = f"{badge} · {item.get('name', 'Unnamed')} — {self.location_label(item)}"
            if kind in ('zone', 'subzone'):
                planes = item.get('planes')
                location = 'all planes' if planes is None else 'plane ' + ','.join(map(str, planes))
            else:
                location = 'unplaced' if item.get('x') is None else f"{item['x']}, {item['y']}, {item.get('plane', 0)}"
            self.results.insert("", "end", iid=str(i), values=(description, location))
        self.status.set(f"Found {len(self.search_points):,} results" + (" · more results exist; narrow the search." if data.get("truncated") else ". Select a result to inspect it."))
        self.schedule_draw()

    def select_search(self, event=None):
        selected = self.results.selection()
        if not selected:
            return
        item = self.search_points[int(selected[0])]
        if item.get('kind') in ('zone', 'subzone') or item.get('catalogId'):
            self.reveal_catalog_result(item.get('catalogId') or item['id'])
            return
        self.select_entity(item)

    def select_entity(self, item, from_marker=False):
        item = dict(item)
        item.setdefault('kind', 'object')
        self.selected_entity = item
        self.generations['entity'] += 1
        generation = self.generations['entity']
        if from_marker:
            self.search_kind.set('NPCs')
            self.loaded_search({'results': [item]})
        if item.get('x') is not None and item.get('y') is not None:
            if not from_marker:
                self.go_to(item['x'], item['y'], item.get('plane', self.plane.get()))
            self.inspected = (item['x'], item['y'], item.get('plane', self.plane.get()))
        self.show_entity_details(item)
        self.notebook.select(self.search_tab)
        if not self.sidebar_visible:
            self.toggle_sidebar()
        self.schedule_draw()
        if item.get('kind') == 'npc' and not item.get('definitionOnly') and not item.get('context'):
            key = self.entity_key(item)
            def loaded(data):
                if self.generations['entity'] != generation or not self.selected_entity or self.entity_key(self.selected_entity) != key:
                    return
                detailed = data.get('spawn', data.get('npc', data))
                if not isinstance(detailed, dict):
                    return
                self.selected_entity = dict(detailed, kind='npc')
                self.show_entity_details(self.selected_entity)
                self.schedule_draw()
            self.client.submit(lambda: self.client.request('/api/npc?' + urllib.parse.urlencode({'id': key})), loaded)

    def show_entity_details(self, item):
        context = item.get('context') or {}
        kind = item.get('kind', 'object')
        native_id = item.get('npcId', item.get('objectId', item.get('id', '?')))
        lines = [f"{item.get('name', 'Unnamed')} · {kind.upper()} ID {native_id}", self.location_label(item)]
        if item.get('x') is None:
            lines += ['Definition only: no known placement was loaded.', 'This result cannot be focused on the map.']
        else:
            lines += [f"Position({item['x']}, {item['y']}, {item.get('plane', 0)})", 'Placement: ' + self.entity_key(item)]
        if context.get('status') == 'outside':
            lines.append('Known position outside all resolved source zones.')
        elif context.get('status') == 'unknown':
            lines.append('Some zone membership could not be determined; inspect the source diagnostics.')
        elif not context and kind == 'npc' and item.get('x') is not None:
            lines.append('Loading zone and subzone context…')
        matches = context.get('matches') or context.get('additionalMatches') or []
        if matches:
            lines.append('Applicable areas:')
            for match in matches:
                lines.append('• ' + (str(match.get('name') or match.get('id')) if isinstance(match, dict) else str(match)))
        if context.get('warnings'):
            lines.extend('• ' + str(value) for value in context['warnings'])
        if item.get('actions'):
            lines.append('Actions: ' + ', '.join(str(a) for a in item['actions'] if a))
        if item.get('source'):
            lines.append('Source: ' + self.diagnostic_text(item['source']))
        for key in ('size', 'combatLevel', 'defaultDirection', 'direction', 'walkingDistance', 'walkDistance',
                    'respawnTicks', 'respawn', 'routeStrategy', 'wander', 'morph', 'options', 'definitionStatus'):
            if key in item:
                lines.append(key + ': ' + json.dumps(item[key], ensure_ascii=False))
        if kind == 'npc':
            lines.append('Source spawn placement; live NPC movement and morph state are not shown.')
        self.set_text(self.search_details, '\n'.join(lines))
        primary = context.get('primarySubzone') or context.get('primaryZone')
        key = primary.get('id') if isinstance(primary, dict) else primary
        if key in self.catalog.entries:
            self.select_catalog(key)
            self.catalog_query.set('')
            self.refresh_catalog_tree()
            iid = '@entry/' + key
            if self.catalog_tree.exists(iid):
                parent = self.catalog_tree.parent(iid)
                while parent:
                    self.catalog_tree.item(parent, open=True)
                    parent = self.catalog_tree.parent(parent)
                self.catalog_tree.selection_set(iid)
                self.catalog_tree.see(iid)

    def cancel_rectangle(self):
        self.first_corner = self.preview_corner = None
        self.canvas.delete("preview")

    def add_rectangle(self, x, y):
        first = self.first_corner
        self.cancel_rectangle()
        bounds = {"minX": min(first[0], x), "minY": min(first[1], y), "maxX": max(first[0], x), "maxY": max(first[1], y), "plane": self.plane.get()}
        number = sum(z.get("source") == "manual" for z in self.zones) + 1
        zone = {"id": "manual-" + uuid.uuid4().hex[:12], "name": f"Manual area {number}", "bounds": bounds,
                "source": "manual", "projectIdentity": self.project_identity, "validation": {"status": "unverified", "message": "Save this draft to validate its tiles and inside point."}}
        self.zones.append(zone)
        self.undo_stack.append(zone)
        self.redo_stack.clear()
        self.refresh_zones(str(zone["id"]))
        self.notebook.select(self.zones_tab)
        self.status.set("Rectangle created. Checking its tiles and choosing an inside point…")
        self.validate_zone(zone)

    def validate_zone(self, zone):
        request_id = uuid.uuid4().hex
        zone["_validationRequest"] = request_id
        payload = {"bounds": dict(zone["bounds"]), "projectIdentity": self.project_identity}

        def checked(result):
            if (zone not in self.zones or zone.get("_validationRequest") != request_id
                    or result.get("projectIdentity", self.project_identity) != self.project_identity):
                return
            zone.update(result)
            self.refresh_zones(self.selected_zone)
            valid = result.get("validation", {}).get("valid", False)
            self.status.set("Rectangle checked. Review its inside point and code, then save the local draft." if valid else
                            "Rectangle needs review. Open Validation details to see missing or blocked tiles.")

        def failed(error):
            if zone in self.zones and zone.get("_validationRequest") == request_id:
                zone["validation"] = {"valid": False, "message": str(error)}
                self.refresh_zones(self.selected_zone)
                self.error(error)

        self.client.submit(lambda: self.client.request("/api/validate", payload), checked, failed)

    def undo(self):
        if not self.undo_stack:
            self.status.set("No manual rectangle to undo.")
            return
        zone = self.undo_stack.pop()
        if zone in self.zones:
            self.zones.remove(zone)
        self.redo_stack.append(zone)
        self.refresh_zones()
        self.status.set("Manual rectangle removed from the view. Saved draft files remain available.")

    def redo(self):
        if not self.redo_stack:
            self.status.set("No manual rectangle to redo.")
            return
        zone = self.redo_stack.pop()
        self.zones.append(zone)
        self.undo_stack.append(zone)
        self.refresh_zones(str(zone["id"]))
        self.status.set("Manual rectangle restored.")

    def propose(self):
        try:
            bounds = {key: int(var.get()) for key, var in self.bound_vars.items()}
            if bounds["minX"] > bounds["maxX"] or bounds["minY"] > bounds["maxY"]:
                raise ValueError()
        except ValueError:
            self.error("Enter valid whole-number bounds, west ≤ east and south ≤ north.")
            return
        if bounds["maxX"] - bounds["minX"] >= 512 or bounds["maxY"] - bounds["minY"] >= 512:
            self.error("Choose an area no larger than 512 × 512 tiles. Zoom in or enter smaller bounds.")
            return
        name = self.propose_name.get().strip()
        if not name:
            self.error("Enter the object name or action to group into subzones.")
            return
        payload = dict(bounds, name=name, plane=self.plane.get(), radius=8, padding=2, minObjects=2, projectIdentity=self.project_identity)
        self.status.set(f"Proposing subzones around {name}…")
        self.client.submit(lambda: self.client.request("/api/propose", payload), self.loaded_proposals)

    def loaded_proposals(self, data):
        if data.get("projectIdentity", self.project_identity) != self.project_identity:
            return
        candidates = data.get("candidates", [])
        existing = {str(z.get("id")) for z in self.zones}
        added = []
        for item in candidates:
            item.setdefault("id", "proposal-" + uuid.uuid4().hex[:12])
            item.setdefault("projectIdentity", data.get("projectIdentity", self.project_identity))
            if data.get("fingerprints"):
                item.setdefault("fingerprints", data["fingerprints"])
            if str(item["id"]) not in existing:
                item.setdefault("source", "proposal")
                self.zones.append(item)
                added.append(item)
        self.refresh_zones(str(added[0]["id"]) if added else None)
        self.status.set(f"Proposed {len(candidates)} subzones. Review bounds, inside points and validation before saving." + (" " + str(data["warning"]) if data.get("warning") else ""))

    def refresh_zones(self, select=None):
        wanted = select or self.selected_zone
        self.zone_tree.delete(*self.zone_tree.get_children())
        for zone in self.zones:
            identifier = str(zone["id"])
            state = "Cache differs" if zone.get("baselineMatches") is False else ("Saved" if zone.get("_saved") else ("Manual" if zone.get("source") == "manual" else "Proposed"))
            self.zone_tree.insert("", "end", iid=identifier, values=(zone.get("name", "Subzone"), state))
        if wanted and self.zone_tree.exists(wanted):
            self.zone_tree.selection_set(wanted)
            self.zone_tree.see(wanted)
            self.select_zone()
        else:
            self.selected_zone = None
            self.zone_info.set(f"{len(self.zones)} areas in this workspace.")
        self.schedule_draw()

    def get_selected_zone(self):
        return next((zone for zone in self.zones if str(zone.get("id")) == self.selected_zone), None)

    def select_zone(self, event=None):
        selection = self.zone_tree.selection()
        if not selection:
            return
        self.selected_zone = selection[0]
        zone = self.get_selected_zone()
        if not zone:
            return
        self.zone_name.set(zone.get("name", ""))
        b = zone["bounds"]
        inside = zone.get("inside", {})
        validation = zone.get("validation", {})
        if isinstance(validation, dict):
            validation_text = "Static check passed · live behavior unverified" if validation.get("valid") else "Needs validation before use"
            if "walkableTiles" in validation:
                validation_text += f"\n{validation['walkableTiles']} walkable · {validation.get('componentCount', '?')} connected areas"
        else:
            validation_text = str(validation)[:150]
        if zone.get("baselineMatches") is False:
            validation_text = "Cache differs from this saved draft. Generate it again."
        inside_text = f"{inside.get('x', '?')}, {inside.get('y', '?')}" if inside else "chosen on save"
        self.zone_info.set(f"X {b['minX']}…{b['maxX']} · Y {b['minY']}…{b['maxY']} · floor {b.get('plane', 0)}\n{b['maxX'] - b['minX'] + 1} × {b['maxY'] - b['minY'] + 1} tiles · Inside {inside_text}\n{validation_text}")
        self.set_text(self.code_text, zone.get("code") or "Checking this rectangle before generating Luna code…")
        self.schedule_draw()

    def zone_details(self):
        zone = self.get_selected_zone()
        if not zone:
            self.error("Select an area to inspect its validation.")
            return
        dialog = tk.Toplevel(self.root)
        dialog.title(zone.get("name", "Subzone") + " · Validation")
        dialog.geometry("640x600")
        dialog.configure(bg=PANEL)
        dialog.transient(self.root)
        frame = ttk.Frame(dialog, padding=18)
        frame.pack(fill="both", expand=True)
        ttk.Label(frame, text=zone.get("name", "Subzone"), style="Title.TLabel").pack(anchor="w", pady=(0, 10))
        text = self._text(frame, fixed=True)
        self.set_text(text, json.dumps({"bounds": zone.get("bounds"), "inside": zone.get("inside"), "validation": zone.get("validation"), "objects": zone.get("objects", [])}, ensure_ascii=False, indent=2))
        ttk.Button(frame, text="Close", command=dialog.destroy).pack(anchor="e", pady=(12, 0))

    def rename_zone(self):
        zone = self.get_selected_zone()
        if not zone:
            self.error("Select an area to rename.")
            return
        name = self.zone_name.get().strip()
        if not name:
            self.error("Enter an area name.")
            return
        zone["name"] = name
        zone["_saved"] = False
        self.refresh_zones(self.selected_zone)
        self.status.set("Area renamed. Save it to update its local draft.")

    @staticmethod
    def draft_payload(zone):
        return {key: value for key, value in zone.items() if not key.startswith("_")}

    def save_selected(self):
        zone = self.get_selected_zone()
        if zone is None:
            self.error("Select an area to save.")
            return
        self.save_zones([zone])

    def save_all(self):
        zones = [zone for zone in self.zones if not zone.get("_saved")]
        if not zones:
            self.status.set("All current drafts are already saved.")
            return
        self.save_zones(zones)

    def save_zones(self, zones):
        payloads = [self.draft_payload(zone) for zone in zones]
        self.status.set(f"Validating and saving {len(payloads)} local drafts…")

        def save():
            results = []
            for payload in payloads:
                try:
                    result = self.client.request("/api/drafts", payload)
                    wrapped = result.get("draft")
                    results.append((str(payload["id"]), wrapped if isinstance(wrapped, dict) else result, None))
                except Exception as error:
                    results.append((str(payload["id"]), None, str(error)))
            return results

        self.client.submit(save, self.saved_zones)

    def saved_zones(self, results):
        failures = []
        count = 0
        for identifier, result, error in results:
            zone = next((item for item in self.zones if str(item.get("id")) == identifier), None)
            if error:
                failures.append(error)
            elif zone is not None:
                if zone.get("name") == result.get("name"):
                    zone.update(result)
                    zone["_saved"] = True
                else:
                    zone["_saved"] = False
                count += 1
        self.refresh_zones(self.selected_zone)
        self.status.set(f"Saved {count} local drafts." + (" " + str(failures[0])[:250] if failures else " Source cache data is unchanged."))
        pending = self.pending_project_request
        self.pending_project_request = None
        if pending and not failures:
            self._open_project(*pending)

    def code_value(self):
        zone = self.get_selected_zone()
        if zone and zone.get("baselineMatches") is False:
            return ""
        if zone and not zone.get("validation", {}).get("valid", False):
            return ""
        return zone.get("code", "") if zone else ""

    def copy_code(self):
        code = self.code_value()
        if not code:
            self.error("Select a validated area with generated code first.")
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(code)
        self.status.set("Luna code copied to the clipboard.")

    def export_code(self):
        code = self.code_value()
        if not code:
            self.error("Select a validated area with generated code first.")
            return
        filename = filedialog.asksaveasfilename(parent=self.root, title="Export Luna area code", defaultextension=".txt", filetypes=[("Text", "*.txt"), ("Kotlin", "*.kt"), ("Java", "*.java")])
        if filename:
            try:
                Path(filename).write_text(code + "\n", encoding="utf-8")
                self.status.set("Area code exported to " + filename)
            except OSError as error:
                self.error(str(error))

    def export_map(self):
        if not self.summary:
            self.error("Wait for the local map to finish loading before exporting.")
            return
        bounds = self.view_bounds()
        if bounds["maxX"] - bounds["minX"] >= 512 or bounds["maxY"] - bounds["minY"] >= 512:
            self.error("Zoom in until the visible map is no larger than 512 × 512 tiles, then export again.")
            return
        if min(bounds.values()) < 0 or max(bounds.values()) > 16383:
            self.error("Move the visible map inside the world coordinate range before exporting.")
            return
        plane = self.plane.get()
        filename = filedialog.asksaveasfilename(
            parent=self.root, title="Export visible map as PNG",
            initialfile=f"luna-atlas-377-floor-{plane}-{bounds['minX']}-{bounds['minY']}.png",
            defaultextension=".png", filetypes=[("PNG map image", "*.png")],
        )
        if not filename:
            return
        visible = [key for key, enabled in self.catalog.visible.items() if enabled] if self.show_existing.get() else []
        scale = max(1, min(8, round(self.scale)))
        params = dict(bounds, plane=plane, scale=scale, zones=1, existingZones=0,
                      catalogIds=json.dumps(visible), overlayOpacity=self.overlay_opacity.get()/100,
                      labels=1, labelStyles=json.dumps(self.label_styles), layers=json.dumps(self.layer_preferences()),
                      grids=json.dumps(self.grid_preferences()), npcMarkers=int(self.npc_markers.get()),
                      labelDpi=round(self.root.winfo_fpixels('1i'), 3), selectedCatalogId=self.selected_catalog or '',
                      selectedNpcId=self.entity_key(self.selected_entity) if self.selected_entity and self.selected_entity.get('kind') == 'npc' else '')
        included = "saved drafts and visible source zones" if visible else "saved drafts"
        self.status.set(f"Rendering the visible map at {scale} pixels per tile, with {included} and map layers…")

        def export():
            raw = self.client.request("/api/map.png?" + urllib.parse.urlencode(params), binary=True)
            if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
                raise ValueError("The local service did not return a PNG map.")
            Path(filename).write_bytes(raw)
            return filename

        self.client.submit(export, lambda path: self.status.set(f"Map PNG exported with {included}: " + path))

    def help(self):
        messagebox.showinfo("Luna Atlas · Help", "Open and explore your Luna project\n\n"
            "• Open Luna Directory discovers the native cache, configuration, zone and controller sources. Missing resources can be selected in the resolver. Reload reads source changes; Report explains loading and unresolved definitions.\n"
            "• ☰ Controls collapses the panel. Drag the map to pan; scroll or + / − zoom around the pointer. Plane selects levels 0–3. Dungeons can occupy separate world coordinates.\n"
            "• Zones mode selects a visible area from the map. Overlaps offer a chooser. Selection stays synchronized with the source tree. Tile mode reads cache objects and collision.\n"
            "• The Zones tab is searchable by name, identifier, status or x,y[,plane]. Click the View column or press Space to toggle one item. Group on/off includes descendants. Show all/Hide all affects all definitions. These are Atlas display controls; gameplay registration is unchanged.\n"
            "• Zone names are larger than subzone names. Zone labels / Subzone labels are independent; Layers / label fonts controls boundary visibility and each font family, size, bold and italic. Labels use actual effective parts, suppress overlaps and small labels at low zoom, and prioritize the selected visible area.\n"
            "• Find switches between Zones, NPCs and Objects. Results show each placement's zone hierarchy, coordinates and plane. Selecting a hidden zone explicitly reveals its boundary and label. Definitions without placements show details without inventing a location.\n"
            "• NPCs toggles source-spawn markers. Click a marker for its details and context; crowded markers cluster at low zoom, and overlapping placements offer a chooser. Reload refreshes changed NPC JSON. These markers show source spawn positions, not live NPC movement.\n"
            "• Grids independently controls 64×64 regions and 8×8 chunks, their labels and map selection. Region lines are stronger; chunk detail appears when zoomed in. Custom grid display labels are saved per project and do not change Luna. Luna Chunk.x/y is global chunk coordinate minus 6.\n"
            "• View, zoom, plane and overlay preferences are remembered per project. Tile → Copy Position writes Luna's Position(x, y, plane) format.\n"
            "• Draw area selects two inclusive corner tiles. Drafts keeps manual areas and automatic object-cluster proposals separate from source definitions. Ctrl+Z/Ctrl+Y undo and redo manual rectangles. Validate, review and Save drafts before closing or switching projects.\n"
            "• Export map saves the visible 377 imagery, saved drafts and currently visible source overlays as PNG. Code export previews area/inside values; it does not install gameplay code.\n\n"
            "Accuracy\nThe map uses the selected revision 377 cache with native colours and scenery. Modern OSRS tiles are not an authoritative replacement. Source analysis shows what is registered; runtime-only conditions require live evidence. Static collision and resource proposals do not prove live door/quest behavior, global routes or banking.", parent=self.root)

    def close(self):
        if self.preferences_ready and not self.project_opening:
            try:
                self.client.request("/api/preferences", self.preferences_payload(), timeout=2)
            except Exception:
                pass
        self.alive = False
        for callback in self.root.tk.call("after", "info"):
            self.root.after_cancel(callback)
        self.client.pool.shutdown(wait=False, cancel_futures=True)
        if hasattr(self.client, "tile_pool"):
            self.client.tile_pool.shutdown(wait=False, cancel_futures=True)
        self.root.destroy()


def main(url=None):
    if url is None:
        parser = argparse.ArgumentParser(description="Native Luna Atlas 377 desktop")
        parser.add_argument("--url", default="http://127.0.0.1:8791", help="Local atlas service URL")
        url = parser.parse_args().url
    root = tk.Tk()
    app = AtlasApp(root, url)
    root.mainloop()
    return app


if __name__ == "__main__":
    main()
