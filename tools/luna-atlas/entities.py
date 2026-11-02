"""Source NPC placements and effective Luna location context. Author: lare96."""
from __future__ import annotations

import copy
import hashlib
import json
from bisect import bisect_right
from collections import Counter, OrderedDict, defaultdict
from pathlib import Path
import re
import threading
import time

from cache377 import CacheError, Reader
from geometry import contains, parts_for_plane
from project import _mask_comments, _code_only

MAX_SOURCE_BYTES = 12_000_000
MAX_SPAWNS = 100_000
MAX_CONTEXTS = 8192
DIRECTIONS = {'NONE','NORTH_WEST','NORTH','NORTH_EAST','WEST','EAST','SOUTH_WEST','SOUTH','SOUTH_EAST'}
ROUTES = {'NORMAL','BLOCKED','INDOORS','OUTDOORS'}
FREQUENCIES = {'SLOW','NORMAL','FAST','RAPID'}


def _diagnostic(code, message, severity='warning', source=None):
    result={'code':code,'message':str(message),'severity':severity}
    if source is not None:
        result['source']=source
    return result


def _integer(value,label,low=0,high=16383):
    if type(value) is not int or not low<=value<=high:
        raise ValueError(f'{label} must be an integer between {low} and {high}.')
    return value


def decode_npc_definitions(archive):
    """Consume bounded native npc.idx/dat records without executing client code. Author: lare96."""
    raw=archive.get('npc.dat')
    index=Reader(archive.get('npc.idx'),'npc.idx')
    count=index.u16()
    if count>65535:
        raise CacheError('NPC definition count exceeds native IDs.')
    sizes=[index.u16() for _ in range(count)]
    index.finish()
    if len(raw)<2 or int.from_bytes(raw[:2],'big')!=count or sum(sizes)+2!=len(raw):
        raise CacheError('NPC definition index and data lengths/counts disagree.')
    result,diagnostics={},[]
    offset=2
    for npc_id,size in enumerate(sizes):
        data=Reader(raw[offset:offset+size],f'NPC definition {npc_id}')
        offset+=size
        definition={'id':npc_id,'npcId':npc_id,'kind':'npc','name':'null','size':1,
                    'actions':[],'combatLevel':1,'morph':None,'definitionResolved':True}
        actions={}
        try:
            while True:
                opcode=data.u8()
                if opcode==0:
                    data.finish()
                    break
                if opcode in (1,60):
                    data.take(data.u8()*2)
                elif opcode in (2,3):
                    value=data.text()
                    if opcode==2:
                        definition['name']=value
                elif opcode==12:
                    value=data.u8()
                    definition['size']=value if value<128 else value-256
                elif opcode in (13,14,90,91,92,97,98,102,103):
                    data.take(2)
                elif opcode==17:
                    data.take(8)
                elif 30<=opcode<40:
                    action=data.text()
                    # Server decoder checks the literal lower-case hidden token.
                    if action not in ('hidden','null',''):
                        actions[opcode-30]=action
                    else:
                        actions.pop(opcode-30,None)
                elif opcode==40:
                    data.take(data.u8()*4)
                elif opcode in (93,99,107):
                    pass
                elif opcode==95:
                    definition['combatLevel']=data.u16()
                elif opcode in (100,101):
                    data.take(1)
                elif opcode==106:
                    varbit,varp=data.u16(),data.u16()
                    children=[data.u16() for _ in range(data.u8()+1)]
                    definition['morph']={'varbit':-1 if varbit==65535 else varbit,
                                         'varp':-1 if varp==65535 else varp,
                                         'children':[-1 if child==65535 else child for child in children]}
                else:
                    raise CacheError(f'Unsupported native NPC opcode {opcode} at byte {data.at-1}.')
            definition['actions']=[actions[i] for i in sorted(actions)]
            definition['rawName']=definition['name']
            definition['nameResolved']=definition['name'] not in ('null','')
            if not definition['nameResolved']:
                definition['name']=f'NPC {npc_id}'
            result[npc_id]=definition
        except (ValueError,IndexError) as error:
            diagnostics.append(_diagnostic('NPC_DEFINITION_UNRESOLVED',str(error),source={'archive':2,'member':'npc.dat','npcId':npc_id}))
            result[npc_id]={'id':npc_id,'npcId':npc_id,'kind':'npc','name':f'NPC {npc_id}',
                            'rawName':None,'size':None,'actions':[],'combatLevel':None,'morph':None,
                            'definitionResolved':False,'nameResolved':False}
    return result,diagnostics,count


def _area(entry):
    coverage=entry.get('registrationCoverage',{}).get('effectiveTiles')
    if type(coverage) is int:
        return coverage
    rectangles=[p for p in entry.get('parts',[]) if p.get('type','rectangle')=='rectangle']
    if rectangles:
        # Sweep exact rectangle unions, ignoring planes for specificity ranking.
        xs=sorted({x for p in rectangles for x in (p['minX'],p['maxX']+1)})
        total=0
        for x0,x1 in zip(xs,xs[1:]):
            spans=sorted((p['minY'],p['maxY']+1) for p in rectangles if p['minX']<=x0<=p['maxX'])
            last=None;length=0
            for y0,y1 in spans:
                if last is None or y0>last:
                    length+=y1-y0
                elif y1>last:
                    length+=y1-last
                last=max(last if last is not None else y1,y1)
            total+=(x1-x0)*length
        return total
    return sum(abs(sum(ax*by-bx*ay for (ax,ay),(bx,by) in zip(p.get('vertices',[]),p.get('vertices',[])[1:]+p.get('vertices',[])[:1])))/2
               for p in entry.get('parts',[]) if p.get('type')=='polygon')


class LocationIndex:
    """Plane-aware actual membership with deterministic overlap ordering. Author: lare96."""

    def __init__(self,catalog):
        self.catalog=catalog or {}
        self.entries={str(e['id']):e for e in self.catalog.get('entries',[])}
        self.buckets=defaultdict(set)
        self.broad={p:set() for p in range(4)}
        self.areas={key:_area(entry) for key,entry in self.entries.items()}
        self.depths={key:self._depth(key) for key in self.entries}
        self.complete=self.catalog.get('consistent',bool(self.entries)) is not False and bool(self.entries or self.catalog.get('consistent'))
        discovery=self.catalog.get('discovery',{}).get('resources',{})
        if discovery and (not discovery.get('zoneSource') or not discovery.get('subzoneSource')):
            self.complete=False
        incomplete={'CATALOG_ZONES','CATALOG_SUBZONES','UNRESOLVED_ZONE_BOUNDARY',
                    'UNRESOLVED_SUBZONE_BOUNDARY','ZONE_REGISTRATION_CONFLICT','SOURCE_CHANGED_DURING_SCAN'}
        if any(issue.get('code') in incomplete for issue in self.catalog.get('diagnostics',[])):
            self.complete=False
        for key,entry in self.entries.items():
            if entry.get('kind') in ('zone','subzone') and entry.get('activation',{}).get('status') not in ('registered','conditional','unregistered'):
                self.complete=False
            if entry.get('kind') in ('zone','subzone') and entry.get('activation',{}).get('status')!='unregistered' and not entry.get('parts'):
                self.complete=False
            for plane in range(4):
                for part in parts_for_plane(entry,plane):
                    if part.get('type','rectangle')=='rectangle':
                        x0,y0,x1,y1=(part[k] for k in ('minX','minY','maxX','maxY'))
                    elif part.get('vertices'):
                        xs,ys=zip(*part['vertices']);x0,y0,x1,y1=min(xs),min(ys),max(xs),max(ys)
                    else:
                        continue
                    rx0,ry0=max(0,int(x0)//64),max(0,int(y0)//64)
                    rx1,ry1=min(255,int(x1)//64),min(255,int(y1)//64)
                    if (rx1-rx0+1)*(ry1-ry0+1)>4096:
                        self.broad[plane].add(key)
                    else:
                        for rx in range(rx0,rx1+1):
                            for ry in range(ry0,ry1+1):
                                self.buckets[(plane,rx,ry)].add(key)
        self.cache=OrderedDict()
        self.lock=threading.RLock()

    def _depth(self,key):
        depth=0;seen=set()
        while key in self.entries and key not in seen:
            seen.add(key);depth+=1;key=self.entries[key].get('parentId')
        return depth

    def _compact(self,entry):
        return {key:copy.deepcopy(entry[key]) for key in ('id','name','kind','identifier','parentId','parentOptions','parentRelationship','parentExpression','source','activation') if key in entry}

    def context(self,x=None,y=None,plane=None):
        if x is None and y is None and plane is None:
            return {'status':'definition-only','label':'No known placement','matches':[],
                    'additionalMatches':[],'primaryZone':None,'primarySubzone':None,
                    'warnings':['No static placement was loaded; this does not rule out scripted or live instances.']}
        if any(type(v) is not int for v in (x,y,plane)) or not (0<=x<=16383 and 0<=y<=16383 and 0<=plane<=3):
            return {'status':'unknown','label':'Zone membership unknown','matches':[],
                    'additionalMatches':[],'primaryZone':None,'primarySubzone':None,'warnings':['Location is outside supported tile/plane coordinates.']}
        key=(x,y,plane)
        with self.lock:
            if key in self.cache:
                self.cache.move_to_end(key)
                return copy.deepcopy(self.cache[key])
        candidates=self.buckets.get((plane,x>>6,y>>6),set())|self.broad[plane]
        matched=[self.entries[k] for k in candidates if contains(self.entries[k],x,y,plane)
                 and self.entries[k].get('activation',{}).get('status')!='unregistered']
        registered=[e for e in matched if e.get('activation',{}).get('status') in ('registered','conditional')]
        zones=sorted((e for e in registered if e.get('kind')=='zone'),key=lambda e:(self.areas[e['id']],e['id']))
        subzones=sorted((e for e in registered if e.get('kind')=='subzone'),key=lambda e:(self.areas[e['id']],-self.depths[e['id']],e['id']))
        subzone=subzones[0] if subzones else None
        zone=zones[0] if zones else None
        warnings=[]
        dynamic=False
        unresolved_parent=False
        hierarchy=[]
        dynamic_options=[]
        if subzone:
            zone=None
            current=subzone
            seen=set()
            while current and current['id'] not in seen:
                seen.add(current['id']);hierarchy.append(current)
                if current.get('kind')=='zone':
                    zone=current;break
                options=current.get('parentOptions',[])
                parent=self.entries.get(current.get('parentId'))
                if parent and parent.get('activation',{}).get('status') in ('registered','conditional'):
                    current=parent;continue
                if len(options)>1 or any(token in current.get('parentRelationship','').lower() for token in ('dynamic','runtime-dependent')):
                    dynamic=True;dynamic_options=options
                    warnings.append('This subzone has a runtime-dependent travel parent; no fixed parent is inferred from location.')
                else:
                    unresolved_parent=True
                    warnings.append('The declared parent is missing or unregistered; no other containing zone is substituted as its parent.')
                break
            else:
                unresolved_parent=True
                warnings.append('A cyclic parent hierarchy could not be resolved.')
        if subzone and dynamic:
            names=[self.entries[p]['name'] for p in dynamic_options if p in self.entries]
            label=('Dynamic parent ('+' / '.join(names)+')' if names else 'Dynamic parent')+' → '+' → '.join(e['name'] for e in reversed(hierarchy))
        elif subzone:
            label=('Unresolved parent → ' if unresolved_parent else '')+' → '.join(e['name'] for e in reversed(hierarchy))
        elif zone:
            label=zone['name']
        else:
            label='Outside defined zones' if self.complete else 'Zone membership unknown'
        known=self.complete and not unresolved_parent and all(e.get('activation',{}).get('status')=='registered'
                   for e in matched+hierarchy if e.get('kind') in ('zone','subzone'))
        status='known' if known and (zone or subzone) else 'outside' if known else 'unknown'
        if not known:
            warnings.append('Source zone coverage or registration is unresolved; additional membership cannot be ruled out.')
        selected={e['id'] for e in (zone,subzone) if e}
        ordered=sorted((e for e in matched if e.get('kind') in ('zone','subzone')),
                       key=lambda e:(0 if e.get('kind')=='zone' else 1,self.areas[e['id']],e['id']))
        result={'status':status,'label':label,'x':x,'y':y,'plane':plane,
                'primaryZone':self._compact(zone) if zone else None,
                'primarySubzone':self._compact(subzone) if subzone else None,
                'hierarchy':[self._compact(e) for e in reversed(hierarchy or ([zone] if zone else []))],
                'matches':[self._compact(e) for e in ordered],
                'additionalMatches':[self._compact(e) for e in ordered if e['id'] not in selected],
                'geographicZones':[self._compact(e) for e in zones],
                'controllers':[self._compact(e) for e in sorted(registered,key=lambda e:e['id']) if e.get('kind')=='controller'],
                'dynamicParent':dynamic,'warnings':warnings,'scope':'effective-static-source-geometry'}
        if subzone and zone:
            result['parentContainsLocation']=contains(zone,x,y,plane)
            if not result['parentContainsLocation']:
                result['warnings'].append('The declared travel parent is shown in the hierarchy; its geometric region does not contain this placement.')
        with self.lock:
            self.cache[key]=result
            self.cache.move_to_end(key)
            while len(self.cache)>MAX_CONTEXTS:
                self.cache.popitem(last=False)
        return copy.deepcopy(result)

    def annotate(self,entity):
        result=dict(entity)
        context=self.context(entity.get('x'),entity.get('y'),entity.get('plane'))
        result['context']=context
        result['locationLabel']=context['label']
        result['displayName']=str(entity.get('name','Unnamed entity'))+' — '+context['label']
        return result


def _json_tokens(text):
    """Yield balanced top-array record spans; preserve valid records around malformed JSON."""
    start=0
    while start<len(text) and text[start].isspace():start+=1
    if start>=len(text) or text[start]!='[':
        raise ValueError('NPC spawn file must contain a JSON array.')
    begin=start+1;depth=0;quoted=False;escaped=False
    for at in range(begin,len(text)):
        char=text[at]
        if quoted:
            if escaped:escaped=False
            elif char=='\\':escaped=True
            elif char=='"':quoted=False
            continue
        if char=='"':quoted=True
        elif char in '[{':depth+=1
        elif char=='}':depth-=1
        elif char==']':
            if depth==0:
                if text[begin:at].strip():yield begin,text[begin:at]
                if text[at+1:].strip():raise ValueError('Unexpected data after NPC spawn array.')
                return
            depth-=1
        elif char==',' and depth==0:
            yield begin,text[begin:at];begin=at+1
        if depth<0:raise ValueError('Unbalanced NPC spawn JSON delimiters.')
    if text[begin:].strip():yield begin,text[begin:]
    raise ValueError('NPC spawn array or string is incomplete.')


class EntityCatalog:
    """Declared global NPC placements and names for a selected source snapshot. Author: lare96."""

    def __init__(self,world,project_report,catalog):
        started=time.perf_counter()
        self.world=world
        self.project=project_report or {}
        self.locations=LocationIndex(catalog)
        self.definitions={}
        self.spawns=[]
        self.diagnostics=[]
        self.by_id={}
        self.placements=defaultdict(list)
        self.spatial=defaultdict(list)
        self.consistent=True
        self.file_valid=True
        self.source_path=None
        self.spawn_path=None
        self._sources={}
        self._definition_count=0
        try:
            self.definitions,issues,self._definition_count=decode_npc_definitions(world.snapshot.archive(2))
            self.diagnostics.extend(issues)
        except (ValueError,OSError,AttributeError) as error:
            self.diagnostics.append(_diagnostic('NPC_DEFINITIONS_UNAVAILABLE',str(error),'error'))
        self._load_spawns()
        self._check_sources()
        digest=hashlib.sha256(json.dumps(getattr(world,'fingerprints',{}),sort_keys=True).encode())
        digest.update(str(self.spawn_path or self.project.get('resources',{}).get('npcSpawns')).encode())
        for path,(checksum,_) in sorted(self._sources.items(),key=lambda pair:str(pair[0])):
            digest.update(str(path).encode());digest.update(checksum.encode())
        self.sourceHash=digest.hexdigest()
        self.summary={'spawns':len(self.spawns),'definitions':len(self.definitions),'nativeDefinitionCount':self._definition_count,
                      'placedDefinitions':len(self.placements),'unplacedDefinitions':sum(i not in self.placements for i in self.definitions),
                      'planes':dict(Counter(s['plane'] for s in self.spawns)),
                      'source':self.source_path,'sourceHash':self.sourceHash,'consistent':self.consistent,
                      'fileValid':self.file_valid,'diagnosticCount':len(self.diagnostics),
                      'loadSeconds':round(time.perf_counter()-started,4),'reused':False,
                      'coverage':'Declared global NPC JSON spawn anchors. Runtime movement, scripted spawns and current live instances are not captured.'}

    def _relative(self,path):
        root=self.project.get('root')
        try:return Path(path).resolve().relative_to(Path(root).resolve()).as_posix() if root else str(path)
        except ValueError:return str(path)

    def _read(self,path):
        path=Path(path).resolve()
        raw=path.read_bytes()
        if len(raw)>MAX_SOURCE_BYTES:
            raise ValueError('NPC source file exceeds the supported inspection limit.')
        if raw!=path.read_bytes():
            self.consistent=False
            raise ValueError('NPC source changed while reading; reload the project.')
        text=raw.decode('utf-8-sig')
        self._sources[path]=(hashlib.sha256(raw).hexdigest(),text)
        return text

    def _check_sources(self):
        for path,(checksum,_) in self._sources.items():
            try:unchanged=hashlib.sha256(path.read_bytes()).hexdigest()==checksum
            except OSError:unchanged=False
            if not unchanged:
                self.consistent=False
                self.diagnostics.append(_diagnostic('NPC_SOURCE_CHANGED','NPC source changed during loading; reload before relying on it.','error',{'path':self._relative(path),'line':1}))

    def _load_spawns(self):
        root=self.project.get('root')
        if not root:
            self.diagnostics.append(_diagnostic('NPC_SOURCE_DIRECTORY_MISSING','Open a Luna directory to load its NPC spawn placements.'))
            return
        resources=self.project.get('resources',{})
        if 'npcSpawns' in resources and not resources['npcSpawns']:
            self.file_valid=False
            self.diagnostics.append(_diagnostic('NPC_SPAWNS_UNAVAILABLE','The selected project did not resolve its NPC spawn file. Resolve the reported npcSpawns resource; no other file was silently substituted.'))
            return
        path=resources.get('npcSpawns') or str(Path(root)/'data/game/world/npc_spawns.jsonc')
        path=Path(path)
        if not path.is_absolute():path=Path(root)/path
        self.spawn_path=path.resolve()
        self.source_path=self._relative(path)
        loader=Path(root)/'src/main/kotlin/engine/engine/spawn/NpcSpawnFileParser.kt'
        launch=Path(root)/'src/main/kotlin/engine/engine/spawn/spawn.kts'
        verified=True
        for source,required in ((loader,('NpcSpawnFileParser',)),(launch,('ServerLaunchEvent','NpcSpawnFileParser'))):
            try:
                text=self._read(source)
                masked=_mask_comments(text);code=_code_only(masked)
                if not all(value in code for value in required):verified=False
                if source==loader and not re.search(r'Paths\.get\(\s*"data"\s*,\s*"game"\s*,\s*"world"\s*,\s*"npc_spawns\.jsonc"\s*\)',masked):verified=False
                if source==launch and not re.search(r'\btaskPool\s*\.\s*execute\(\s*NpcSpawnFileParser\(\s*\)\s*\)',code):verified=False
            except (OSError,ValueError,UnicodeError):verified=False
        self.loader_verified=verified
        if not verified:
            self.diagnostics.append(_diagnostic('NPC_LOADER_UNVERIFIED','NPC loading code could not be fully verified. Displayed markers are declared JSON placements, not proof of running NPCs.'))
        try:
            text=_mask_comments(self._read(path))
        except (OSError,ValueError,UnicodeError) as error:
            self.file_valid=False
            self.diagnostics.append(_diagnostic('NPC_SPAWNS_UNAVAILABLE',str(error),'warning',{'path':self.source_path,'line':1}))
            return
        lines=[at for at,c in enumerate(text) if c=='\n']
        try:
            value=json.loads(text,parse_constant=lambda value:(_ for _ in ()).throw(ValueError('Non-finite JSON number.')))
            if not isinstance(value,list):raise ValueError('NPC spawn file must contain an array.')
        except (ValueError,TypeError) as error:
            self.file_valid=False
            self.diagnostics.append(_diagnostic('NPC_SPAWN_JSON','Strict JSON validation failed: '+str(error)+'. Valid balanced records are retained as a partial source preview; permissive Gson variants are not executed.','error',{'path':self.source_path,'line':getattr(error,'lineno',1)}))
        duplicates=Counter()
        by_name=defaultdict(list)
        for npc_id,definition in self.definitions.items():
            raw_name=definition.get('rawName',definition['name'])
            if raw_name is not None:by_name[raw_name.casefold()].append(npc_id)
        try:
            for index,(offset,token) in enumerate(_json_tokens(text)):
                actual_offset=offset+len(token)-len(token.lstrip())
                source={'path':self.source_path,'line':bisect_right(lines,actual_offset)+1,'index':index}
                if index>=MAX_SPAWNS:
                    self.diagnostics.append(_diagnostic('NPC_SPAWN_LIMIT','NPC spawn inspection limit reached.','error',source));break
                try:
                    value=json.loads(token,parse_constant=lambda value:(_ for _ in ()).throw(ValueError('Non-finite JSON number.')))
                    spawn=self._spawn(value,source,by_name)
                    signature=json.dumps([spawn[k] for k in ('npcId','x','y','plane','respawnTicks','defaultDirection','routeStrategy','wander')],sort_keys=True)
                    digest=hashlib.sha256(signature.encode()).hexdigest()[:20]
                    occurrence=duplicates[digest];duplicates[digest]+=1
                    spawn['id']=spawn['instanceId']=f'npc-{digest}-{occurrence}'
                    spawn['fileValid']=self.file_valid
                    spawn['loaderVerified']=verified
                    self.spawns.append(spawn);self.by_id[spawn['id']]=spawn
                    self.placements[spawn['npcId']].append(spawn)
                    self.spatial[(spawn['plane'],spawn['x']>>6,spawn['y']>>6)].append(spawn)
                except (ValueError,TypeError,KeyError) as error:
                    self.diagnostics.append(_diagnostic('NPC_SPAWN_RECORD',str(error),'warning',source))
        except ValueError as error:
            self.file_valid=False
            self.diagnostics.append(_diagnostic('NPC_SPAWN_JSON_TAIL',str(error),'error',{'path':self.source_path,'line':len(lines)+1}))
        if not self.file_valid:
            for spawn in self.spawns:spawn['fileValid']=False

    def _spawn(self,value,source,by_name):
        if not isinstance(value,dict):raise ValueError('NPC spawn must be a JSON object.')
        if 'name' in value:
            if not isinstance(value['name'],str):raise ValueError('NPC name must be text.')
            ids=by_name.get(value['name'].casefold(),[])
            if not ids:raise ValueError('No cache NPC definition matches name '+repr(value['name'])+'.')
            npc_id=min(ids)
            if len(ids)>1:
                self.diagnostics.append(_diagnostic('NPC_AMBIGUOUS_NAME',f'Name {value["name"]!r} matches several definitions; Luna resolves the first ID ({npc_id}).',source=source))
        else:npc_id=_integer(value.get('id'),'NPC ID',0,65535)
        if npc_id not in self.definitions:raise ValueError(f'NPC {npc_id} is outside the selected cache definition table.')
        position=value.get('position')
        if not isinstance(position,dict):raise ValueError('NPC position must contain x, y and optional z.')
        x,y=_integer(position.get('x'),'NPC x'),_integer(position.get('y'),'NPC y')
        plane=_integer(position.get('z',0),'NPC z',0,3)
        respawn=_integer(value.get('respawn_ticks',50),'respawn_ticks',-2147483648,2147483647)
        direction=value.get('default_direction')
        route=value.get('route_strategy','NORMAL')
        if 'default_direction' in value and (not isinstance(direction,str) or direction not in DIRECTIONS):raise ValueError('Unknown NPC default_direction.')
        if not isinstance(route,str) or route not in ROUTES:raise ValueError('Unknown NPC route_strategy.')
        wander=None
        if 'wander' in value:
            raw=value['wander']
            if raw is True:wander={'radius':3,'frequency':'NORMAL'}
            elif isinstance(raw,dict):
                radius=_integer(raw.get('radius'),'NPC wander radius',0,2147483647)
                frequency=raw.get('frequency','NORMAL')
                if not isinstance(frequency,str) or frequency not in FREQUENCIES:raise ValueError('Unknown NPC wander frequency.')
                wander={'radius':radius,'frequency':frequency}
            else:raise ValueError('Luna requires wander=true or a wander object when this field is present.')
        definition=self.definitions[npc_id]
        return {'kind':'npc','npcId':npc_id,'name':definition['name'],'definitionResolved':definition['definitionResolved'],
                'nameResolved':definition['nameResolved'],'definitionOnly':False,'x':x,'y':y,'plane':plane,
                'size':definition['size'],'actions':list(definition['actions']),'combatLevel':definition['combatLevel'],
                'morph':definition['morph'],'respawnTicks':respawn,'defaultDirection':direction,
                'routeStrategy':route,'wander':wander,'source':source,
                'placementSource':'Luna global NPC JSON spawn anchor','runtimePosition':False}

    def _filtered(self,plane,bounds):
        if plane is not None:_integer(plane,'plane',0,3)
        if bounds is None:
            return (s for s in self.spawns if plane is None or s['plane']==plane)
        if isinstance(bounds,dict):x0,y0,x1,y1=(bounds[k] for k in ('minX','minY','maxX','maxY'))
        else:x0,y0,x1,y1=bounds
        for name,value in zip(('minX','minY','maxX','maxY'),(x0,y0,x1,y1)):_integer(value,name)
        if x0>x1 or y0>y1:raise ValueError('NPC search bounds are inverted.')
        def iterate():
            for level in (range(4) if plane is None else (plane,)):
                for rx in range(x0>>6,(x1>>6)+1):
                    for ry in range(y0>>6,(y1>>6)+1):
                        for spawn in self.spatial.get((level,rx,ry),()):
                            if x0<=spawn['x']<=x1 and y0<=spawn['y']<=y1:yield spawn
        return iterate()

    @staticmethod
    def _matches(name,definition):
        query=str(name).strip().casefold()
        if query.startswith('id:'):return query[3:].strip().isdigit() and definition.get('npcId',definition.get('id'))==int(query[3:].strip())
        if query.isdigit():return definition.get('npcId',definition.get('id'))==int(query)
        if query.startswith('name:'):query=query[5:].strip()
        return not query or query in definition['name'].casefold()

    def search(self,name='',plane=None,bounds=None,limit=200,include_unplaced=False):
        _integer(limit,'limit',1,20001)
        result=[]
        for spawn in self._filtered(plane,bounds):
            if self._matches(name,spawn):
                result.append(self.locations.annotate(spawn))
                if len(result)>=limit:return result
        if include_unplaced and bounds is None:
            for npc_id,definition in self.definitions.items():
                if npc_id not in self.placements and self._matches(name,definition):
                    item=dict(definition,id=f'npc-definition-{npc_id}',instanceId=None,definitionOnly=True,x=None,y=None,plane=None,
                              placementSource='No global JSON placement loaded',placementCoverage='global-json-only')
                    result.append(self.locations.annotate(item))
                    if len(result)>=limit:return result
        return result

    def inspect(self,instance_id):
        spawn=self.by_id.get(instance_id)
        if spawn is None:raise ValueError('Unknown NPC spawn instance.')
        return self.locations.annotate(spawn)

    def rebind(self,project_report,catalog):
        """Reuse unchanged declarations while refreshing their zone context. Author: lare96."""
        started=time.perf_counter()
        old_root=self.project.get('root')
        new_root=(project_report or {}).get('root')
        same_root=old_root and new_root and Path(old_root).resolve()==Path(new_root).resolve()
        old_resource=self.project.get('resources',{}).get('npcSpawns')
        new_resource=(project_report or {}).get('resources',{}).get('npcSpawns')
        unchanged=bool(same_root and self.consistent and old_resource==new_resource)
        if unchanged:
            expected={Path(new_root)/'src/main/kotlin/engine/engine/spawn/NpcSpawnFileParser.kt',
                      Path(new_root)/'src/main/kotlin/engine/engine/spawn/spawn.kts'}
            if self.spawn_path:expected.add(self.spawn_path)
            for path in expected|set(self._sources):
                path=path.resolve()
                try:
                    checksum=hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
                except OSError:
                    unchanged=False;break
                previous=self._sources.get(path)
                if checksum!=(previous[0] if previous else None):
                    unchanged=False;break
        if not unchanged:
            return EntityCatalog(self.world,project_report,catalog)
        result=copy.copy(self)
        result.project=project_report
        result.locations=LocationIndex(catalog)
        result.summary=dict(self.summary,reused=True,loadSeconds=round(time.perf_counter()-started,4))
        return result

    def data(self,plane=None,bounds=None,limit=None,contexts=False):
        if limit is not None:_integer(limit,'limit',1,MAX_SPAWNS)
        result=[]
        truncated=False
        for spawn in self._filtered(plane,bounds):
            if limit is not None and len(result)>=limit:
                truncated=True;break
            result.append(self.locations.annotate(spawn) if contexts else dict(spawn))
        return {'spawns':result,'summary':dict(self.summary),'diagnostics':list(self.diagnostics),'sourceHash':self.sourceHash,'truncated':truncated}
