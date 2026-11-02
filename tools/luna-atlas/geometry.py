"""Shared tile-space catalog geometry for inspection and overlays. Author: lare96."""
from functools import lru_cache
import math
import hashlib

PALETTE=('#65c5e0','#d5ae6b','#8dcf8a','#c395da','#e78d88','#84b7ed','#d6cf77')


def catalog_colour(entry):
    family=str(entry.get('parentId') or entry['id'])
    return PALETTE[int(hashlib.sha256(family.encode()).hexdigest()[:8],16)%len(PALETTE)]


def parts_for_plane(entry, plane):
    planes=entry.get('planes')
    if planes is not None and plane not in planes:
        return []
    return [part for part in entry.get('parts',[]) if part.get('plane',-1) in (-1,plane)]


def contains(entry,x,y,plane):
    for part in parts_for_plane(entry,plane):
        if part.get('type','rectangle')=='rectangle':
            if part['minX']<=x<=part['maxX'] and part['minY']<=y<=part['maxY']:
                return True
        elif part.get('type')=='polygon':
            vertices=part.get('vertices',[])
            px,py=(x,y) if part.get('coordinateMode')=='tile' else (x+.5,y+.5)
            inside=False
            for i,(ax,ay) in enumerate(vertices):
                bx,by=vertices[(i+1)%len(vertices)]
                if (ay>py)!=(by>py) and px<(bx-ax)*(py-ay)/(by-ay)+ax:
                    inside=not inside
            if inside:
                return True
    return False


def bounds_for_plane(entry,plane):
    boxes=[]
    for part in parts_for_plane(entry,plane):
        if part.get('type','rectangle')=='rectangle':
            boxes.append(tuple(part[k] for k in ('minX','minY','maxX','maxY')))
        elif part.get('vertices'):
            xs,ys=zip(*part['vertices'])
            boxes.append((math.floor(min(xs)),math.floor(min(ys)),math.ceil(max(xs))-1,math.ceil(max(ys))-1))
    if not boxes:
        return None
    return {'minX':min(b[0] for b in boxes),'minY':min(b[1] for b in boxes),
            'maxX':max(b[2] for b in boxes),'maxY':max(b[3] for b in boxes),'plane':plane}


def _xor_intervals(first,second):
    events={}
    for side,intervals in enumerate((first,second)):
        for start,end in intervals:
            events.setdefault(start,[0,0])[side]+=1
            events.setdefault(end,[0,0])[side]-=1
    counts=[0,0]
    previous=None
    segments=[]
    for point,delta in sorted(events.items()):
        if previous is not None and previous<point and bool(counts[0])!=bool(counts[1]):
            if segments and segments[-1][1]==previous:
                segments[-1]=(segments[-1][0],point)
            else:
                segments.append((previous,point))
        counts=[counts[0]+delta[0],counts[1]+delta[1]]
        previous=point
    return segments


@lru_cache(maxsize=1024)
def _rectangle_outlines(boxes):
    result=[]
    for x in sorted({v for x0,y0,x1,y1 in boxes for v in (x0,x1+1)}):
        left=[(y0,y1+1) for x0,y0,x1,y1 in boxes if x0<x<=x1+1]
        right=[(y0,y1+1) for x0,y0,x1,y1 in boxes if x0<=x<x1+1]
        result.extend((x,a,x,b) for a,b in _xor_intervals(left,right))
    for y in sorted({v for x0,y0,x1,y1 in boxes for v in (y0,y1+1)}):
        below=[(x0,x1+1) for x0,y0,x1,y1 in boxes if y0<y<=y1+1]
        above=[(x0,x1+1) for x0,y0,x1,y1 in boxes if y0<=y<y1+1]
        result.extend((a,y,b,y) for a,b in _xor_intervals(below,above))
    return tuple(result)


def outline_segments(entry,plane):
    parts=parts_for_plane(entry,plane)
    boxes=tuple(sorted(set(tuple(part[k] for k in ('minX','minY','maxX','maxY'))
                           for part in parts if part.get('type','rectangle')=='rectangle')))
    result=list(_rectangle_outlines(boxes))
    for part in parts:
        if part.get('type')=='polygon':
            vertices=part.get('vertices',[])
            result.extend((a[0],a[1],b[0],b[1]) for a,b in zip(vertices,vertices[1:]+vertices[:1]))
    return result
