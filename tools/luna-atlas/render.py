"""Bounded cache-derived overview rendering. Author: lare96."""
from functools import lru_cache
from io import BytesIO
from pathlib import Path
import hashlib
import json
import os
import threading
import uuid
from PIL import Image, ImageDraw
import appearance as native_appearance
from appearance import NativeAppearance
from cache377 import DECODER_SHA

SCALE = 8
_RENDER_IDENTITY = hashlib.sha256(
    Path(__file__).read_bytes() + Path(native_appearance.__file__).read_bytes()
    + DECODER_SHA.encode()
).hexdigest()


class Renderer:
    """North-up region images using the same integer tiles as queries. Author: lare96."""

    def __init__(self, world, cache_dir=None):
        self.world = world
        self.max_extent = max(max(d['width'],d['length']) for d in world.definitions.values())
        self.appearance=NativeAppearance(world) if hasattr(world,'snapshot') else None
        self.metadata=(dict(self.appearance.metadata) if self.appearance else
                       {'revision':377,'style':'Geometric overview fallback','referenceImagery':False})
        self.diagnostics=list(self.appearance.diagnostics) if self.appearance else []
        self.cache_dir=None
        if cache_dir is not None:
            digest=hashlib.sha256((json.dumps(world.fingerprints,sort_keys=True)+_RENDER_IDENTITY).encode()).hexdigest()
            base=Path(cache_dir).resolve()
            self.cache_dir=(base/('native377-overview-v4-'+digest)).resolve()
            if not self.cache_dir.is_relative_to(base):
                raise ValueError('Rendered images must stay in the atlas cache directory.')
            self.cache_dir.mkdir(parents=True,exist_ok=True)
        self._locks={}
        self._guard=threading.Lock()
        self._cached_region=lru_cache(maxsize=192)(self._region_png)
        self.disk_hits=0
        self.rendered_regions=0
        self._floor_colours={}
        self._styles={}
        for ident,definition in world.definitions.items():
            name=definition['name'].lower()
            actions=[a.lower() for a in definition.get('actions',[]) if a]
            kind=('tree' if any('chop' in a for a in actions) or 'tree' in name
                  else 'rock' if 'mine' in actions or name in ('rocks','rock')
                  else 'bank' if 'bank' in name
                  else 'scenery' if definition.get('solid') and name!='null' else '')
            self._styles[ident]=(definition['width'],definition['length'],kind,
                                 (96,79,57) if 'dead' in name else (57,72,44),definition.get('mapScene'))

    def floor_colour(self, floor_id, overlay=False):
        key=(floor_id,overlay)
        if key not in self._floor_colours:
            self._floor_colours[key]=self._floor_colour(floor_id,overlay)
        return self._floor_colours[key]

    def _floor_colour(self, floor_id, overlay=False):
        if self.appearance:
            return self.appearance.floor_colour(floor_id,overlay)
        if overlay and floor_id == 6:
            return (38, 81, 119)
        if overlay and floor_id in (19, 152):
            return (185, 62, 25)
        if not 1 <= floor_id <= len(self.world.floors):
            return (29, 34, 32)
        floor = self.world.floors[floor_id - 1]
        colour = floor.get('rgb', 0)
        if colour == 0xff00ff:
            return None
        if colour == 0 and floor.get('texture', -1) >= 0:
            return (91, 86, 67)
        return tuple(max(0, int(((colour >> n) & 255) * .82)) for n in (16, 8, 0))

    def region_png(self, plane, rx, ry):
        if type(plane) is not int or type(rx) is not int or type(ry) is not int or not (0<=plane<=3 and 0<=rx<=255 and 0<=ry<=255):
            raise ValueError('Invalid plane or region coordinates.')
        if ((rx<<8)|ry) not in self.world.regions:
            raise ValueError('This region is absent from the selected cache.')
        key=(plane,rx,ry)
        with self._guard:
            lock=self._locks.setdefault(key,threading.Lock())
        # One render per missing region even when several windows request it.
        with lock:
            return self._cached_region(plane,rx,ry)

    def _region_png(self,plane,rx,ry):
        region = self.world.regions.get((rx << 8) | ry)
        if region is None:
            raise ValueError('This region is absent from the selected cache.')
        path=self.cache_dir/f'{plane}-{rx}-{ry}.png' if self.cache_dir else None
        if path is not None and path.is_file():
            try:
                with path.open('rb') as cached_file:
                    data=cached_file.read(2_000_001)
                if len(data)>2_000_000:
                    raise ValueError('Cached image exceeds the bounded region size.')
                with Image.open(BytesIO(data)) as cached:
                    if cached.size!=(512,512) or cached.format!='PNG':
                        raise ValueError('Invalid cached region image.')
                    cached.verify()
                self.disk_hits+=1
                return data
            except (OSError,ValueError,SyntaxError,Image.DecompressionBombError):
                pass
        image = Image.new('RGB', (64 * SCALE, 64 * SCALE), (23, 29, 30))
        draw = ImageDraw.Draw(image)
        underlays={plane:self.appearance.underlay_colours(plane,rx,ry)} if self.appearance else {}
        if self.appearance and plane<3 and any(flag & 2 for flag in region['flags'][4096:8192]):
            underlays[plane+1]=self.appearance.underlay_colours(plane+1,rx,ry)
        for x in range(64):
            for y in range(64):
                raw_plane = plane + bool(region['flags'][4096+x*64+y] & 2)
                if raw_plane > 3:
                    continue
                idx = raw_plane*4096+x*64+y
                underlay, overlay = region['underlay'][idx], region['overlay'][idx]
                left, top = x*SCALE, (63-y)*SCALE
                if underlay:
                    colour = (underlays[raw_plane][x*64+y] if self.appearance else None) or self.floor_colour(underlay)
                    if colour:
                        draw.rectangle((left, top, left+SCALE-1, top+SCALE-1), fill=colour)
                if overlay:
                    colour = self.floor_colour(overlay, True)
                    if colour:
                        # A shaped, rotated tile retains its native overlay footprint.
                        mask = overlay_mask(region['shape'][idx], region['rotation'][idx])
                        patch = Image.new('RGB', (SCALE, SCALE), colour)
                        image.paste(patch, (left, top), mask)
        # Include neighbouring origins so multi-tile scenery crosses region seams.
        objects = []
        margin=(self.max_extent+63)//64
        for nx in range(max(0, rx-margin), min(255,rx+1)+1):
            for ny in range(max(0, ry-margin), min(255,ry+1)+1):
                neighbour = self.world.regions.get((nx << 8) | ny)
                if neighbour:
                    objects.extend(neighbour['objects'])
        walls = []
        for obj in objects:
            if obj.get('effectivePlane', obj['plane']) != plane:
                continue
            typ, rotation = obj['type'], obj['rotation']
            width,length,kind,tree_colour,scene=self._styles[obj['id']]
            if rotation & 1:
                width, length = length, width
            left = (obj['x']-rx*64)*SCALE
            top = (ry*64+64-obj['y']-length)*SCALE
            right, bottom = left+width*SCALE-1, top+length*SCALE-1
            sprite=self.appearance.scenes.get(scene) if self.appearance and scene is not None else None
            if sprite and typ not in (4,5,6,7,8):
                bitmap,offset_x,offset_y=sprite
                sprite_x=left+(width*SCALE-bitmap.width)//2+offset_x
                sprite_y=top+(length*SCALE-bitmap.height)//2+offset_y
                if sprite_x+bitmap.width>0 and sprite_y+bitmap.height>0 and sprite_x<512 and sprite_y<512:
                    image.paste(bitmap,(sprite_x,sprite_y),bitmap)
                continue
            if right < 0 or bottom < 0 or left >= 512 or top >= 512:
                continue
            if typ in (0, 1, 2, 3, 9):
                walls.append(obj)
            elif kind=='tree':
                draw.ellipse((left, top, right, bottom), fill=tree_colour, outline=(24, 42, 26))
            elif kind=='rock':
                draw.ellipse((left+1, top+1, max(left+2,right-1), max(top+2,bottom-1)),
                             fill=(126, 121, 106), outline=(53, 52, 46))
            elif kind=='bank':
                draw.rectangle((left,top,right,bottom), fill=(181,148,60), outline=(65,53,30))
            elif typ in (10, 11) and kind=='scenery':
                draw.rectangle((left+1,top+1,max(left+1,right-1),max(top+1,bottom-1)),
                               outline=(123,111,89))
        for obj in walls:
            left, top = (obj['x']-rx*64)*SCALE, (ry*64+63-obj['y'])*SCALE
            edges = [(left,top,left,top+SCALE), (left,top,left+SCALE,top),
                     (left+SCALE,top,left+SCALE,top+SCALE), (left,top+SCALE,left+SCALE,top+SCALE)]
            rotation, typ = obj['rotation'], obj['type']
            colour = (183, 170, 142)
            if typ in (0, 2):
                draw.line(edges[rotation], fill=colour, width=2)
                if typ == 2:
                    draw.line(edges[(rotation+1)%4], fill=colour, width=2)
            elif typ == 9:
                line = (left,top+SCALE,left+SCALE,top) if rotation%2==0 else (left,top,left+SCALE,top+SCALE)
                draw.line(line, fill=colour, width=2)
        buffer = BytesIO()
        image.save(buffer, format='PNG')
        data=buffer.getvalue()
        self.rendered_regions+=1
        if path is not None:
            temporary=path.with_name(path.name+'.'+uuid.uuid4().hex+'.tmp')
            try:
                temporary.write_bytes(data)
                os.replace(temporary,path)
            except OSError:
                # A read-only/full preview cache must not prevent exploration.
                pass
            finally:
                temporary.unlink(missing_ok=True)
        return data


@lru_cache(maxsize=64)
def overlay_mask(shape, rotation):
    """Native shaped-tile triangles sampled at pixel centres, with north at top."""
    image = Image.new('L', (SCALE, SCALE), 0)
    # Shape zero is a full overlay. Other shapes use native SceneGraph vertex/faces.
    if shape == 0:
        image.paste(255, (0, 0, SCALE, SCALE))
        return image
    points = SHAPE_POINTS[shape + 1]
    faces = SHAPE_FACES[shape + 1]
    vertices = []
    coords = {1:(0,0),2:(64,0),3:(128,0),4:(128,64),5:(128,128),6:(64,128),
              7:(0,128),8:(0,64),9:(64,32),10:(96,64),11:(64,96),12:(32,64),
              13:(32,32),14:(96,32),15:(96,96),16:(32,96)}
    for point in points:
        if point <= 8 and point % 2 == 0:
            point = ((point - rotation*2 - 1) & 7) + 1
        elif 9 <= point <= 12:
            point = ((point - 9 - rotation) & 3) + 9
        elif 13 <= point <= 16:
            point = ((point - 13 - rotation) & 3) + 13
        vertices.append(coords[point])
    pixels = image.load()
    for i in range(0,len(faces),4):
        overlay, a,b,c = faces[i:i+4]
        if not overlay:
            continue
        indices = [((p-rotation)&3) if p<4 else p for p in (a,b,c)]
        (ax,ay),(bx,by),(cx,cy) = [vertices[p] for p in indices]
        area = (bx-ax)*(cy-ay)-(cx-ax)*(by-ay)
        if not area:
            continue
        for row in range(SCALE):
            y=(SCALE-row-.5)/SCALE*128
            for col in range(SCALE):
                x=(col+.5)/SCALE*128
                u=((bx-x)*(cy-y)-(cx-x)*(by-y))/area
                v=((cx-x)*(ay-y)-(ax-x)*(cy-y))/area
                if u>=-1e-9 and v>=-1e-9 and 1-u-v>=-1e-9:
                    pixels[col,row]=255
    return image


# Native Luna WebWalkTerrain / SceneGraph shape tables.
SHAPE_POINTS = [[1, 3, 5, 7], [1, 3, 5, 7], [1, 3, 5, 7], [1, 3, 5, 7, 6], [1, 3, 5, 7, 6], [1, 3, 5, 7, 6], [1, 3, 5, 7, 6], [1, 3, 5, 7, 2, 6], [1, 3, 5, 7, 2, 8], [1, 3, 5, 7, 2, 8], [1, 3, 5, 7, 11, 12], [1, 3, 5, 7, 11, 12], [1, 3, 5, 7, 13, 14]]
SHAPE_FACES = [[0, 1, 2, 3, 0, 0, 1, 3], [1, 1, 2, 3, 1, 0, 1, 3], [0, 1, 2, 3, 1, 0, 1, 3], [0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3], [0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4], [0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4], [0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3], [0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3], [0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5], [0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5], [0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3], [1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3], [1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5]]
