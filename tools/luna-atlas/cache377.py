"""Strict, read-only revision 377 world snapshot for the local Luna atlas.

The sector/JAG/update formats follow Luna Studio's CacheSnapshot/JagArchive and
the selected 377 client's ObjectDefinition, TileDefinition and Region readers.
No source-cache writer is present. Terrain arrays retain native map planes; placement
``plane`` includes Luna's four explicit agility-object corrections, while
``effectivePlane`` additionally applies the server's bridge lowering. Definition
``solid``, ``impenetrable`` and ``interactive`` include the native derived
defaults used by Luna's server decoder; native aliases retain API compatibility.
Terrain height operands are consumed but no derived heights are advertised.

@author lare96
"""

from __future__ import annotations

import bz2
import hashlib
import json
import os
import struct
import threading
import time
import uuid
import zlib
import zipfile
from collections import Counter, OrderedDict
from collections.abc import Sequence
from pathlib import Path


STORE_NAMES = ("main_file_cache.dat",) + tuple(
    f"main_file_cache.idx{index}" for index in range(5)
)
MAX_DATA_BYTES = 0x3C00000
MAX_FILE_BYTES = 600_000
MAX_MAP_BYTES = 65_000
REGION_TILES = 4 * 64 * 64
SERVER_PLANE_CORRECTIONS = frozenset((2294, 2295, 2311, 2297))
CACHE_SCHEMA = 1
PLACEMENT_RECORD = struct.Struct(">HHB")
# Capture the loaded decoder's identity once. A running desktop must not label
# old in-memory code with a newer file's hash if an agent updates its sources.
DECODER_SHA = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()


class CacheError(ValueError):
    """An unsupported, corrupt or changing source cache; author lare96."""


class Reader:
    """A bounded big-endian native stream cursor; author lare96."""

    __slots__ = ("data", "at", "label")

    def __init__(self, data: bytes, label: str):
        self.data = data
        self.at = 0
        self.label = label

    def take(self, length: int) -> bytes:
        end = self.at + length
        if length < 0 or end > len(self.data):
            raise CacheError(f"{self.label}: truncated operand at byte {self.at}")
        value = self.data[self.at:end]
        self.at = end
        return value

    def u8(self) -> int:
        if self.at >= len(self.data):
            raise CacheError(f"{self.label}: truncated operand at byte {self.at}")
        value = self.data[self.at]
        self.at += 1
        return value

    def u16(self) -> int:
        return int.from_bytes(self.take(2), "big")

    def u24(self) -> int:
        return int.from_bytes(self.take(3), "big")

    def smart(self) -> int:
        if self.at >= len(self.data):
            raise CacheError(f"{self.label}: missing smart value at byte {self.at}")
        return self.u8() if self.data[self.at] < 128 else self.u16() - 32768

    def text(self) -> str:
        end = self.data.find(b"\n", self.at)
        if end < 0:
            raise CacheError(f"{self.label}: unterminated text at byte {self.at}")
        # This native reader converts single bytes, rather than UTF-8 sequences.
        value = self.data[self.at:end].decode("latin-1")
        self.at = end + 1
        return value

    def finish(self) -> None:
        if self.at != len(self.data):
            raise CacheError(
                f"{self.label}: {len(self.data) - self.at} trailing bytes at {self.at}"
            )


def name_hash(name: str) -> int:
    value = 0
    for char in name.upper():
        value = (value * 61 + ord(char) - 32) & 0xFFFFFFFF
    return value


def _bzip(payload: bytes, expected: int, label: str) -> bytes:
    try:
        decoder = bz2.BZ2Decompressor()
        raw = decoder.decompress(b"BZh1" + payload, max_length=expected + 1)
        if len(raw) != expected or not decoder.eof or decoder.unused_data:
            raise CacheError(f"{label}: invalid BZIP2 boundary or declared size")
        return raw
    except (OSError, EOFError) as error:
        raise CacheError(f"{label}: invalid BZIP2 stream: {error}") from error


class Archive:
    """A checked native JAG archive, including both compression modes; author lare96."""

    def __init__(self, payload: bytes, label: str):
        self.label = label
        cursor = Reader(payload, label)
        unpacked, packed = cursor.u24(), cursor.u24()
        body = cursor.take(packed)
        cursor.finish()
        whole_compressed = unpacked != packed
        if whole_compressed:
            body = _bzip(body, unpacked, label)
        elif len(body) != unpacked:
            raise CacheError(f"{label}: invalid uncompressed archive size")
        table = Reader(body, label)
        count = table.u16()
        rows = []
        for _ in range(count):
            hash_value = int.from_bytes(table.take(4), "big")
            original, stored = table.u24(), table.u24()
            rows.append((hash_value, original, stored))
        self.files = {}
        for hash_value, original, stored in rows:
            if hash_value in self.files:
                raise CacheError(f"{label}: duplicate archive member {hash_value}")
            encoded = table.take(stored)
            if whole_compressed:
                if original != stored:
                    raise CacheError(f"{label}: whole-compressed member has unequal sizes")
                raw = encoded
            else:
                raw = _bzip(encoded, original, f"{label}, member {hash_value}")
            self.files[hash_value] = raw
        table.finish()

    def get(self, name: str) -> bytes:
        try:
            return self.files[name_hash(name)]
        except KeyError as error:
            raise CacheError(f"{self.label}: missing required archive member {name}") from error


class Snapshot:
    """Immutable six-store capture with verified native sector chains; author lare96."""

    def __init__(self, cache_path: str | Path):
        self.path = Path(cache_path).resolve(strict=True)
        if not self.path.is_dir():
            raise CacheError("The source cache must be a directory")
        for path in self.path.iterdir():
            if path.name.startswith("main_file_cache.") and path.name not in STORE_NAMES:
                raise CacheError(f"Unsupported native cache store: {path.name}")
        before = self.hash_sources()
        self.files = {name: self._read_store(name) for name in STORE_NAMES}
        self.fingerprints = {
            name: hashlib.sha256(data).hexdigest() for name, data in self.files.items()
        }
        after = self.hash_sources()
        if before != self.fingerprints or after != self.fingerprints:
            raise CacheError("The source cache changed during its read-only capture")
        self.data = self.files[STORE_NAMES[0]]
        if len(self.data) < 520:
            raise CacheError("Native data store does not reserve sector zero")
        self.occupied = []
        for index in range(5):
            table = self.files[STORE_NAMES[index + 1]]
            if len(table) % 6:
                raise CacheError(f"Index {index}: incomplete six-byte row")
            ids = []
            for file_id in range(len(table) // 6):
                at = file_id * 6
                length = int.from_bytes(table[at:at + 3], "big")
                sector = int.from_bytes(table[at + 3:at + 6], "big")
                if not length and not sector:
                    continue
                if not length or not sector:
                    raise CacheError(f"Index {index}, file {file_id}: half-empty index row")
                self.file(index, file_id)
                ids.append(file_id)
            self.occupied.append(tuple(ids))

    def _read_store(self, name: str) -> bytes:
        path = self.path / name
        if path.is_symlink() or not path.is_file():
            raise CacheError(f"Missing regular native cache store: {path}")
        maximum = MAX_DATA_BYTES if name.endswith(".dat") else 65536 * 6
        if path.stat().st_size > maximum:
            raise CacheError(f"Native cache store exceeds supported size: {name}")
        with path.open("rb") as handle:
            data = handle.read(maximum + 1)
        if len(data) > maximum:
            raise CacheError(f"Native cache store grew beyond supported size: {name}")
        return data

    def hash_sources(self) -> dict[str, str]:
        return {
            name: hashlib.sha256(self._read_store(name)).hexdigest() for name in STORE_NAMES
        }

    def file(self, index: int, file_id: int) -> bytes:
        if not 0 <= index < 5 or not 0 <= file_id < 65536:
            raise CacheError(f"Invalid native file address: index {index}, file {file_id}")
        table = self.files[STORE_NAMES[index + 1]]
        at = file_id * 6
        if at + 6 > len(table):
            raise CacheError(f"Index {index}, file {file_id}: missing index row")
        length = int.from_bytes(table[at:at + 3], "big")
        sector = int.from_bytes(table[at + 3:at + 6], "big")
        if not 0 < length <= MAX_FILE_BYTES:
            raise CacheError(f"Index {index}, file {file_id}: missing/oversized file")
        chunks = []
        seen = set()
        part = 0
        remaining = length
        while remaining:
            if not sector or sector in seen:
                raise CacheError(f"Index {index}, file {file_id}: broken/cyclic sector chain")
            seen.add(sector)
            at = sector * 520
            take = min(remaining, 512)
            if at + 8 + take > len(self.data):
                raise CacheError(f"Index {index}, file {file_id}: sector outside source data")
            owner, sequence = struct.unpack_from(">HH", self.data, at)
            following = int.from_bytes(self.data[at + 4:at + 7], "big")
            if owner != file_id or sequence != part or self.data[at + 7] != index + 1:
                raise CacheError(
                    f"Index {index}, file {file_id}: incorrect header at sector {sector}"
                )
            chunks.append(self.data[at + 8:at + 8 + take])
            remaining -= take
            sector = following
            part += 1
        if sector:
            raise CacheError(f"Index {index}, file {file_id}: chain exceeds declared length")
        return b"".join(chunks)

    def archive(self, file_id: int) -> Archive:
        return Archive(self.file(0, file_id), f"Archive {file_id}")


def _optional(value: int) -> int | None:
    return None if value == 65535 else value


def decode_object_definition(object_id: int, payload: bytes) -> dict:
    cursor = Reader(payload, f"Object {object_id}")
    value = {
        "id": object_id, "name": "null", "description": None,
        "width": 1, "length": 1, "solid": True, "impenetrable": True,
        "interactive": False, "actions": [None] * 5, "rawActions": [None] * 9,
        "morph": None, "hollow": False, "obstructive": False,
        "models": [], "lowMemoryModels": [], "animation": None, "rawAnimation": None,
        "interactionOverride": None, "mapIcon": None, "mapScene": None,
    }
    model_groups = []
    saw_action = False
    native_explicit_true = False
    while True:
        opcode = cursor.u8()
        if opcode == 0:
            cursor.finish()
            break
        if opcode in (1, 5):
            models = []
            for _ in range(cursor.u8()):
                model = cursor.u16()
                kind = cursor.u8() if opcode == 1 else 10
                models.append({"id": model, "type": kind})
            if models:
                model_groups.append(models)
        elif opcode in (2, 3):
            value["name" if opcode == 2 else "description"] = cursor.text()
        elif opcode in (14, 15):
            value["width" if opcode == 14 else "length"] = cursor.u8()
        elif opcode == 17:
            value["solid"] = False
        elif opcode == 18:
            value["impenetrable"] = False
        elif opcode == 19:
            override = cursor.u8()
            value["interactionOverride"] = override
            value["interactive"] = override != 0
            native_explicit_true = native_explicit_true or override == 1
        elif opcode in (21, 22, 23, 62, 64):
            # Rendering-only native flags, consumed without inventing server behavior.
            continue
        elif opcode == 24:
            value["rawAnimation"] = cursor.u16()
            value["animation"] = _optional(value["rawAnimation"])
        elif opcode in (28, 29, 39, 69, 75):
            cursor.u8()
        elif 30 <= opcode <= 38:
            text = cursor.text()
            value["rawActions"][opcode - 30] = text
            if opcode >= 35:
                raise CacheError(f"Object {object_id}: native-unsafe action slot {opcode}")
            value["actions"][opcode - 30] = None if text.lower() == "hidden" else text
            saw_action = True
        elif opcode == 40:
            cursor.take(cursor.u8() * 4)
        elif opcode in (60, 68):
            value["mapIcon" if opcode == 60 else "mapScene"] = cursor.u16()
        elif opcode in (65, 66, 67, 70, 71, 72):
            cursor.u16()
        elif opcode == 73:
            value["obstructive"] = True
        elif opcode == 74:
            value["hollow"] = True
        elif opcode == 77:
            bit, varp, count = cursor.u16(), cursor.u16(), cursor.u8() + 1
            value["morph"] = {
                "varbitId": _optional(bit), "varpId": _optional(varp),
                "children": [_optional(cursor.u16()) for _ in range(count)],
                "serverSignedCountCompatible": count <= 128,
            }
        else:
            raise CacheError(f"Object {object_id}: unknown opcode {opcode} at {cursor.at - 1}")
    if value["width"] < 1 or value["length"] < 1:
        raise CacheError(f"Object {object_id}: nonpositive native footprint")
    if model_groups:
        value["models"], value["lowMemoryModels"] = model_groups[0], model_groups[-1]
    automatic = bool(value["models"] and value["models"][0]["type"] == 10) or saw_action
    value["nativeInteractive"] = (
        automatic if value["interactionOverride"] is None else native_explicit_true
    )
    value["nativeSolid"] = value["solid"] and not value["hollow"]
    value["nativeImpenetrable"] = value["impenetrable"] and not value["hollow"]
    value["interactive"] = value["nativeInteractive"]
    value["solid"] = value["nativeSolid"]
    value["impenetrable"] = value["nativeImpenetrable"]
    value["serverAnimation"] = (
        value["rawAnimation"] if value["rawAnimation"] is not None
        else 497 if object_id in (2282, 2288) else None
    )
    return value


def decode_definitions(archive: Archive) -> dict[int, dict]:
    index = Reader(archive.get("loc.idx"), "loc.idx")
    count = index.u16()
    lengths = [index.u16() for _ in range(count)]
    index.finish()
    data = Reader(archive.get("loc.dat"), "loc.dat")
    if data.u16() != count:
        raise CacheError("loc.dat/loc.idx object counts disagree")
    values = {
        object_id: decode_object_definition(object_id, data.take(length))
        for object_id, length in enumerate(lengths)
    }
    data.finish()
    for value in values.values():
        if value["morph"]:
            for child in value["morph"]["children"]:
                if child is not None and child not in values:
                    raise CacheError(f"Object {value['id']}: missing morph child {child}")
    return values


def decode_floors(archive: Archive) -> list[dict]:
    cursor = Reader(archive.get("flo.dat"), "flo.dat")
    count = cursor.u16()
    values = []
    for floor_id in range(count):
        value = {"id": floor_id, "rgb": 0, "texture": -1, "name": None,
                 "alternateRgb": None, "flag3": False, "occludes": True}
        while True:
            opcode = cursor.u8()
            if opcode == 0:
                break
            if opcode in (1, 7):
                value["rgb" if opcode == 1 else "alternateRgb"] = cursor.u24()
            elif opcode == 2:
                value["texture"] = cursor.u8()
            elif opcode == 3:
                value["flag3"] = True
            elif opcode == 5:
                value["occludes"] = False
            elif opcode == 6:
                value["name"] = cursor.text()
            else:
                raise CacheError(f"Floor {floor_id}: unknown opcode {opcode}")
        values.append(value)
    cursor.finish()
    return values


def decode_terrain(payload: bytes, region_id: int) -> dict[str, bytearray]:
    cursor = Reader(payload, f"Region {region_id} terrain")
    fields = {key: bytearray(REGION_TILES) for key in
              ("flags", "underlay", "overlay", "shape", "rotation")}
    flags, underlay, overlay, shape, rotation = fields.values()
    for tile in range(REGION_TILES):
        while True:
            opcode = cursor.u8()
            if opcode == 0:
                break
            if opcode == 1:
                cursor.u8()
                break
            if opcode <= 49:
                overlay[tile] = cursor.u8()
                shape[tile] = (opcode - 2) // 4
                rotation[tile] = (opcode - 2) & 3
            elif opcode <= 81:
                flags[tile] = opcode - 49
            else:
                underlay[tile] = opcode - 81
    cursor.finish()
    return fields


def decode_placements(payload: bytes, region: dict, definitions: dict[int, dict]) -> list[dict]:
    cursor = Reader(payload, f"Region {region['id']} locations")
    values = []
    object_id = -1
    while True:
        delta = cursor.smart()
        if delta == 0:
            break
        object_id += delta
        if object_id not in definitions:
            raise CacheError(f"Region {region['id']}: missing object definition {object_id}")
        packed = 0
        while True:
            delta = cursor.smart()
            if delta == 0:
                break
            packed += delta - 1
            if packed >= REGION_TILES:
                raise CacheError(f"Region {region['id']}: location outside four native planes")
            local_x, local_y, raw_plane = (packed >> 6) & 63, packed & 63, packed >> 12
            info = cursor.u8()
            kind, rotation = info >> 2, info & 3
            if kind > 22:
                raise CacheError(f"Region {region['id']}: unsupported placement type {kind}")
            plane = 0 if raw_plane == 1 and object_id in SERVER_PLANE_CORRECTIONS else raw_plane
            bridged = bool(region["flags"][4096 + local_x * 64 + local_y] & 2)
            values.append({
                "id": object_id, "x": region["rx"] * 64 + local_x,
                "y": region["ry"] * 64 + local_y, "plane": plane,
                "rawPlane": raw_plane, "effectivePlane": plane - int(bridged),
                "type": kind, "rotation": rotation,
            })
    cursor.finish()
    return values


class LazyPlacements(Sequence):
    """Validated placements materialized on demand with 32-region retention.

    The native raw/corrected/effective-plane semantics are retained. Validation
    and summary counters do not allocate a dictionary for every world object.

    @author lare96
    """

    __slots__ = ("_raw", "_base_x", "_base_y", "_flags", "_key", "_cache", "_lock",
                 "server_corrections", "bridge_lowered")

    def __init__(self, raw, region, definitions, cache=None, lock=None):
        self._raw = raw
        self._base_x, self._base_y = region["rx"] * 64, region["ry"] * 64
        self._flags = region["flags"]
        self._key = region["id"]
        self._cache = cache if cache is not None else OrderedDict()
        self._lock = lock if lock is not None else threading.RLock()
        corrections = lowered = 0
        for object_id, packed, info in PLACEMENT_RECORD.iter_unpack(raw):
            if object_id not in definitions or packed >= REGION_TILES or info >> 2 > 22:
                raise CacheError(f"Region {region['id']}: invalid decoded cache placement")
            corrections += packed >> 12 == 1 and object_id in SERVER_PLANE_CORRECTIONS
            lowered += bool(self._flags[4096 + (packed & 4095)] & 2)
        self.server_corrections, self.bridge_lowered = corrections, lowered

    def __len__(self):
        return len(self._raw) // PLACEMENT_RECORD.size

    def _materialize(self):
        with self._lock:
            if self._key not in self._cache:
                values = []
                for object_id, packed, info in PLACEMENT_RECORD.iter_unpack(self._raw):
                    local_x, local_y, raw_plane = (packed >> 6) & 63, packed & 63, packed >> 12
                    plane = 0 if raw_plane == 1 and object_id in SERVER_PLANE_CORRECTIONS else raw_plane
                    bridged = bool(self._flags[4096 + (packed & 4095)] & 2)
                    values.append({
                        "id": object_id, "x": self._base_x + local_x, "y": self._base_y + local_y,
                        "plane": plane, "rawPlane": raw_plane, "effectivePlane": plane - int(bridged),
                        "type": info >> 2, "rotation": info & 3,
                    })
                self._cache[self._key] = values
                if len(self._cache) > 32:
                    self._cache.popitem(last=False)
            self._cache.move_to_end(self._key)
            return self._cache[self._key]

    def __iter__(self):
        return iter(self._materialize())

    def __getitem__(self, index):
        return self._materialize()[index]


class World:
    """A fully decoded immutable-source world, with native provenance; author lare96."""

    def __init__(self, cache_path: str | Path, cache_dir: str | Path | None = None):
        started = time.perf_counter()
        self.snapshot = Snapshot(cache_path)
        self.cache_path = str(self.snapshot.path)
        self.fingerprints = dict(self.snapshot.fingerprints)
        self._tile_indexes = OrderedDict()
        self._tile_lock = threading.RLock()
        self._placement_cache = OrderedDict()
        self._placement_lock = threading.RLock()
        config = self.snapshot.archive(2)
        self.definitions = decode_definitions(config)
        self.floors = decode_floors(config)
        update = self.snapshot.archive(5)
        self._versions = update.get("map_version")
        self._crcs = update.get("map_crc")
        if len(self._versions) % 2 or len(self._crcs) % 4:
            raise CacheError("Incomplete native map update metadata")
        index = Reader(update.get("map_index"), "map_index")
        if len(index.data) % 7:
            raise CacheError("map_index requires complete native seven-byte rows")
        rows = {}
        while index.at < len(index.data):
            region_id, terrain_id, locations_id = index.u16(), index.u16(), index.u16()
            priority = index.u8()
            if region_id in rows:
                raise CacheError(f"Duplicate map_index region {region_id}")
            rows[region_id] = (terrain_id, locations_id, priority)
        index.finish()
        artifact, cache_key = self._cache_address(cache_dir)
        used = False
        cache_status = "disabled" if artifact is None else "miss"
        if artifact is not None and artifact.exists():
            try:
                self.regions = self._read_warm(artifact, cache_key, rows)
                self.summary = self._summarize()
                used, cache_status = True, "hit"
            except (CacheError, OSError, ValueError, KeyError, TypeError,
                    zipfile.BadZipFile, zlib.error, EOFError) as error:
                cache_status = "rejected: " + str(error)[:180]
        if not used:
            self.regions = {}
            for region_id, (terrain_id, locations_id, priority) in rows.items():
                region = self._region(region_id, terrain_id, locations_id, priority)
                region.update(decode_terrain(self.map_payload(terrain_id), region_id))
                region["objects"] = decode_placements(
                    self.map_payload(locations_id), region, self.definitions
                )
                self.regions[region_id] = region
            self.summary = self._summarize()
            if artifact is not None:
                try:
                    self._write_warm(artifact, cache_key)
                    cache_status += "; written"
                except (OSError, ValueError, zipfile.BadZipFile) as error:
                    # A read-only/unavailable optional workspace must not prevent
                    # a valid strict source decode from opening.
                    cache_status += "; write unavailable: " + str(error)[:120]
        self.summary.update({"warmCacheUsed": used, "warmCacheStatus": cache_status,
                             "loadSeconds": round(time.perf_counter() - started, 3),
                             "warmCacheSchema": CACHE_SCHEMA,
                             "placements": "lazy by region" if used else "native eager decode"})

    @staticmethod
    def _region(region_id, terrain_id, locations_id, priority):
        return {
            "id": region_id, "rx": region_id >> 8, "ry": region_id & 255,
            "terrainFile": terrain_id, "objectFile": locations_id, "indexFlag": priority,
        }

    def _summarize(self):
        totals = Counter()
        for region_id, region in self.regions.items():
            underlays, overlays, flags = (Counter(region[key]) for key in
                                          ("underlay", "overlay", "flags"))
            if (max(underlays) > min(174, len(self.floors))
                    or max(overlays) > len(self.floors) or max(flags) > 32
                    or max(region["shape"]) > 11 or max(region["rotation"]) > 3):
                raise CacheError(f"Region {region_id}: unsupported terrain operand/reference")
            totals["underlayReferences"] += REGION_TILES - underlays[0]
            totals["overlayReferences"] += REGION_TILES - overlays[0]
            totals["objects"] += len(region["objects"])
            totals["tiles"] += REGION_TILES
            totals["waterTiles"] += overlays[6]
            blocked = sum(count for value, count in flags.items() if value & 1)
            totals["blockedTerrainTiles"] += blocked
            totals["serverBlockedTerrainTiles"] += blocked
            totals["bridgeFlagTiles"] += sum(count for value, count in flags.items() if value & 2)
            totals["roofFlagTiles"] += sum(count for value, count in flags.items() if value & 4)
            if isinstance(region["objects"], LazyPlacements):
                totals["serverPlaneCorrections"] += region["objects"].server_corrections
                totals["bridgeLoweredObjects"] += region["objects"].bridge_lowered
            else:
                totals["serverPlaneCorrections"] += sum(
                    obj["plane"] != obj["rawPlane"] for obj in region["objects"]
                )
                totals["bridgeLoweredObjects"] += sum(
                    obj["plane"] != obj["effectivePlane"] for obj in region["objects"]
                )
        return {
            "regions": len(self.regions), "definitions": len(self.definitions),
            "floors": len(self.floors), **dict(totals),
            "occupiedFiles": [len(ids) for ids in self.snapshot.occupied],
            "mapFilesVerified": len(self.regions) * 2,
            "definitionSemantics": "server and native fields share derived interaction and hollow defaults",
            "heightCoverage": "native height operands consumed; derived heights not exposed",
            "runtimeCoverage": "static source cache only; server runtime overrides not captured",
        }

    def _cache_address(self, directory):
        if directory is None:
            return None, None
        root = Path(directory).resolve()
        if root == self.snapshot.path or self.snapshot.path in root.parents:
            raise CacheError("Decoded cache workspace must be outside the source cache")
        self._decoder_sha = DECODER_SHA
        identity = json.dumps({"schema": CACHE_SCHEMA, "decoder": self._decoder_sha,
                               "stores": self.fingerprints}, sort_keys=True).encode()
        key = hashlib.sha256(identity).hexdigest()
        return root / ("world-" + key + ".zip"), key

    def _read_warm(self, path, key, rows):
        if path.is_symlink() or path.stat().st_size > 128 * 1024 * 1024:
            raise CacheError("Unsupported decoded cache artifact")
        with zipfile.ZipFile(path, "r") as archive:
            infos = archive.infolist()
            names = {entry.filename for entry in infos}
            expected = {"manifest.json"} | {f"regions/{region_id}.bin" for region_id in rows}
            if len(infos) != len(names) or names != expected:
                raise CacheError("Decoded cache region inventory differs from native map_index")
            if archive.getinfo("manifest.json").file_size > 128 * 1024:
                raise CacheError("Decoded cache manifest exceeds its bound")
            manifest = json.loads(archive.read("manifest.json"))
            if (not isinstance(manifest, dict) or manifest.get("schema") != CACHE_SCHEMA
                    or manifest.get("key") != key or manifest.get("decoder") != self._decoder_sha
                    or manifest.get("stores") != self.fingerprints
                    or not isinstance(manifest.get("objects"), dict)
                    or set(manifest["objects"]) != {str(region_id) for region_id in rows}):
                raise CacheError("Decoded cache baseline/schema does not match this source")
            result = {}
            for region_id, (terrain_id, locations_id, priority) in rows.items():
                count = manifest["objects"][str(region_id)]
                if type(count) is not int or not 0 <= count < MAX_MAP_BYTES // 2:
                    raise CacheError("Decoded cache object count exceeds native stream bounds")
                # Recheck actual source update/gzip integrity on warm opens too.
                # The compact artifact skips tile-opcode parsing, not source
                # sector/version/CRC checks. Native bytes also bound allocations.
                self.map_payload(terrain_id)
                if count > len(self.map_payload(locations_id)) // 2:
                    raise CacheError("Decoded cache object count exceeds its native source payload")
                name = f"regions/{region_id}.bin"
                size = 5 * REGION_TILES + count * PLACEMENT_RECORD.size
                if archive.getinfo(name).file_size != size:
                    raise CacheError(f"Region {region_id}: decoded cache size disagrees")
                raw = archive.read(name)  # ZipFile verifies the complete entry CRC.
                value = self._region(region_id, terrain_id, locations_id, priority)
                for at, field in enumerate(("flags", "underlay", "overlay", "shape", "rotation")):
                    value[field] = bytearray(raw[at * REGION_TILES:(at + 1) * REGION_TILES])
                value["objects"] = LazyPlacements(raw[5 * REGION_TILES:], value, self.definitions,
                                                   self._placement_cache, self._placement_lock)
                result[region_id] = value
        return result

    def _write_warm(self, path, key):
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.is_symlink():
            raise CacheError("Decoded cache artifacts must not be links")
        temporary = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
        manifest = {"schema": CACHE_SCHEMA, "key": key, "decoder": self._decoder_sha,
                    "stores": self.fingerprints,
                    "objects": {str(region_id): len(value["objects"])
                                for region_id, value in self.regions.items()}}
        try:
            with temporary.open("xb") as output:
                with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED,
                                     compresslevel=1) as archive:
                    archive.writestr("manifest.json", json.dumps(manifest, separators=(",", ":")))
                    for region_id, value in self.regions.items():
                        data = bytearray()
                        for field in ("flags", "underlay", "overlay", "shape", "rotation"):
                            data.extend(value[field])
                        for obj in value["objects"]:
                            packed = obj["rawPlane"] * 4096 + (obj["x"] & 63) * 64 + (obj["y"] & 63)
                            data.extend(PLACEMENT_RECORD.pack(obj["id"], packed,
                                                              obj["type"] * 4 + obj["rotation"]))
                        archive.writestr(f"regions/{region_id}.bin", data)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)

    def map_payload(self, file_id: int) -> bytes:
        if (file_id >= len(self._versions) // 2 or file_id >= len(self._crcs) // 4
                or file_id < 0):
            raise CacheError(f"Map file {file_id}: missing native update metadata")
        version = int.from_bytes(self._versions[file_id * 2:file_id * 2 + 2], "big")
        if not version:
            raise CacheError(f"Map file {file_id}: disabled native update entry")
        raw = self.snapshot.file(4, file_id)
        if len(raw) < 3 or int.from_bytes(raw[-2:], "big") != version:
            raise CacheError(f"Map file {file_id}: invalid native version trailer")
        compressed = raw[:-2]
        expected_crc = int.from_bytes(self._crcs[file_id * 4:file_id * 4 + 4], "big")
        if zlib.crc32(compressed) & 0xFFFFFFFF != expected_crc:
            raise CacheError(f"Map file {file_id}: invalid native compressed-payload CRC")
        try:
            decoder = zlib.decompressobj(wbits=31)
            value = decoder.decompress(compressed, MAX_MAP_BYTES)
            if (len(value) >= MAX_MAP_BYTES or not decoder.eof or decoder.unused_data
                    or decoder.unconsumed_tail):
                raise CacheError(f"Map file {file_id}: unsupported gzip boundary/size")
            return value
        except zlib.error as error:
            raise CacheError(f"Map file {file_id}: corrupt GZIP stream: {error}") from error

    def _object_index(self, region: dict) -> dict:
        with self._tile_lock:
            key = region["id"]
            if key in self._tile_indexes:
                self._tile_indexes.move_to_end(key)
                return self._tile_indexes[key]
            index = {"map": {}, "raw": {}, "effective": {}}
            for obj in region["objects"]:
                xy = (obj["x"] & 63) * 64 + (obj["y"] & 63)
                for mode, field in (("map", "plane"), ("raw", "rawPlane"),
                                    ("effective", "effectivePlane")):
                    index[mode].setdefault(obj[field] * 4096 + xy, []).append(obj)
            self._tile_indexes[key] = index
            if len(self._tile_indexes) > 16:
                self._tile_indexes.popitem(last=False)
            return index

    def tile(self, x: int, y: int, plane: int) -> dict | None:
        if not (isinstance(x, int) and isinstance(y, int) and isinstance(plane, int)):
            raise ValueError("Tile coordinates and plane must be integers")
        if not 0 <= plane < 4 or not 0 <= x < 16384 or not 0 <= y < 16384:
            return None
        region = self.regions.get(((x >> 6) << 8) | (y >> 6))
        if region is None:
            return None
        local = (x & 63) * 64 + (y & 63)
        at = plane * 4096 + local
        values = {key: region[key][at] for key in
                  ("flags", "underlay", "overlay", "shape", "rotation")}
        index = self._object_index(region)
        values.update({
            "x": x, "y": y, "plane": plane, "region": region["id"],
            "blocked": bool(values["flags"] & 1),
            "roof": bool(values["flags"] & 4),
            "bridge": bool(values["flags"] & 2),
            "bridgeColumn": bool(region["flags"][4096 + local] & 2),
            "objects": list(index["map"].get(at, ())),
            "rawObjects": list(index["raw"].get(at, ())),
            "effectiveObjects": list(index["effective"].get(at, ())),
            "terrainSemantics": "raw native map plane; blocked uses the native terrain flag",
        })
        return values

    def assert_sources_unchanged(self) -> None:
        if self.snapshot.hash_sources() != self.fingerprints:
            raise CacheError("Source cache differs from this atlas snapshot; reopen the atlas")
