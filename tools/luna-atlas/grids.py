"""Luna region/chunk coordinates without cache or gameplay mutations.

Region.java uses 64-tile cells and x*256+y identifiers. Position.getChunkX/Y
are global 8-tile coordinates, while Position.getChunk constructs Chunk using
getBottomLeftChunkX/Y (global minus six). Chunk converts back with 8*(x+6).
Its mixed hashCode is explicitly not a coordinate identifier.

@author lare96
"""
import math

WORLD_SIZE = 16384
REGION_SIZE = 64
CHUNK_SIZE = 8
SIZES = {'region': REGION_SIZE, 'chunk': CHUNK_SIZE}
SOURCE = {
    'region': 'src/main/java/io/luna/game/model/Region.java',
    'chunk': 'src/main/java/io/luna/game/model/chunk/Chunk.java',
    'position': 'src/main/java/io/luna/game/model/Position.java',
    'hash': 'src/main/java/io/luna/util/HashUtils.java',
}


def _integer(value, label, minimum=0, maximum=WORLD_SIZE-1):
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(label+' must be an integer.')
    if not minimum <= value <= maximum:
        raise ValueError(f'{label} must be between {minimum} and {maximum}.')
    return value


def _kind(kind):
    if kind not in SIZES:
        raise ValueError('Grid kind must be region or chunk.')
    return kind


def java_mix32(value):
    """HashUtils.mix32 with Java signed-int overflow; not a cell ID."""
    value &= 0xffffffff
    value ^= value >> 16
    value = (value * 0x85ebca6b) & 0xffffffff
    value ^= value >> 13
    value = (value * 0xc2b2ae35) & 0xffffffff
    value ^= value >> 16
    return value if value < 0x80000000 else value-0x100000000


def grid_at(x, y, plane=0, kind='region'):
    """Describe a native cell containing an integer world tile. Author: lare96."""
    _integer(x,'x'); _integer(y,'y'); _integer(plane,'plane',0,3); _kind(kind)
    size=SIZES[kind]
    cx,cy=x//size,y//size
    rx,ry=x//REGION_SIZE,y//REGION_SIZE
    bounds={'minX':cx*size,'minY':cy*size,'maxX':cx*size+size-1,'maxY':cy*size+size-1,'plane':plane}
    result={'kind':kind,'x':cx,'y':cy,'plane':plane,'size':size,'bounds':bounds,
            'regionX':rx,'regionY':ry,'regionId':rx*256+ry,
            'coordinateSemantics':'Grid geometry and identifiers are plane-independent; plane describes the inspected map view.',
            'source':SOURCE[kind]}
    if kind=='region':
        result.update(id=rx*256+ry,key=str(rx*256+ry),identifier=f'Region {rx*256+ry} · {rx}, {ry}')
    else:
        nx,ny=cx-6,cy-6
        packed=((nx<<16)|(ny&0xffff)) & 0xffffffff
        signed=packed if packed<0x80000000 else packed-0x100000000
        result.update(id=f'{cx},{cy}',key=f'{cx},{cy}',identifier=f'Chunk {cx}, {cy}',
                      worldChunkX=cx,worldChunkY=cy,nativeX=nx,nativeY=ny,
                      localChunkX=cx%8,localChunkY=cy%8,packedCoordinate=signed,
                      hashCode=java_mix32(packed),
                      nativeSemantics='Luna Chunk.x/y are global chunk coordinates minus 6. Packed coordinate and mixed hash are diagnostic values, not unique grid IDs.')
    return result


def grid_from_id(kind, identifier, plane=0):
    """Resolve a region ID or global chunk coordinate pair. Author: lare96."""
    _kind(kind); _integer(plane,'plane',0,3)
    if kind=='region':
        if isinstance(identifier,str) and identifier.isdecimal():
            identifier=int(identifier)
        _integer(identifier,'region ID',0,65535)
        return grid_at((identifier//256)*64,(identifier%256)*64,plane,kind)
    if isinstance(identifier,str):
        values=identifier.split(',')
        if len(values)!=2 or any(not v.strip().isdecimal() for v in values):
            raise ValueError('Chunk identifier must be a global x,y coordinate pair.')
        cx,cy=map(lambda v:int(v.strip()),values)
    elif isinstance(identifier,(list,tuple)) and len(identifier)==2:
        cx,cy=identifier
    else:
        raise ValueError('Chunk identifier must be a global x,y coordinate pair.')
    _integer(cx,'chunk x',0,2047);_integer(cy,'chunk y',0,2047)
    return grid_at(cx*8,cy*8,plane,kind)


def clipped_bounds(bounds):
    """Viewport tile bounds clipped to Atlas's supported world; empty -> None."""
    try:
        values=[bounds[k] for k in ('minX','minY','maxX','maxY')]
    except (TypeError,KeyError):
        raise ValueError('Grid bounds need minX, minY, maxX and maxY.') from None
    if any(isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) for v in values):
        raise ValueError('Grid bounds must be finite numbers.')
    min_x,min_y,max_x,max_y=values
    if min_x>max_x or min_y>max_y:
        raise ValueError('Grid bounds are inverted.')
    if max_x<0 or max_y<0 or min_x>=WORLD_SIZE or min_y>=WORLD_SIZE:
        return None
    return {'minX':max(0,math.floor(min_x)),'minY':max(0,math.floor(min_y)),
            'maxX':min(WORLD_SIZE-1,math.floor(max_x)),'maxY':min(WORLD_SIZE-1,math.floor(max_y))}


def grid_lines(bounds,kind='region'):
    """World-edge line segments; north-up projection belongs to the caller."""
    _kind(kind)
    b=clipped_bounds(bounds)
    if b is None:
        return []
    size=SIZES[kind]
    x0,y0,x1,y1=b['minX'],b['minY'],b['maxX']+1,b['maxY']+1
    lines=[]
    for x in range(((x0+size-1)//size)*size,x1+1,size):
        lines.append((x,y0,x,y1))
    for y in range(((y0+size-1)//size)*size,y1+1,size):
        lines.append((x0,y,x1,y))
    return lines


def grid_cells(bounds,kind='region',plane=0,limit=4096):
    """Bounded cells intersecting a viewport, including partial edge cells."""
    _kind(kind);_integer(plane,'plane',0,3);_integer(limit,'limit',1,65536)
    b=clipped_bounds(bounds)
    if b is None:
        return []
    size=SIZES[kind]
    result=[]
    for cx in range(b['minX']//size,b['maxX']//size+1):
        for cy in range(b['minY']//size,b['maxY']//size+1):
            result.append(grid_at(cx*size,cy*size,plane,kind))
            if len(result)>=limit:
                return result
    return result


def clean_custom_labels(values,kind):
    """Keep supported, bounded Atlas display labels from saved preferences."""
    _kind(kind)
    if not isinstance(values,dict):
        return {}
    labels={}
    for key,value in values.items():
        if len(labels)>=4096:
            break
        if not isinstance(value,str):
            continue
        value=' '.join(value.split())[:80]
        if not value:
            continue
        try:
            key=grid_from_id(kind,key)['key']
        except (ValueError,TypeError):
            continue
        labels[key]=value
    return labels


def describe(kind,x,y,plane=0):
    return grid_at(x,y,plane,kind)


def iter_cells(kind,bounds,plane=0,limit=4096):
    yield from grid_cells(bounds,kind,plane,limit)
