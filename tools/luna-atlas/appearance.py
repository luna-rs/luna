"""Deterministic native 377 overview colours and cache scenery sprites.

The native client randomizes palette, floor and sprite colours at startup. This
overview deliberately keeps those variations fixed so neighbouring exports and
cache comparisons remain stable. No geometry, coordinates or cache bytes change.

@author lare96
"""
from dataclasses import dataclass
from functools import lru_cache
from PIL import Image

from cache377 import CacheError, Reader


def trunc_div(value, divisor):
    """Java integer division, including native negative hue intermediates."""
    return int(value / divisor)


def gamma_colour(rgb, exponent):
    channels = [min(255, int((((rgb >> shift) & 255) / 256.0) ** exponent * 256.0))
                for shift in (16, 8, 0)]
    return (channels[0] << 16) | (channels[1] << 8) | channels[2]


def rgb_tuple(rgb):
    return ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255)


def native_hsl(rgb):
    """TileDefinition.method209 before its random per-launch colour offsets."""
    if rgb == 0xff00ff:
        rgb = 0
    red, green, blue = (((rgb >> n) & 255) / 256.0 for n in (16, 8, 0))
    low, high = min(red, green, blue), max(red, green, blue)
    hue = saturation = 0.0
    light = (low + high) / 2.0
    if low != high:
        saturation = ((high - low) / (high + low) if light < .5
                      else (high - low) / (2.0 - high - low))
        if red == high:
            hue = (green - blue) / (high - low)
        elif green == high:
            hue = 2.0 + (blue - red) / (high - low)
        else:
            hue = 4.0 + (red - green) / (high - low)
    hue /= 6.0
    weight = max(1, int(((1.0 - light) if light > .5 else light) * saturation * 512.0))
    return (int(hue * 256), max(0, min(255, int(saturation * 256))),
            max(0, min(255, int(light * 256))), int(hue * weight), weight)


def packed_hsl(hue, saturation, light):
    for threshold in (179, 192, 217, 243):
        if light > threshold:
            saturation = trunc_div(saturation, 2)
    return (trunc_div(hue, 4) << 10) + (trunc_div(saturation, 32) << 7) + trunc_div(light, 2)


def lit_hsl(packed, brightness=96):
    light = max(2, min(126, trunc_div(brightness * (packed & 127), 128)))
    return (packed & 0xff80) + light


@lru_cache(maxsize=2)
def native_palette(brightness=.8):
    """ThreeDimensionalCanvas.initColorTable without random gamma jitter."""
    colours = []
    for group in range(512):
        hue, saturation = (group // 8) / 64.0 + .0078125, (group & 7) / 8.0 + .0625
        for lightness in range(128):
            light = lightness / 128.0
            upper = light * (1.0 + saturation) if light < .5 else light + saturation - light * saturation
            lower = 2.0 * light - upper
            channels = []
            for shifted in (hue + 1.0 / 3, hue, hue - 1.0 / 3):
                shifted %= 1.0
                value = (lower + (upper - lower) * 6.0 * shifted if 6 * shifted < 1
                         else upper if 2 * shifted < 1
                         else lower + (upper - lower) * (2.0 / 3 - shifted) * 6.0 if 3 * shifted < 2
                         else lower)
                channels.append(int(value * 256))
            rgb = (channels[0] << 16) + (channels[1] << 8) + channels[2]
            colours.append(rgb_tuple(gamma_colour(rgb, brightness) or 1))
    return tuple(colours)


@dataclass(frozen=True)
class IndexedFrame:
    """One native indexed frame, retaining transparent index and draw offsets.

    @author lare96
    """
    width: int
    height: int
    offset_x: int
    offset_y: int
    canvas_width: int
    canvas_height: int
    indices: bytes
    palette: tuple

    def image(self):
        pixels = bytearray(self.width * self.height * 4)
        for position, index in enumerate(self.indices):
            rgb = self.palette[index]
            pixels[position * 4:position * 4 + 4] = bytes((rgb >> 16, (rgb >> 8) & 255, rgb & 255,
                                                         255 if index else 0))
        return Image.frombytes('RGBA', (self.width, self.height), bytes(pixels))


def indexed_frames(archive, name, maximum=100):
    """Decode the native IndexedSprite stream, with strict size/layout limits."""
    data = Reader(archive.get(name + '.dat'), name + '.dat')
    index = Reader(archive.get('index.dat'), name + ' index.dat')
    index.at = data.u16()
    canvas_width, canvas_height, palette_size = index.u16(), index.u16(), index.u8()
    if not 1 <= canvas_width <= 512 or not 1 <= canvas_height <= 512 or not 1 <= palette_size <= 256:
        raise CacheError(name + ': invalid native sprite canvas/palette')
    palette = (0,) + tuple(index.u24() for _ in range(palette_size - 1))
    frames = []
    while data.at < len(data.data):
        if len(frames) >= maximum:
            raise CacheError(name + ': frame count exceeds native consumer limit')
        offset_x, offset_y, width, height, layout = index.u8(), index.u8(), index.u16(), index.u16(), index.u8()
        if not width or not height or width + offset_x > canvas_width or height + offset_y > canvas_height:
            raise CacheError(name + ': frame dimensions cross the native canvas')
        if layout not in (0, 1):
            raise CacheError(name + ': unsupported sprite layout ' + str(layout))
        values = data.take(width * height)
        if any(value >= palette_size for value in values):
            raise CacheError(name + ': sprite palette reference is out of bounds')
        if layout == 1:
            values = bytes(values[x * height + y] for y in range(height) for x in range(width))
        frames.append(IndexedFrame(width, height, offset_x, offset_y, canvas_width, canvas_height,
                                   values, palette))
    data.finish()
    return tuple(frames)


class NativeAppearance:
    """Read-only floor palette, texture averages and native cache map scenes.

    @author lare96
    """

    def __init__(self, world):
        self.world = world
        self.palette = native_palette()
        self.diagnostics = []
        self.floors = []
        self.overlay_hsl = []
        for floor in world.floors:
            hue, saturation, light, chroma, weight = native_hsl(floor.get('rgb', 0))
            # The selected native loader restores anInt324 from old anInt323 in
            # opcode7. Retain that actual behavior, rather than normalizing it.
            if floor.get('alternateRgb') is not None:
                weight = chroma
            self.floors.append((hue, saturation, light, chroma, weight))
            overlay = native_hsl(floor.get('alternateRgb') if floor.get('alternateRgb') is not None else floor.get('rgb', 0))
            self.overlay_hsl.append(packed_hsl(max(0,min(255,overlay[0])),overlay[1],overlay[2]))
        self._load_floor_operands()
        self.scenes = {}
        self.texture_colours = {}
        self._underlays = lru_cache(maxsize=48)(self._underlay_colours)
        self._load_assets()
        self.metadata = {'revision':377, 'style':'Native 377 minimap colours and scenery',
                         'worldData':'Selected Luna cache', 'referenceImagery':False,
                         'floorBlending':'Native 10 by 10 HSL neighbourhood, including region neighbours',
                         'mapSceneSprites':len(self.scenes), 'textureAverages':len(self.texture_colours),
                         'deterministic':True,
                         'limitations':['Client random colour variation is fixed for reproducible maps.',
                                        'Overview imagery does not reproduce full 3D scene lighting, heights or runtime morph state.']}

    def _load_floor_operands(self):
        snapshot = getattr(self.world, 'snapshot', None)
        if snapshot is None:
            return
        try:
            cursor=Reader(snapshot.archive(2).get('flo.dat'),'Native floor appearance')
            if cursor.u16()!=len(self.floors):
                raise CacheError('Floor appearance count differs from the selected definitions')
            values,packed=[],[]
            for _ in self.floors:
                current=(0,0,0,0,0)
                overlay=0
                while True:
                    opcode=cursor.u8()
                    if opcode==0:
                        break
                    if opcode in (1,7):
                        derived=native_hsl(cursor.u24())
                        overlay=packed_hsl(max(0,min(255,derived[0])),derived[1],derived[2])
                        if opcode==1:
                            current=derived
                        else:
                            current=current[:4]+(current[3],)
                    elif opcode==2:
                        cursor.u8()
                    elif opcode in (3,5):
                        pass
                    elif opcode==6:
                        cursor.text()
                    else:
                        raise CacheError('Unsupported native floor appearance operand '+str(opcode))
                values.append(current)
                packed.append(overlay)
            cursor.finish()
            self.floors,self.overlay_hsl=values,packed
        except (CacheError,ValueError) as error:
            self.diagnostics.append('Floor colour operand history unavailable; using decoded colours: '+str(error))

    def _load_assets(self):
        snapshot = getattr(self.world, 'snapshot', None)
        if snapshot is None:
            self.diagnostics.append('Native visual archives are unavailable; scenery uses geometric markers.')
            return
        try:
            for index, frame in enumerate(indexed_frames(snapshot.archive(4), 'mapscene')):
                self.scenes[index] = (frame.image().resize((frame.width * 2, frame.height * 2), Image.Resampling.NEAREST),
                                      frame.offset_x * 2, frame.offset_y * 2)
        except (CacheError, ValueError) as error:
            self.diagnostics.append('Map scenery sprites could not load: ' + str(error))
        texture_ids = sorted({floor.get('texture', -1) for floor in self.world.floors if floor.get('texture', -1) >= 0})
        if not texture_ids:
            return
        try:
            archive = snapshot.archive(6)
        except (CacheError, ValueError) as error:
            self.diagnostics.append('Texture colours could not load: ' + str(error))
            return
        for texture in texture_ids:
            try:
                frame = indexed_frames(archive, str(texture), maximum=1)[0]
                palette = [gamma_colour(rgb, .8) for rgb in frame.palette]
                palette = [1 if index and colour & 0xf8f8ff == 0 else colour for index,colour in enumerate(palette)]
                channels = [sum((rgb >> shift) & 255 for rgb in palette) // len(palette) for shift in (16, 8, 0)]
                colour = (channels[0] << 16) | (channels[1] << 8) | channels[2]
                self.texture_colours[texture] = rgb_tuple(gamma_colour(colour, 1.4) or 1)
            except (CacheError, ValueError, IndexError) as error:
                self.diagnostics.append('Texture ' + str(texture) + ' uses its floor colour: ' + str(error))

    def floor_colour(self, floor_id, overlay=False):
        if not 1 <= floor_id <= len(self.world.floors):
            return (35, 40, 36)
        floor = self.world.floors[floor_id - 1]
        if overlay and floor.get('texture', -1) in self.texture_colours:
            return self.texture_colours[floor['texture']]
        colour = floor.get('rgb', 0)
        if colour == 0xff00ff:
            colour = floor.get('alternateRgb')
            if colour is None:
                return None
        elif overlay and floor.get('alternateRgb') is not None:
            colour = floor['alternateRgb']
        if overlay:
            return self.palette[lit_hsl(self.overlay_hsl[floor_id-1]) & 65535]
        hue, saturation, light, _, _ = native_hsl(colour)
        return self.palette[lit_hsl(packed_hsl(max(0, min(255, hue)), saturation, light)) & 65535]

    def _underlay_colours(self, raw_plane, rx, ry):
        selected=self.world.regions.get((rx<<8)|ry)
        if selected is None or not any(selected['underlay'][raw_plane*4096:(raw_plane+1)*4096]):
            return (None,)*4096
        # Region's sliding sums add x+5 and remove x-5 before painting: the
        # active window is x-4..x+5 (ten tiles), similarly for y.
        size, stride = 73, 74
        sums = [[0] * (stride * stride) for _ in range(5)]
        for x in range(size):
            world_x = rx * 64 + x - 4
            for y in range(size):
                world_y = ry * 64 + y - 4
                values = (0, 0, 0, 0, 0)
                if 0 <= world_x < 16384 and 0 <= world_y < 16384:
                    region = self.world.regions.get(((world_x >> 6) << 8) | (world_y >> 6))
                    if region:
                        floor_id = region['underlay'][raw_plane * 4096 + (world_x & 63) * 64 + (world_y & 63)]
                        if 1 <= floor_id <= len(self.floors):
                            _, saturation, light, chroma, weight = self.floors[floor_id - 1]
                            values = chroma, saturation, light, weight, 1
                pos = (x + 1) * stride + y + 1
                for total, value in zip(sums, values):
                    total[pos] = value + total[pos - stride] + total[pos - 1] - total[pos - stride - 1]
        colours = []
        for x in range(64):
            for y in range(64):
                a, b, c, d = x * stride + y, (x + 10) * stride + y, x * stride + y + 10, (x + 10) * stride + y + 10
                chroma, saturation, light, weight, count = (total[d] - total[b] - total[c] + total[a] for total in sums)
                if not count or weight <= 0:
                    colours.append(None)
                    continue
                hue = trunc_div(chroma * 256, weight) & 255
                packed = packed_hsl(hue, trunc_div(saturation, count), trunc_div(light, count))
                colours.append(self.palette[lit_hsl(packed) & 65535])
        return tuple(colours)

    def underlay_colours(self, raw_plane, rx, ry):
        return self._underlays(raw_plane, rx, ry)
