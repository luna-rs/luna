"""Read-only literal subzone discovery, never Kotlin execution. Author: lare96."""
import hashlib
from pathlib import Path
import re


def existing_zones(server):
    path=Path(server)/'src/main/kotlin/api/api/bot/zone/SubZone.kt'
    if not path.is_file():
        return {'zones':[],'unparsed':[],'available':False}
    raw=path.read_bytes()
    if raw!=path.read_bytes():
        raise ValueError('SubZone.kt changed during the read. Refresh the atlas.')
    text=raw.decode('utf-8-sig')
    # Only literal constructor arguments are indexed; no dynamic expression is evaluated.
    text=re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S)
    entries=list(re.finditer(r'^    ([A-Z][A-Z0-9_]*)\(',text,re.M))
    zones=[]
    unparsed=[]
    for number,entry in enumerate(entries):
        body=text[entry.end():entries[number+1].start() if number+1<len(entries) else len(text)]
        area=re.search(r'\barea\s*=\s*SimpleBoxArea\.(ofRadius|of)\(\s*((?:-?\d+\s*,\s*){3}-?\d+(?:\s*,\s*-?\d+)?)\s*\)',body)
        if not area:
            unparsed.append(entry[1]); continue
        values=[int(v.strip()) for v in area[2].split(',')]
        x,y,a,b=values[:4]
        max_x,max_y=(x+a-1,y+b-1) if area[1]=='ofRadius' else (a,b)
        plane=values[4] if len(values)==5 else 0
        inside=re.search(r'\binside\s*=\s*Position\(\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*(\d+))?\s*\)',body)
        parent=re.search(r'\bparent\s*=\s*\{\s*([A-Z][A-Z0-9_]*)\s*\}',body)
        zone={'id':entry[1],'name':entry[1].replace('_',' ').title(),
              'bounds':{'minX':x,'minY':y,'maxX':max_x,'maxY':max_y,'plane':plane},
              'parent':parent[1] if parent else 'dynamic',
              'source':str(path),'sourceHash':hashlib.sha256(raw).hexdigest(),'literalOnly':True}
        if inside:
            zone['inside']={'x':int(inside[1]),'y':int(inside[2]),'plane':int(inside[3] or 0)}
        if max_x<x or max_y<y:
            unparsed.append(entry[1]); continue
        zones.append(zone)
    return {'zones':zones,'unparsed':unparsed,'available':True,
            'source':str(path),'sourceHash':hashlib.sha256(raw).hexdigest(),
            'coverage':'Literal SimpleBoxArea.of and ofRadius arguments only; dynamic constructors remain unparsed.'}
