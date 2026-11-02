"""Bounded, cache-only area planning using Luna's static collision rules.

The source cache is never modified. Doors, scripts, morph state, transports and
runtime objects require a live server check before treating drafts as gameplay.
"""

from array import array
from collections import Counter, OrderedDict, defaultdict, deque
from concurrent.futures import Future
from hashlib import sha256
import json
from threading import RLock


MAX_SIDE = 512
MAX_HITS = 5000
MAX_CANDIDATES = 100
MAX_CLUSTER_STEPS = 1_000_000
MAX_COLLISION_REGIONS = 96
MAX_OBJECT_REGIONS = 32
WALL = {(-1, 1): 1, (0, 1): 2, (1, 1): 4, (1, 0): 8,
        (1, -1): 16, (0, -1): 32, (-1, -1): 64, (-1, 0): 128}
CARDINAL = ((-1, 0), (0, 1), (1, 0), (0, -1))
DIAGONAL = ((-1, 1), (1, 1), (1, -1), (-1, -1))
LOC, DECOR, FLOOR = 0x100, 0x40000, 0x200000
SOLID = LOC | DECOR | FLOOR
LIMITATIONS = [
    "Static cache collision only; live doors, object changes, NPCs, scripts and morph state are unverified.",
    "Reachability is confined to this rectangle and plane; global routes and transports are unverified.",
]


def _integer(value, label):
    if isinstance(value, bool):
        raise ValueError(label + " must be an integer")
    try:
        result = int(value)
    except (ValueError, TypeError, OverflowError):
        raise ValueError(label + " must be an integer") from None
    if isinstance(value, float) and value != result:
        raise ValueError(label + " must be an integer")
    return result


def _bounds(value, plane=None):
    if isinstance(value, dict):
        result = {k: _integer(value[k], k) for k in ("minX", "minY", "maxX", "maxY")}
        result["plane"] = _integer(value.get("plane", 0 if plane is None else plane), "plane")
    else:
        if len(value) != 4:
            raise ValueError("bounds require minX, minY, maxX, maxY")
        result = dict(zip(("minX", "minY", "maxX", "maxY"),
                          (_integer(v, "coordinate") for v in value)))
        result["plane"] = _integer(0 if plane is None else plane, "plane")
    if not 0 <= result["plane"] <= 3:
        raise ValueError("plane must be between 0 and 3")
    if result["minX"] > result["maxX"] or result["minY"] > result["maxY"]:
        raise ValueError("bounds must be in south-west to north-east order")
    if result["minX"] < 0 or result["minY"] < 0 or result["maxX"] > 16383 or result["maxY"] > 16383:
        raise ValueError("coordinates must be between 0 and 16383")
    if result["maxX"] - result["minX"] + 1 > MAX_SIDE or result["maxY"] - result["minY"] + 1 > MAX_SIDE:
        raise ValueError("area requests are limited to 512 by 512 inclusive tiles")
    return result


def _contains(bounds, x, y):
    return bounds["minX"] <= x <= bounds["maxX"] and bounds["minY"] <= y <= bounds["maxY"]


def _footprint(obj, definition):
    width = max(1, min(255, int(definition.get("width", 1))))
    length = max(1, min(255, int(definition.get("length", 1))))
    if obj.get("rotation", 0) & 1:
        width, length = length, width
    return width, length


def _effective(obj, world):
    if "effectivePlane" in obj:
        return obj["effectivePlane"]
    plane = obj.get("plane", obj.get("rawPlane", 0))
    region = world.regions.get(((obj["x"] >> 6) << 8) | (obj["y"] >> 6))
    if region and region["flags"][4096 + (obj["x"] & 63) * 64 + (obj["y"] & 63)] & 2:
        plane -= 1
    return plane


def _matches(query, obj, definition):
    if not query:
        return True
    if query.isdecimal():
        return obj["id"] == int(query)
    if query.startswith("id:"):
        operand = query[3:].strip()
        if not operand.isdecimal():
            raise ValueError("id: requires a nonnegative object ID")
        return obj["id"] == int(operand)
    if query.startswith("action:"):
        operand = query[7:].strip()
        return bool(operand) and any(operand in str(a).casefold() for a in definition.get("actions", ()) if a)
    if query.startswith("name:"):
        operand = query[5:].strip()
        return bool(operand) and operand in str(definition.get("name", "")).casefold()
    return (query in str(definition.get("name", "")).casefold()
            or any(query in str(a).casefold() for a in definition.get("actions", ()) if a))


class CollisionWindow:
    """Static masks for one bounded rectangle, in effective server planes.

    @author lare96
    """

    def __init__(self, world, bounds, object_index=None, collision_source=None):
        self.world, self.bounds = world, _bounds(bounds)
        self.x0, self.y0 = bounds["minX"], bounds["minY"]
        self.width = bounds["maxX"] - self.x0 + 1
        self.height = bounds["maxY"] - self.y0 + 1
        self.plane = bounds["plane"]
        self.masks = array("I", [0]) * (self.width * self.height)
        self.known = bytearray(len(self.masks))
        self.unknown_definitions = set()
        self.unknown_tiles = {}
        self.morphs = set()
        self.morph_objects = []
        self._labels = None
        if collision_source is None:
            self._terrain()
            self._objects(object_index)
        else:
            collision_source.copy_into(self)

    def index(self, x, y):
        if not _contains(self.bounds, x, y):
            return None
        return (x - self.x0) * self.height + y - self.y0

    def point(self, index):
        return self.x0 + index // self.height, self.y0 + index % self.height

    def flag(self, x, y, mask):
        index = self.index(x, y)
        if index is not None:
            self.masks[index] |= mask

    def _terrain(self):
        for x in range(self.x0, self.bounds["maxX"] + 1):
            for y in range(self.y0, self.bounds["maxY"] + 1):
                index = self.index(x, y)
                region = self.world.regions.get(((x >> 6) << 8) | (y >> 6))
                if region is None:
                    self.masks[index] |= FLOOR
                    continue
                local = (x & 63) * 64 + (y & 63)
                # CollisionManager lowers every map tile in a bridged column.
                raw_plane = self.plane + bool(region["flags"][4096 + local] & 2)
                if raw_plane > 3:
                    self.masks[index] |= FLOOR
                    continue
                offset = raw_plane * 4096 + local
                self.known[index] = 1
                if region["flags"][offset] & 1:
                    self.masks[index] |= FLOOR

    def _source_objects(self):
        # The uncached path also supports mutable test fixtures. Captured World
        # snapshots use the bounded spatial index instead of this broad scan.
        for rx in range(max(0, self.x0 - 255) >> 6, ((self.bounds["maxX"] + 1) >> 6) + 1):
            for ry in range(max(0, self.y0 - 255) >> 6, ((self.bounds["maxY"] + 1) >> 6) + 1):
                region = self.world.regions.get((rx << 8) | ry)
                if region is None:
                    continue
                for obj in region.get("objects", ()):
                    if _effective(obj, self.world) == self.plane:
                        yield obj

    def _objects(self, object_index=None):
        objects = object_index.objects_in(self.bounds, self.plane) if object_index else self._source_objects()
        for obj in objects:
            definition = self.world.definitions.get(obj["id"])
            if definition is None:
                if _contains(self.bounds, obj["x"], obj["y"]):
                    self.unknown_definitions.add(obj["id"])
                    index = self.index(obj["x"], obj["y"])
                    self.unknown_tiles.setdefault(index, set()).add(obj["id"])
                    self.flag(obj["x"], obj["y"], LOC)
                continue
            width, length = object_index.footprint(obj) if object_index else _footprint(obj, definition)
            x, y = obj["x"], obj["y"]
            if x > self.bounds["maxX"] + 1 or y > self.bounds["maxY"] + 1 or x + width < self.x0 or y + length < self.y0:
                continue
            if definition.get("morph") or definition.get("morphs"):
                self.morphs.add(obj["id"])
                self.morph_objects.append((obj["id"], x, y, width, length))
            kind, orientation = obj.get("type", 10), obj.get("rotation", 0) & 3
            if kind == 22:
                if definition.get("solid") and definition.get("interactive"):
                    self.flag(x, y, DECOR)
            elif not definition.get("solid"):
                continue
            elif kind == 0:
                self._wall(x, y, CARDINAL[orientation])
            elif kind in (1, 3):
                self._wall(x, y, DIAGONAL[orientation])
            elif kind == 2:
                dx, dy = DIAGONAL[orientation]
                self._wall(x, y, (dx, 0))
                self._wall(x, y, (0, dy))
            elif 9 <= kind < 22:
                for xx in range(max(self.x0, x), min(self.bounds["maxX"], x + width - 1) + 1):
                    for yy in range(max(self.y0, y), min(self.bounds["maxY"], y + length - 1) + 1):
                        self.flag(xx, yy, LOC)

    def _wall(self, x, y, direction):
        dx, dy = direction
        self.flag(x, y, WALL[direction])
        self.flag(x + dx, y + dy, WALL[(-dx, -dy)])

    def walkable(self, x, y):
        index = self.index(x, y)
        return index is not None and bool(self.known[index]) and not self.masks[index] & SOLID

    def edge_open(self, x, y, nx, ny):
        first, second = self.index(x, y), self.index(nx, ny)
        if first is None or second is None:
            return False
        delta = nx - x, ny - y
        return not (self.masks[first] & WALL[delta] or self.masks[second] & WALL[(-delta[0], -delta[1])])

    def can_step(self, x, y, nx, ny):
        dx, dy = nx - x, ny - y
        if (dx, dy) not in WALL or not self.walkable(x, y) or not self.walkable(nx, ny):
            return False
        if not self.edge_open(x, y, nx, ny):
            return False
        if dx and dy:
            # Both orthogonal passages must be open; no diagonal corner cuts.
            return (self.can_step(x, y, x + dx, y) and self.can_step(x, y, x, y + dy)
                    and self.can_step(x + dx, y, nx, ny) and self.can_step(x, y + dy, nx, ny))
        return True

    def components(self):
        if self._labels is not None:
            return self._labels
        labels = array("i", [-1]) * len(self.masks)
        next_label = 0
        for index in range(len(labels)):
            x, y = self.point(index)
            if labels[index] >= 0 or not self.walkable(x, y):
                continue
            labels[index] = next_label
            queue = deque([(x, y)])
            while queue:
                x, y = queue.popleft()
                for dx, dy in CARDINAL:
                    xx, yy = x + dx, y + dy
                    other = self.index(xx, yy)
                    if other is not None and labels[other] < 0 and self.can_step(x, y, xx, yy):
                        labels[other] = next_label
                        queue.append((xx, yy))
            next_label += 1
        self._labels = labels
        return labels

    def approaches(self, obj):
        definition = self.world.definitions.get(obj["id"], {})
        width, length = _footprint(obj, definition)
        x0, y0 = obj["x"], obj["y"]
        x1, y1 = x0 + width - 1, y0 + length - 1
        result = set()
        for y in range(y0, y1 + 1):
            for x, target_x in ((x0 - 1, x0), (x1 + 1, x1)):
                if self.walkable(x, y) and self.edge_open(x, y, target_x, y):
                    result.add(self.index(x, y))
        for x in range(x0, x1 + 1):
            for y, target_y in ((y0 - 1, y0), (y1 + 1, y1)):
                if self.walkable(x, y) and self.edge_open(x, y, x, target_y):
                    result.add(self.index(x, y))
        return result


class _CollisionCache:
    """Bounded immutable region masks and placement grids for one snapshot.

    Region builds are single-flight: unrelated requests build independently,
    while concurrent requests for the same key share the completed result.

    @author lare96
    """

    def __init__(self, world):
        self.world = world
        self.dimensions = {
            object_id: _footprint({"rotation": 0}, definition)
            for object_id, definition in world.definitions.items()
        }
        self.max_dimension = max((max(size) for size in self.dimensions.values()), default=1)
        self.lock = RLock()
        self.regions = OrderedDict()
        self.object_regions = OrderedDict()
        self.pending = {}
        self.builds = Counter()

    def _get(self, kind, key, build):
        entries = self.regions if kind == "collision" else self.object_regions
        limit = MAX_COLLISION_REGIONS if kind == "collision" else MAX_OBJECT_REGIONS
        pending_key = kind, key
        with self.lock:
            if key in entries:
                value = entries[key]
                entries.move_to_end(key)
                return value
            future = self.pending.get(pending_key)
            owner = future is None
            if owner:
                future = Future()
                self.pending[pending_key] = future
        if not owner:
            return future.result()
        try:
            value = build()
        except BaseException as error:
            with self.lock:
                self.pending.pop(pending_key, None)
            future.set_exception(error)
            raise
        with self.lock:
            entries[key] = value
            entries.move_to_end(key)
            while len(entries) > limit:
                entries.popitem(last=False)
            self.builds[kind] += 1
            self.pending.pop(pending_key, None)
        future.set_result(value)
        return value

    def footprint(self, obj):
        width, length = self.dimensions.get(obj["id"], (1, 1))
        return (length, width) if obj.get("rotation", 0) & 1 else (width, length)

    def _grid(self, region_id):
        def build():
            groups = defaultdict(list)
            for obj in self.world.regions.get(region_id, {}).get("objects", ()):
                plane = _effective(obj, self.world)
                if 0 <= plane <= 3:
                    groups[(plane, (obj["x"] & 63) >> 3, (obj["y"] & 63) >> 3)].append(obj)
            return {key: tuple(values) for key, values in groups.items()}
        return self._get("objects", region_id, build)

    def objects_in(self, bounds, plane):
        # Use the real maximum dimensions, not the byte-format maximum of 255.
        # An extra tile on each far side includes neighboring wall origins and
        # the preexisting morph-warning perimeter semantics.
        x0, y0 = max(0, bounds["minX"] - self.max_dimension), max(0, bounds["minY"] - self.max_dimension)
        x1, y1 = min(16383, bounds["maxX"] + 1), min(16383, bounds["maxY"] + 1)
        for rx in range(x0 >> 6, (x1 >> 6) + 1):
            for ry in range(y0 >> 6, (y1 >> 6) + 1):
                region_id = (rx << 8) | ry
                if region_id not in self.world.regions:
                    continue
                grid = self._grid(region_id)
                local_x0, local_x1 = max(0, x0 - rx * 64) >> 3, min(63, x1 - rx * 64) >> 3
                local_y0, local_y1 = max(0, y0 - ry * 64) >> 3, min(63, y1 - ry * 64) >> 3
                for gx in range(local_x0, local_x1 + 1):
                    for gy in range(local_y0, local_y1 + 1):
                        for obj in grid.get((plane, gx, gy), ()):
                            if x0 <= obj["x"] <= x1 and y0 <= obj["y"] <= y1:
                                yield obj

    def region(self, rx, ry, plane):
        def build():
            bounds = {"minX": rx * 64, "minY": ry * 64, "maxX": rx * 64 + 63,
                      "maxY": ry * 64 + 63, "plane": plane}
            return CollisionWindow(self.world, bounds, object_index=self)
        return self._get("collision", (rx, ry, plane), build)

    def copy_into(self, window):
        bounds = window.bounds
        for rx in range(window.x0 >> 6, (bounds["maxX"] >> 6) + 1):
            for ry in range(window.y0 >> 6, (bounds["maxY"] >> 6) + 1):
                source = self.region(rx, ry, window.plane)
                x0, x1 = max(window.x0, source.x0), min(bounds["maxX"], source.x0 + 63)
                y0, y1 = max(window.y0, source.y0), min(bounds["maxY"], source.y0 + 63)
                length = y1 - y0 + 1
                for x in range(x0, x1 + 1):
                    first = (x - source.x0) * 64 + y0 - source.y0
                    target = (x - window.x0) * window.height + y0 - window.y0
                    window.masks[target:target + length] = source.masks[first:first + length]
                    window.known[target:target + length] = source.known[first:first + length]
                for index, object_ids in source.unknown_tiles.items():
                    x, y = source.point(index)
                    if _contains(bounds, x, y):
                        window.unknown_tiles[window.index(x, y)] = set(object_ids)
                        window.unknown_definitions.update(object_ids)
                for object_id, x, y, width, height in source.morph_objects:
                    if x <= bounds["maxX"] + 1 and y <= bounds["maxY"] + 1 and x + width >= window.x0 and y + height >= window.y0:
                        window.morphs.add(object_id)

    def info(self):
        with self.lock:
            return {"regions": len(self.regions), "objectRegions": len(self.object_regions),
                    "collisionBuilds": self.builds["collision"], "objectBuilds": self.builds["objects"],
                    "pending": len(self.pending), "maxDimension": self.max_dimension,
                    "maskBytes": sum(len(value.masks) * value.masks.itemsize + len(value.known)
                                     for value in self.regions.values())}


class Planner:
    """Query cache objects and create descriptive, unapproved area drafts.

    @author lare96
    """

    def __init__(self, world):
        self.world = world
        # Real World snapshots have verified fingerprints and are immutable for
        # the lifetime of a Planner. Mutable decoder/test fixtures stay uncached.
        self._collision_cache = _CollisionCache(world) if hasattr(world, "fingerprints") else None

    def _window(self, bounds):
        return CollisionWindow(self.world, bounds, collision_source=self._collision_cache)

    def collision_cache_info(self):
        return self._collision_cache.info() if self._collision_cache else {"regions": 0, "objectRegions": 0}

    def _summary(self, obj):
        definition = self.world.definitions.get(obj["id"], {})
        width, length = _footprint(obj, definition)
        return {"id": obj["id"], "name": definition.get("name") or "Object " + str(obj["id"]),
                "x": obj["x"], "y": obj["y"], "plane": _effective(obj, self.world),
                "rawPlane": obj.get("rawPlane", obj.get("plane", 0)),
                "type": obj.get("type", 10), "rotation": obj.get("rotation", 0),
                "actions": [a for a in definition.get("actions", ()) if a],
                "width": width, "length": length}

    def search(self, name="", plane=0, bounds=None, limit=200):
        plane = _integer(plane, "plane")
        if not 0 <= plane <= 3:
            raise ValueError("plane must be between 0 and 3")
        limit = max(1, min(MAX_HITS + 1, _integer(limit, "limit")))
        bounds = _bounds(bounds, plane) if bounds is not None else None
        query = str(name).strip().casefold()
        results = []
        for region_id in sorted(self.world.regions):
            region = self.world.regions[region_id]
            if bounds and (region["rx"] * 64 > bounds["maxX"] or (region["rx"] + 1) * 64 <= bounds["minX"]
                           or region["ry"] * 64 > bounds["maxY"] or (region["ry"] + 1) * 64 <= bounds["minY"]):
                continue
            for obj in region.get("objects", ()):
                if _effective(obj, self.world) != plane or (bounds and not _contains(bounds, obj["x"], obj["y"])):
                    continue
                definition = self.world.definitions.get(obj["id"], {})
                if not _matches(query, obj, definition):
                    continue
                results.append(self._summary(obj))
                if len(results) >= limit:
                    return results
        return results

    def walkable(self, x, y, plane=0):
        return self.position_info(x, y, plane)["walkable"]

    def position_info(self, x, y, plane=0):
        bounds = _bounds((x, y, x, y), plane)
        if self._collision_cache is None:
            window = CollisionWindow(self.world, bounds)
            index = 0
        else:
            x, y, plane = bounds["minX"], bounds["minY"], bounds["plane"]
            window = self._collision_cache.region(x >> 6, y >> 6, plane)
            index = (x & 63) * 64 + (y & 63)
        known, mask = bool(window.known[index]), window.masks[index]
        return {"walkable": bool(known and not mask & SOLID), "known": known,
                "mask": mask, "scope": "local-static", "globalReachability": False,
                "warnings": LIMITATIONS + (["Object definitions are missing."] if index in window.unknown_tiles else [])}

    @staticmethod
    def _code(bounds, inside):
        area = "SimpleBoxArea.of({minX}, {minY}, {maxX}, {maxY}, {plane})".format(**bounds)
        if inside is None:
            return "val area = " + area
        return ("val area = " + area + "\nval inside = Position({x}, {y}, {plane})".format(**inside))

    def _validate(self, bounds, inside=None, resources=None):
        window = self._window(bounds)
        labels = window.components()
        warnings = list(LIMITATIONS)
        counts = Counter(label for label in labels if label >= 0)
        resources = [] if resources is None else resources
        approaches = [window.approaches(obj) for obj in resources]
        coverage = Counter()
        for points in approaches:
            coverage.update({labels[index] for index in points if labels[index] >= 0})
        requested_inside = inside is not None
        if inside is None:
            chosen = max(counts, key=lambda label: (coverage[label], counts[label], -label)) if counts else None
            if chosen is not None:
                cx, cy = (bounds["minX"] + bounds["maxX"]) / 2, (bounds["minY"] + bounds["maxY"]) / 2
                points = sorted({index for resource_points in approaches for index in resource_points
                                 if labels[index] == chosen})
                if not points:
                    points = [index for index, label in enumerate(labels) if label == chosen]
                index = min(points, key=lambda i: ((window.point(i)[0] - cx) ** 2 + (window.point(i)[1] - cy) ** 2, i))
                x, y = window.point(index)
                inside = {"x": x, "y": y, "plane": bounds["plane"]}
        inside_walkable = False
        chosen = None
        if inside is not None:
            inside = {"x": _integer(inside["x"], "inside.x"), "y": _integer(inside["y"], "inside.y"),
                      "plane": _integer(inside.get("plane", bounds["plane"]), "inside.plane")}
            inside_walkable = inside["plane"] == bounds["plane"] and window.walkable(inside["x"], inside["y"])
            if inside_walkable:
                chosen = labels[window.index(inside["x"], inside["y"])]
        missing = len(window.known) - sum(window.known)
        if missing:
            warnings.append(str(missing) + " tiles have no decoded terrain at this effective plane.")
        if len(counts) > 1:
            warnings.append("Walkable tiles form multiple disconnected components inside this rectangle.")
        if not inside_walkable:
            warnings.append("The requested inside tile is blocked, unknown or outside the area." if requested_inside
                            else "No walkable inside tile exists in this rectangle.")
        if window.unknown_definitions:
            warnings.append("Unknown object definitions were conservatively blocked at their origins.")
        if window.morphs:
            warnings.append("Morphing objects use base definitions; runtime morph state is unverified.")
        reachable = [i for i, points in enumerate(approaches) if any(labels[index] == chosen for index in points)] if chosen is not None else []
        if resources and len(reachable) != len(resources):
            warnings.append("Some selected objects have no approach reachable from the inside tile within this rectangle.")
        validation = {"valid": bool(inside_walkable and not missing and not window.unknown_definitions
                                    and (not resources or len(reachable) == len(resources))),
                      "scope": "local-static", "globalReachability": False, "liveValidated": False,
                      "semanticsVerified": False, "warnings": warnings, "inclusive": True,
                      "tileCount": len(window.masks), "knownTiles": sum(window.known), "missingTiles": missing,
                      "walkableTiles": sum(counts.values()), "blockedTiles": len(window.masks) - sum(counts.values()),
                      "componentCount": len(counts), "allWalkableConnected": len(counts) == 1,
                      "reachableTiles": counts.get(chosen, 0), "insideWalkable": bool(inside_walkable),
                      "insideIsResourceApproach": bool(inside_walkable and resources and any(
                          window.index(inside["x"], inside["y"]) in points for points in approaches)),
                      "resourceCount": len(resources), "reachableResourceCount": len(reachable),
                      "inaccessibleResourceCount": len(resources) - len(reachable),
                      "unknownDefinitionIds": sorted(window.unknown_definitions), "morphObjectIds": sorted(window.morphs)}
        return {"bounds": bounds, "inside": inside, "validation": validation,
                "code": self._code(bounds, inside)}, reachable

    def validate_area(self, bounds, inside=None):
        return self._validate(_bounds(bounds), inside)[0]

    def validate_resources(self, bounds, inside, objects):
        """Revalidate resource references against native placements before save."""
        bounds = _bounds(bounds)
        if not isinstance(objects, list) or not objects or len(objects) > MAX_HITS:
            raise ValueError("resource validation requires 1 to 5000 object references")
        native, seen, indexes = [], set(), {}
        fields = ("id", "x", "y", "plane", "type", "rotation")
        for reference in objects:
            if not isinstance(reference, dict) or any(field not in reference for field in fields):
                raise ValueError("resource references require id, x, y, plane, type and rotation")
            key = tuple(_integer(reference[field], "resource." + field) for field in fields)
            object_id, x, y, plane, kind, rotation = key
            if plane != bounds["plane"] or not _contains(bounds, x, y):
                raise ValueError("resource reference is outside the area or on another plane")
            if key in seen:
                raise ValueError("duplicate resource references are not allowed")
            seen.add(key)
            region_id = ((x >> 6) << 8) | (y >> 6)
            if region_id not in indexes:
                region = self.world.regions.get(region_id, {})
                indexes[region_id] = {
                    (obj["id"], obj["x"], obj["y"], _effective(obj, self.world),
                     obj.get("type", 10), obj.get("rotation", 0)): obj
                    for obj in region.get("objects", ())
                }
            placement = indexes[region_id].get(key)
            if placement is None:
                raise ValueError("resource reference does not match a placement in the current cache")
            native.append(placement)
        result, _ = self._validate(bounds, inside, native)
        result["objects"] = [self._summary(obj) for obj in native]
        result["validation"]["verifiedResourceReferences"] = True
        return result

    def propose(self, options):
        bounds = _bounds(options)
        name = str(options.get("name", "")).strip()
        if not name:
            raise ValueError("enter an object name to propose descriptive resource clusters")
        radius = max(1, min(64, _integer(options.get("radius", 8), "radius")))
        padding = max(1, min(16, _integer(options.get("padding", 2), "padding")))
        minimum = max(1, min(1000, _integer(options.get("minObjects", 2), "minObjects")))
        limit = max(1, min(MAX_CANDIDATES, _integer(options.get("limit", 50), "limit")))
        hits = self.search(name, bounds["plane"], bounds, MAX_HITS + 1)
        truncated = len(hits) > MAX_HITS
        hits = hits[:MAX_HITS]
        named_count = len(hits)
        action = str(options.get("action", "")).strip().casefold()
        actionable = options.get("actionableOnly", True)
        non_actionable_count = sum(not obj["actions"] for obj in hits) if actionable else 0
        hits = [obj for obj in hits if (not actionable or obj["actions"])
                and (not action or any(action in value.casefold() for value in obj["actions"]))]
        window = self._window(bounds)
        labels = window.components()
        approaches = [window.approaches(obj) for obj in hits]
        by_component = defaultdict(set)
        at_tile = defaultdict(set)
        for i, points in enumerate(approaches):
            for index in points:
                if labels[index] >= 0:
                    by_component[labels[index]].add(i)
                    at_tile[index].add(i)
        # Join only resources connected by a short, collision-checked cardinal
        # walk between valid approach tiles, not merely close on the picture.
        parents = {}
        budget = MAX_CLUSTER_STEPS
        budget_exhausted = False

        def find(key):
            parents.setdefault(key, key)
            while parents[key] != key:
                parents[key] = parents[parents[key]]
                key = parents[key]
            return key

        for component, members in sorted(by_component.items()):
            for i in sorted(members):
                first = find((component, i))
                seeds = {index for index in approaches[i] if labels[index] == component}
                seen = set(seeds)
                queue = deque((index, 0) for index in sorted(seeds))
                while queue:
                    if budget <= 0:
                        budget_exhausted = True
                        truncated = True
                        break
                    budget -= 1
                    index, distance = queue.popleft()
                    for other in at_tile.get(index, ()):
                        if other in members:
                            second = find((component, other))
                            if first != second:
                                parents[second] = first
                    if distance >= radius:
                        continue
                    x, y = window.point(index)
                    for dx, dy in CARDINAL:
                        xx, yy = x + dx, y + dy
                        other = window.index(xx, yy)
                        if other is not None and other not in seen and window.can_step(x, y, xx, yy):
                            seen.add(other)
                            queue.append((other, distance + 1))
                if budget_exhausted:
                    break
            if budget_exhausted:
                break
        groups = defaultdict(list)
        for key in parents:
            groups[find(key)].append(key[1])
        candidates, emitted, claimed = [], set(), set()
        # A large footprint can be approached from several disconnected pockets.
        # Prefer the largest viable group and assign each placement only once.
        for group in sorted(groups.values(), key=lambda values: (-len(values), tuple(values))):
            selected = [i for i in sorted(group) if i not in claimed]
            if len(selected) < minimum:
                continue
            objects = [hits[i] for i in selected]
            candidate_bounds = {"minX": max(bounds["minX"], min(o["x"] for o in objects) - padding),
                                "minY": max(bounds["minY"], min(o["y"] for o in objects) - padding),
                                "maxX": min(bounds["maxX"], max(o["x"] + o["width"] - 1 for o in objects) + padding),
                                "maxY": min(bounds["maxY"], max(o["y"] + o["length"] - 1 for o in objects) + padding),
                                "plane": bounds["plane"]}
            result, reachable = self._validate(candidate_bounds, resources=objects)
            # A bounding rectangle can cut a detour that existed in the search
            # window. Keep only resources reachable within the final rectangle.
            if len(reachable) != len(objects):
                objects = [objects[i] for i in reachable]
                selected = [selected[i] for i in reachable]
                if len(objects) < minimum:
                    continue
                result, _ = self._validate(candidate_bounds, resources=objects)
            if not result["validation"]["valid"]:
                continue
            key = json.dumps([candidate_bounds, [(o["id"], o["x"], o["y"], o["type"], o["rotation"]) for o in objects]], sort_keys=True)
            if key in emitted:
                continue
            emitted.add(key)
            claimed.update(selected)
            names = sorted({obj["name"] for obj in objects})
            label = ", ".join(names[:3]) + (" and others" if len(names) > 3 else "")
            result.update({"id": "cluster-" + sha256(key.encode()).hexdigest()[:16], "draft": True,
                           "name": label + " cluster (" + str(len(objects)) + " objects)",
                           "objects": objects, "method": "static approach connectivity",
                           "radius": radius, "distanceMetric": "cardinal walking steps"})
            candidates.append(result)
            if len(candidates) >= limit:
                truncated = True
                break
        return {"candidates": candidates, "matchedObjects": len(hits), "namedObjects": named_count,
                "skippedNonActionable": non_actionable_count, "skippedByFilters": named_count - len(hits),
                "actionableOnly": bool(actionable),
                "action": action, "truncated": truncated,
                "bounds": bounds, "draft": True, "scope": "local-static", "globalReachability": False,
                "clusterSteps": MAX_CLUSTER_STEPS - budget, "budgetExhausted": budget_exhausted,
                "warnings": list(LIMITATIONS) + (["Result or planning limits were reached; narrow the search."] if truncated else [])}
