"""Luna 377 atlas: shared desktop, HTTP, CLI and agent core. Author: lare96."""
import argparse
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import sys
import threading
from urllib.parse import parse_qs, urlparse
import uuid
import hashlib
from io import BytesIO
from PIL import Image

from cache377 import World
from planner import Planner
from render import Renderer
from overlays import existing_zones
from project import discover, catalog as build_catalog
from geometry import contains
from export_layers import draw_layers
from entities import EntityCatalog
from searches import SearchIndex
from grids import grid_at, clean_custom_labels

ROOT = Path(__file__).resolve().parent


def integer(value, label, minimum=0, maximum=16383):
    if isinstance(value, bool) or isinstance(value, float):
        raise ValueError(label + ' must be an integer.')
    try:
        result = int(value)
    except (TypeError, ValueError):
        raise ValueError(label + ' must be an integer.') from None
    if not minimum <= result <= maximum:
        raise ValueError(f'{label} must be between {minimum} and {maximum}.')
    return result


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        with temporary.open('x', encoding='utf-8') as out:
            json.dump(data, out, indent=2, ensure_ascii=False)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def restore_file(path,data):
    if data is None:
        path.unlink(missing_ok=True)
        return
    temporary=path.with_name(path.name+'.'+uuid.uuid4().hex+'.tmp')
    try:
        temporary.write_bytes(data)
        os.replace(temporary,path)
    finally:
        temporary.unlink(missing_ok=True)


class Atlas:
    """One immutable cache capture and a separate draft workspace. Author: lare96."""

    def __init__(self, cache, state=None, project_root=None, overrides=None, shared=None):
        self.state = Path(state or ROOT / '.local').resolve()
        source = Path(cache).resolve()
        if self.state == source or source in self.state.parents:
            raise ValueError('Draft workspace must be outside the source cache.')
        self.source = str(source)
        if shared is None:
            self.world = World(source,cache_dir=self.workspace_dir('decoded'))
            self.planner = Planner(self.world)
            self.renderer = Renderer(self.world,self.workspace_dir('tiles'))
        else:
            self.world,self.planner,self.renderer=shared[:3]
        self.lock = threading.RLock()
        server=Path(project_root).resolve() if project_root else next((p for p in source.parents if (p/'src/main/kotlin/api/api/bot/zone/SubZone.kt').is_file()),None)
        self.overrides=dict(overrides or {})
        if server:
            self.project=discover(server,self.overrides)
            self.catalog_data=build_catalog(server,self.overrides)
        else:
            self.project={'usable':False,'root':None,'name':source.name,'resources':{'cache':str(source)},
                          'diagnostics':[{'severity':'warning','code':'CACHE_ONLY','message':'A cache is open without a Luna source directory. Open Luna Directory to load registered zones.'}]}
            self.catalog_data={'entries':[],'diagnostics':self.project['diagnostics'],'sourceHash':''}
        self.project_identity=hashlib.sha256(str(server or source).casefold().encode()).hexdigest()[:24]
        self.project.update(identity=self.project_identity)
        previous_entities=shared[3] if shared and len(shared)>3 else None
        self.entities=(previous_entities.rebind(self.project,self.catalog_data) if previous_entities
                       else EntityCatalog(self.world,self.project,self.catalog_data))
        self.locations=self.entities.locations
        self.search_index=SearchIndex(self.world,self.planner,self.locations,self.catalog_data)
        self.project.setdefault('diagnostics',[]).extend(self.entities.diagnostics)
        for warning in getattr(self.renderer,'diagnostics',[]):
            self.project.setdefault('diagnostics',[]).append({'severity':'warning','code':'IMAGERY_FALLBACK','message':str(warning)})
        self.dataset_identity=hashlib.sha256((json.dumps(self.world.fingerprints,sort_keys=True)+(self.catalog_data.get('sourceHash') or '')+self.entities.sourceHash).encode()).hexdigest()[:24]
        self.existing={'zones':[e for e in self.catalog_data.get('entries',[]) if e.get('kind')=='subzone'],
                       'available':bool(server),'unparsed':[e['id'] for e in self.catalog_data.get('entries',[]) if e.get('kind')=='subzone' and not e.get('parts')]}
        self._migrate_drafts()

    def project_dir(self):
        target=(self.workspace_dir('projects')/self.project_identity).resolve()
        if not target.is_relative_to(self.state) or target.is_relative_to(Path(self.source)):
            raise ValueError('Project settings must stay inside the atlas workspace.')
        return target

    def _migrate_drafts(self):
        # Legacy drafts belong to their original project, even for shared caches.
        legacy=self.workspace_dir('drafts')
        marker=self.project_dir()/'legacy-drafts-imported.json'
        paths=list(legacy.glob('*.json'))
        if not paths:
            return
        owner_path=self.state/'legacy-draft-owner.json'
        if owner_path.exists():
            try:
                owner_record=json.loads(owner_path.read_text(encoding='utf-8'))
                if not isinstance(owner_record,dict) or not isinstance(owner_record.get('projectIdentity'),str):
                    raise ValueError('Invalid migration owner record.')
                owner=owner_record['projectIdentity']
            except (OSError,ValueError) as error:
                self.project.setdefault('diagnostics',[]).append({'severity':'warning','code':'LEGACY_DRAFT_OWNER_UNREADABLE',
                    'message':'Legacy drafts were left unchanged because their project attribution could not be read: '+str(error)})
                return
        else:
            record_path=self.state/'project.json'
            try:
                record=json.loads(record_path.read_text(encoding='utf-8')) if record_path.exists() else {}
            except (OSError,ValueError):
                record={}
            origin=record.get('directory')
            if not origin:
                origin_cache=Path(record.get('cache') or self.source).resolve()
                origin=next((p for p in origin_cache.parents if (p/'src/main/java/io/luna/Luna.java').is_file()),origin_cache)
            owner=hashlib.sha256(str(Path(origin).resolve()).casefold().encode()).hexdigest()[:24]
            atomic_json(owner_path,{'projectIdentity':owner,'origin':str(origin)})
        if owner!=self.project_identity:
            return
        imported=[]
        for path in paths:
            if path.is_symlink():
                continue
            try:
                data=json.loads(path.read_text(encoding='utf-8'))
            except (OSError,ValueError):
                continue
            if data.get('fingerprints')!=self.world.fingerprints:
                continue
            target=self.draft_root()/path.name
            if not target.exists():
                atomic_json(target,dict(data,projectIdentity=self.project_identity))
                imported.append(path.name)
        atomic_json(marker,{'imported':imported})

    def workspace_dir(self,name):
        target=(self.state/name).resolve()
        if not target.is_relative_to(self.state) or target==Path(self.source) or target.is_relative_to(Path(self.source)):
            raise ValueError('Draft directory must stay inside the separate atlas workspace.')
        return target

    def draft_root(self):
        target=(self.project_dir()/'drafts').resolve()
        if not target.is_relative_to(self.state) or target.is_relative_to(Path(self.source)):
            raise ValueError('Draft directory must stay inside the separate atlas workspace.')
        return target

    def summary(self):
        return dict(self.world.summary, cache=self.source, fingerprints=self.world.fingerprints,
                    project=self.project,projectIdentity=self.project_identity,datasetIdentity=self.dataset_identity,
                    catalogSummary={'count':len(self.catalog_data.get('entries',[])),
                                    'sourceHash':self.catalog_data.get('sourceHash'),
                                    'diagnostics':self.catalog_data.get('diagnostics',[])},
                    npcs=self.entities.summary,npcSummary=self.entities.summary,
                    regions=[{'id':r['id'],'rx':r['rx'],'ry':r['ry']} for r in self.world.regions.values()],
                    regionCount=len(self.world.regions), existingZones=self.existing['zones'],
                    zoneCoverage={k:v for k,v in self.existing.items() if k!='zones'},
                    displayPlanes='Effective server planes: a bridge column lowers raw terrain and placements by one plane.',
                    performance={'regionMemoryCache':self.renderer._cached_region.cache_info()._asdict(),
                                 'regionDiskHits':self.renderer.disk_hits,'renderedRegions':self.renderer.rendered_regions},
                    imagery=dict(getattr(self.renderer,'metadata',{}),diagnostics=getattr(self.renderer,'diagnostics',[])),
                    center={'x':3222,'y':3218,'plane':0},
                    draftCount=len(self.drafts()['drafts']),
                    capabilities=['positions','object-search','zone-search','npc-spawns','location-context',
                                  'region-chunk-grids','rectangle-drafts','resource-clusters','static-collision'],
                    limitations=['Cache-derived overview; textures, lighting and native scene fidelity are not reproduced.',
                                 'Collision is a local static snapshot. Dynamic doors, scripts, quests and travel are unverified.',
                                 'Proposals are drafts; parent zone, entrance, banking and semantic intent need content integration.',
                                 'NPC markers are declared JSON spawn anchors; scripted spawns and live movement are not captured.'])

    def catalog(self):
        return dict(self.catalog_data,project=self.project,projectIdentity=self.project_identity,datasetIdentity=self.dataset_identity)

    def preferences(self):
        path=self.project_dir()/'preferences.json'
        try:
            prefs=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
            if not isinstance(prefs,dict):
                prefs={}
        except (OSError,ValueError):
            prefs={}
        return {'preferences':prefs,'projectIdentity':self.project_identity}

    def save_preferences(self,args):
        self.check_identity(args)
        prefs=args.get('preferences')
        if not isinstance(prefs,dict) or len(json.dumps(prefs,allow_nan=False))>100_000:
            raise ValueError('Atlas preferences must be a bounded JSON object.')
        with self.lock:
            atomic_json(self.project_dir()/'preferences.json',prefs)
        return self.preferences()

    def check_identity(self,args):
        if args.get('projectIdentity',self.project_identity)!=self.project_identity:
            raise ValueError('The Luna directory changed. Refresh this view before saving.')

    def saved_project(self):
        root=self.project.get('root')
        overrides={}
        for key,value in self.overrides.items():
            path=Path(value).resolve() if Path(value).is_absolute() else (Path(root)/value).resolve()
            overrides[key]=str(path.relative_to(root)) if root and path.is_relative_to(Path(root)) else str(path)
        return {'schema':2,'directory':root,'cache':self.source,'overrides':overrides,
                'resources':self.project.get('relativePaths',{}),'fingerprints':self.world.fingerprints}

    @staticmethod
    def bounds(args):
        result = {key:integer(args[key],key) for key in ('minX','minY','maxX','maxY')}
        result['plane'] = integer(args.get('plane',0),'plane',0,3)
        if result['minX'] > result['maxX'] or result['minY'] > result['maxY']:
            raise ValueError('Area corners are inverted.')
        if result['maxX']-result['minX'] >= 512 or result['maxY']-result['minY'] >= 512:
            raise ValueError('Area requests are limited to 512 by 512 tiles.')
        return result

    def position(self, args):
        x, y = integer(args.get('x'),'x'), integer(args.get('y'),'y')
        plane = integer(args.get('plane',0),'plane',0,3)
        tile = self.world.tile(x,y,plane)
        if tile is None:
            raise ValueError('This position is absent from the selected cache.')
        result=dict(tile)
        fields=('flags','underlay','overlay','shape','rotation')
        result['rawTerrain']={key:tile[key] for key in fields}
        raw_plane=plane+bool(tile.get('bridgeColumn'))
        result['terrainSourcePlane']=raw_plane if raw_plane<4 else None
        if raw_plane<4:
            region=self.world.regions[tile['region']]
            index=raw_plane*4096+(x&63)*64+(y&63)
            result.update({key:region[key][index] for key in fields})
        else:
            result.update({key:0 for key in fields})
        result['blocked']=raw_plane>3 or bool(result['flags']&1)
        result['roof']=bool(result['flags']&4)
        result['objects']=[self.planner._summary(obj) for obj in tile.get('effectiveObjects',tile['objects'])]
        result['terrainSemantics']='Effective server plane; raw native terrain retained in rawTerrain.'
        result['staticCollision']=self.planner.position_info(x,y,plane)
        result['fingerprint']=self.world.fingerprints['main_file_cache.dat']
        result['zones']=self.containing(x,y,plane)['entries']
        result['context']=self.locations.context(x,y,plane)
        result['grid']={'region':grid_at(x,y,plane,'region'),'chunk':grid_at(x,y,plane,'chunk')}
        return result

    def containing(self,x,y,plane):
        return {'entries':[{'id':e['id'],'name':e['name'],'kind':e['kind'],'activation':e.get('activation',{})}
                           for e in self.catalog_data.get('entries',[]) if contains(e,x,y,plane)],
                'projectIdentity':self.project_identity}

    def grid(self,args):
        info=grid_at(integer(args.get('x'),'x'),integer(args.get('y'),'y'),
                     integer(args.get('plane',0),'plane',0,3),str(args.get('kind','region')))
        preferences=self.preferences()['preferences'].get('grids',{})
        preferences=preferences if isinstance(preferences,dict) else {}
        key='customRegionLabels' if info['kind']=='region' else 'customChunkLabels'
        info['customLabel']=clean_custom_labels(preferences.get(key,{}),info['kind']).get(info['key'],'')
        info['projectIdentity']=self.project_identity
        return info

    def npcs(self,args):
        plane=integer(args['plane'],'plane',0,3) if args.get('plane') not in (None,'') else None
        bounds=self.search_bounds(args)
        limit=integer(args['limit'],'limit',1,100000) if 'limit' in args else None
        result=self.entities.data(plane,bounds,limit,contexts=False)
        result['projectIdentity']=self.project_identity
        result['datasetIdentity']=self.dataset_identity
        return result

    def npc(self,args):
        key=args.get('id') or args.get('instanceId')
        if not isinstance(key,str) or len(key)>120:
            raise ValueError('Select a valid NPC placement identifier.')
        result=self.entities.inspect(key)
        if result is None:
            raise ValueError('This NPC placement is not in the loaded source snapshot.')
        return dict(result,projectIdentity=self.project_identity)

    def search_bounds(self,args):
        keys=('minX','minY','maxX','maxY')
        if any(k in args for k in keys) and not all(k in args for k in keys):
            raise ValueError('Supply all four search bounds or none.')
        if all(k in args for k in keys):
            b=self.bounds({k:args[k] for k in keys})
            return tuple(b[k] for k in keys)
        return None

    def search(self, args):
        name = str(args.get('name','')).strip()
        if len(name)>100:
            raise ValueError('Search text is limited to 100 characters.')
        explicit='kind' in args
        kind=str(args.get('kind','objects'))
        if kind not in ('zones','npcs','objects'):
            raise ValueError('Search kind must be zones, npcs or objects.')
        value=args.get('plane',None if explicit else 0)
        plane=integer(value,'plane',0,3) if value not in (None,'') else None
        limit = integer(args.get('limit',200),'limit',1,2000)
        bounds=self.search_bounds(args)
        unplaced=args.get('includeUnplaced',False)
        unplaced=unplaced is True or str(unplaced).lower() in ('1','true')
        if kind=='zones':
            hits=self.search_index.zones(name,plane,bounds,limit+1)
        elif kind=='npcs':
            hits=self.entities.search(name,plane,bounds,limit+1,include_unplaced=unplaced)
        else:
            hits=self.search_index.objects(name,plane,bounds,limit+1,include_unplaced=unplaced)
        result={'results':hits[:limit],'objects':hits[:limit] if kind=='objects' else [],
                'truncated':len(hits)>limit,'kind':kind,'plane':plane,'projectIdentity':self.project_identity,
                'source':'Luna source and selected cache placements','fingerprints':self.world.fingerprints}
        return result

    def propose(self, args):
        options=dict(args, **self.bounds(args))
        if not isinstance(options.get('name'),str) or not options['name'].strip():
            raise ValueError('Supply an object name, ID or action to define the activity.')
        for key, default, low, high in [('radius',8,1,32),('padding',2,1,16),('minObjects',2,1,100)]:
            options[key]=integer(options.get(key,default),key,low,high)
        if 'actionableOnly' in options and type(options['actionableOnly']) is not bool:
            raise ValueError('actionableOnly must be a boolean.')
        if 'action' in options and (not isinstance(options['action'],str) or len(options['action'])>100):
            raise ValueError('Action filter must be text of at most 100 characters.')
        result=self.planner.propose(options)
        result['fingerprints']=self.world.fingerprints
        for candidate in result['candidates']:
            candidate['fingerprints']=self.world.fingerprints
            candidate['projectIdentity']=self.project_identity
        return result

    def map_png(self,args):
        bounds=self.bounds(args)
        scale=integer(args.get('scale',4),'scale',1,8)
        min_x,min_y,max_x,max_y=(bounds[k] for k in ('minX','minY','maxX','maxY'))
        image=Image.new('RGB',((max_x-min_x+1)*8,(max_y-min_y+1)*8),(23,29,30))
        for rx in range(min_x>>6,(max_x>>6)+1):
            for ry in range(min_y>>6,(max_y>>6)+1):
                if (rx<<8)|ry not in self.world.regions:
                    continue
                region=Image.open(BytesIO(self.renderer.region_png(bounds['plane'],rx,ry)))
                image.paste(region,((rx*64-min_x)*8,(max_y-(ry*64+63))*8))
        if scale!=8:
            image=image.resize(((max_x-min_x+1)*scale,(max_y-min_y+1)*scale),Image.Resampling.NEAREST)
        drafts=self.drafts()['drafts'] if str(args.get('zones',1)).lower() not in ('0','false') else []
        image=draw_layers(image,bounds,scale,getattr(self,'catalog_data',{}),drafts,
                          getattr(self,'entities',None),args,self.world)
        buffer=BytesIO()
        image.save(buffer,format='PNG')
        return buffer.getvalue()

    def render(self,args):
        data=self.map_png(args)
        target=self.workspace_dir('previews')/(uuid.uuid4().hex+'.png')
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(data)
        return {'path':str(target),'bounds':self.bounds(args),'scale':integer(args.get('scale',4),'scale',1,8),
                'fingerprints':self.world.fingerprints,'displayPlanes':'effective server planes',
                'rendering':'Cache-derived overview with native shaped overlays; textures, lighting and scene models are simplified.'}

    def drafts(self):
        result=[]
        with self.lock:
            for path in sorted(self.draft_root().glob('*.json')):
                if path.is_symlink():
                    raise ValueError('Draft files must be regular local files.')
                data=json.loads(path.read_text(encoding='utf-8'))
                data['baselineMatches']=data.get('fingerprints')==self.world.fingerprints
                result.append(data)
        return {'drafts':result}

    def save(self,args):
        self.check_identity(args)
        self.world.assert_sources_unchanged()
        if not isinstance(args.get('bounds'),dict):
            raise ValueError('A draft needs inclusive bounds.')
        bounds=self.bounds(args['bounds'])
        name=args.get('name','Unnamed area')
        if not isinstance(name,str) or not name.strip() or len(name)>120:
            raise ValueError('Give this area a name of 1 to 120 characters.')
        inside=args.get('inside')
        if inside is not None:
            if not isinstance(inside,dict):
                raise ValueError('Inside position must be an object.')
            inside={key:integer(inside.get(key,bounds['plane'] if key=='plane' else None),key,
                                0,3 if key=='plane' else 16383) for key in ('x','y','plane')}
        objects=args.get('objects',[])
        if not isinstance(objects,list) or len(objects)>5000:
            raise ValueError('Draft resources must be a list of at most 5000 object references.')
        checked=(self.planner.validate_resources(bounds,inside,objects) if objects
                 else self.planner.validate_area(bounds,inside))
        if not checked['validation']['valid']:
            raise ValueError('Area has missing map data or lacks a valid walkable inside position.')
        given=args.get('fingerprints')
        if given is not None and given!=self.world.fingerprints:
            raise ValueError('This proposal belongs to a different cache baseline. Generate it again.')
        draft_id=args.get('id') or uuid.uuid4().hex[:16]
        if not isinstance(draft_id,str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,80}',draft_id):
            raise ValueError('Draft ID contains invalid characters.')
        draft=dict(checked,id=draft_id,name=name.strip(),source=args.get('source','atlas'),
                   draft=True,semanticsVerified=False,fingerprints=self.world.fingerprints,
                   projectIdentity=self.project_identity,
                   createdAt=datetime.now(timezone.utc).isoformat())
        draft.setdefault('objects',[])
        with self.lock:
            atomic_json(self.draft_root()/(draft_id+'.json'),draft)
        return draft

    def execute(self,op,args):
        if op=='npcs':
            return self.npcs(args)
        if op=='npc':
            return self.npc(args)
        if op=='grid':
            return self.grid(args)
        if op=='catalog':
            return self.catalog()
        if op=='preferences':
            return self.preferences()
        if op=='contains':
            return self.containing(integer(args.get('x'),'x'),integer(args.get('y'),'y'),integer(args.get('plane',0),'plane',0,3))
        if op=='summary':
            return self.summary()
        if op=='position':
            return self.position(args)
        if op=='search':
            return self.search(args)
        if op=='propose':
            return self.propose(args)
        if op=='drafts':
            return self.drafts()
        if op=='save':
            return self.save(args)
        if op=='validate':
            bounds=self.bounds(args.get('bounds',args))
            result=self.planner.validate_area(bounds,args.get('inside'))
            result['fingerprints']=self.world.fingerprints
            result['projectIdentity']=self.project_identity
            return result
        if op=='render':
            return self.render(args)
        raise ValueError('Unknown operation: '+op)


class EmptyAtlas:
    """Welcome workspace before a valid project has been selected. Author: lare96."""

    def __init__(self,state,diagnostics=None):
        self.state=Path(state).resolve()
        self.source=None
        self.world=None
        self.project_identity='welcome'
        self.overrides={}
        self.project={'usable':False,'root':None,'name':'Open a Luna directory','identity':'welcome',
                      'resources':{},'diagnostics':diagnostics or []}

    def summary(self):
        return {'regions':[],'objects':0,'fingerprints':{},'project':self.project,'projectIdentity':'welcome',
                'datasetIdentity':'welcome','existingZones':[],'center':{'x':3222,'y':3218,'plane':0},
                'catalogSummary':{'count':0,'diagnostics':self.project['diagnostics']}}

    def execute(self,op,args):
        if op=='summary':
            return self.summary()
        if op=='catalog':
            return {'project':self.project,'projectIdentity':'welcome','entries':[],'diagnostics':self.project['diagnostics'],'sourceHash':''}
        if op=='preferences':
            return {'projectIdentity':'welcome','preferences':{}}
        if op=='drafts':
            return {'drafts':[]}
        if op=='npcs':
            return {'spawns':[],'summary':{'spawns':0,'definitions':0},'diagnostics':self.project['diagnostics'],
                    'sourceHash':None,'projectIdentity':'welcome'}
        raise ValueError('Open a Luna directory before using map tools.')

    def save_preferences(self,args):
        return self.execute('preferences',{})

    def map_png(self,args):
        raise ValueError('Open a Luna directory before exporting a map.')


class AtlasServer(ThreadingHTTPServer):
    """Local-only desktop and agent transport. Author: lare96."""
    daemon_threads=True

    def __init__(self,atlas,port):
        self.atlas=atlas
        self.project_lock=threading.Lock()
        super().__init__(('127.0.0.1',port),Handler)
        self.record_endpoint()

    def record_endpoint(self,atlas=None):
        atlas=atlas or self.atlas
        atomic_json(atlas.state/'endpoint.json',{'pid':os.getpid(),'port':self.server_port,
                                               'cache':atlas.source,'fingerprints':atlas.world.fingerprints if atlas.world else {},
                                               'projectIdentity':atlas.project_identity})

    def open_project(self,args,reload=False):
        with self.project_lock:
            old=self.atlas
            directory=args.get('directory') or (old.project.get('root') if reload else None)
            if not isinstance(directory,str) or not directory.strip():
                return {'loaded':False,'project':{'usable':False,'diagnostics':[{'severity':'error','code':'DIRECTORY_REQUIRED','message':'Choose a Luna directory.'}]}}
            overrides=args.get('overrides',old.overrides if reload else {})
            report=discover(directory,overrides)
            if not report.get('usable'):
                return {'loaded':False,'project':report}
            cache=report.get('resources',{}).get('cache')
            if not cache:
                return {'loaded':False,'project':report}
            try:
                shared=None
                if old.world and Path(cache).resolve()==Path(old.source):
                    try:
                        old.world.assert_sources_unchanged()
                        shared=(old.world,old.planner,old.renderer,getattr(old,'entities',None))
                    except ValueError:
                        pass
                next_atlas=Atlas(cache,old.state,report['root'],overrides,shared)
                summary=next_atlas.summary()
                project_path,endpoint_path=old.state/'project.json',old.state/'endpoint.json'
                previous_project=project_path.read_bytes() if project_path.exists() else None
                previous_endpoint=endpoint_path.read_bytes() if endpoint_path.exists() else None
                try:
                    atomic_json(project_path,next_atlas.saved_project())
                    self.record_endpoint(next_atlas)
                except (OSError,ValueError,TypeError):
                    restore_file(project_path,previous_project)
                    restore_file(endpoint_path,previous_endpoint)
                    raise
            except (ValueError,OSError,KeyError,TypeError) as error:
                report.setdefault('diagnostics',[]).append({'severity':'error','code':'PROJECT_LOAD_FAILED','message':str(error)})
                return {'loaded':False,'project':report}
            self.atlas=next_atlas
            return {'loaded':True,'project':next_atlas.project,'summary':summary}

    def server_close(self):
        super().server_close()
        endpoint=self.atlas.state/'endpoint.json'
        try:
            current=json.loads(endpoint.read_text(encoding='utf-8'))
            if current.get('pid')==os.getpid() and current.get('port')==self.server_port:
                endpoint.unlink()
        except (OSError,ValueError):
            pass


class Handler(BaseHTTPRequestHandler):
    """Bounded JSON API; no cache or gameplay writers. Author: lare96."""

    def log_message(self,fmt,*args):
        pass

    def send(self,data,status=200,content_type='application/json'):
        raw=data if isinstance(data,bytes) else json.dumps(data,ensure_ascii=True).encode()
        self.send_response(status)
        self.send_header('Content-Type',content_type)
        self.send_header('Content-Length',str(len(raw)))
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('Cache-Control','no-store')
        self.end_headers()
        self.wfile.write(raw)

    def local_request(self):
        host=self.headers.get('Host','').split(':')[0]
        if host not in ('127.0.0.1','localhost'):
            raise ValueError('Only local atlas requests are accepted.')
        origin=self.headers.get('Origin')
        if origin and origin not in (f'http://127.0.0.1:{self.server.server_port}',f'http://localhost:{self.server.server_port}'):
            raise ValueError('Foreign browser origins are not accepted.')

    def do_GET(self):
        try:
            self.local_request()
            url=urlparse(self.path)
            args={k:values[-1] for k,values in parse_qs(url.query).items()}
            atlas=self.server.atlas
            if url.path=='/api/map.png':
                return self.send(atlas.map_png(args),content_type='image/png')
            match=re.fullmatch(r'/tiles/([0-3])/(\d{1,3})/(\d{1,3})\.png',url.path)
            if match:
                plane,rx,ry=map(int,match.groups())
                integer(rx,'rx',0,255); integer(ry,'ry',0,255)
                if not atlas.world:
                    raise ValueError('Open a Luna directory first.')
                return self.send(atlas.renderer.region_png(plane,rx,ry),content_type='image/png')
            op={'/api/summary':'summary','/api/position':'position','/api/search':'search',
                '/api/drafts':'drafts','/api/catalog':'catalog','/api/preferences':'preferences','/api/contains':'contains',
                '/api/npcs':'npcs','/api/npc':'npc','/api/grid':'grid'}.get(url.path)
            if op:
                return self.send(atlas.execute(op,args))
            if url.path=='/':
                return self.send({'name':'Luna Atlas 377','desktop':'Run Start-Atlas.cmd',
                                  'operations':['summary','position','search','propose','validate','drafts','save']})
            self.send({'error':'Unknown atlas endpoint.'},404)
        except (ValueError,KeyError,TypeError) as error:
            self.send({'error':str(error)},400)
        except Exception as error:
            print(f'Atlas request failed: {error}',file=sys.stderr)
            self.send({'error':'Atlas request failed. See local diagnostics.'},500)

    def do_POST(self):
        try:
            self.local_request()
            if self.headers.get('Content-Type','').split(';')[0]!='application/json':
                raise ValueError('Use application/json requests.')
            size=integer(self.headers.get('Content-Length',0),'Content-Length',1,1_000_000)
            args=json.loads(self.rfile.read(size))
            if not isinstance(args,dict):
                raise ValueError('Request must be a JSON object.')
            if self.path=='/api/project/discover':
                return self.send(discover(args.get('directory',''),args.get('overrides',{})))
            if self.path in ('/api/project/open','/api/project/reload'):
                return self.send(self.server.open_project(args,self.path.endswith('/reload')))
            atlas=self.server.atlas
            if self.path=='/api/preferences':
                return self.send(atlas.save_preferences(args))
            op={'/api/propose':'propose','/api/validate':'validate','/api/drafts':'save','/api/render':'render'}.get(self.path)
            if op is None:
                return self.send({'error':'Unknown atlas endpoint.'},404)
            self.send(atlas.execute(op,args))
        except (ValueError,KeyError,TypeError) as error:
            self.send({'error':str(error)},400)
        except Exception as error:
            print(f'Atlas request failed: {error}',file=sys.stderr)
            self.send({'error':'Atlas request failed. See local diagnostics.'},500)


def load_selected(state,cache=None,directory=None,welcome=False):
    state=Path(state)
    try:
        path=state/'project.json'
        record=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
        if not isinstance(record,dict):
            raise ValueError('Atlas project settings must be a JSON object.')
        if cache is not None and directory is None:
            return Atlas(cache,state)
        root=str(Path(directory).expanduser().resolve()) if directory is not None else record.get('directory')
        if root:
            root=str(Path(root).expanduser().resolve())
            same_root=record.get('directory') and Path(root)==Path(record['directory']).expanduser().resolve()
            overrides=dict(record.get('overrides',{})) if same_root else {}
            if cache is not None:
                overrides['cache']=str(cache)
            report=discover(root,overrides)
            if not report.get('usable'):
                messages=[d.get('message','') for d in report.get('diagnostics',[]) if d.get('severity')=='error']
                raise ValueError('; '.join(messages) or 'Resolve the Luna directory paths before loading.')
            return Atlas(report['resources']['cache'],state,report['root'],overrides)
        if record.get('cache'):
            return Atlas(record['cache'],state)
        if not welcome:
            raise ValueError('Select --directory, --cache, or open a Luna directory in the desktop app.')
        return EmptyAtlas(state)
    except (OSError,ValueError,KeyError,TypeError) as error:
        if not welcome:
            raise
        return EmptyAtlas(state,[{'severity':'error','code':'SAVED_PROJECT_UNAVAILABLE','message':str(error)}])


def main():
    parser=argparse.ArgumentParser(description='Luna 377 cache atlas and local area authoring.')
    parser.add_argument('--cache',type=Path)
    parser.add_argument('--directory',type=Path,help='Luna project root; discover its native cache and registered zones.')
    parser.add_argument('--state',type=Path,default=ROOT/'.local')
    parser.add_argument('--port',type=int,default=8791)
    parser.add_argument('operation',choices=['summary','catalog','contains','preferences','npcs','npc','grid','position','search','propose','validate','drafts','save','render','serve','desktop','mcp'])
    parser.add_argument('--json',default='{}',help='JSON request or @path/to/request.json')
    options=parser.parse_args()
    atlas=load_selected(options.state,options.cache,options.directory,options.operation=='desktop')
    if options.operation=='mcp':
        from atlas_mcp import run
        return run(atlas)
    if options.operation in ('serve','desktop'):
        server=AtlasServer(atlas,options.port)
        print(f'Luna Atlas ready at http://127.0.0.1:{server.server_port}',flush=True)
        if options.operation=='serve':
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
            finally:
                server.server_close()
        else:
            thread=threading.Thread(target=server.serve_forever,daemon=True)
            thread.start()
            try:
                import desktop
                desktop.main(f'http://127.0.0.1:{server.server_port}')
            finally:
                server.shutdown()
                server.server_close()
        return
    raw=options.json
    request=json.loads(Path(raw[1:]).read_text(encoding='utf-8')) if raw.startswith('@') else json.loads(raw)
    if not isinstance(request,dict):
        raise ValueError('Request must be a JSON object.')
    print(json.dumps(atlas.execute(options.operation,request),ensure_ascii=True,indent=2))


if __name__=='__main__':
    try:
        main()
    except Exception as failure:
        print(json.dumps({'error':str(failure)}),file=sys.stderr)
        sys.exit(1)
