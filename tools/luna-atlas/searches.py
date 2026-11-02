"""Placed-object and zone search with shared effective location context. Author: lare96."""
from functools import lru_cache
import threading

from cache377 import LazyPlacements, PLACEMENT_RECORD, SERVER_PLANE_CORRECTIONS
from geometry import bounds_for_plane
from planner import _matches


class SearchIndex:
    """One immutable world's search adapters; no source or gameplay writes. Author: lare96."""

    def __init__(self,world,planner,locations,catalog):
        self.world,self.planner,self.locations,self.catalog=world,planner,locations,catalog
        self._placed_ids=None
        self._lock=threading.RLock()
        self.definition_ids=lru_cache(maxsize=128)(self._definition_ids)

    def _definition_ids(self,query):
        return frozenset(i for i,d in self.world.definitions.items() if _matches(query,{'id':i},d))

    def placed_ids(self):
        with self._lock:
            if self._placed_ids is None:
                ids=set()
                for region in self.world.regions.values():
                    placements=region['objects']
                    if isinstance(placements,LazyPlacements):
                        # The validated warm-cache record is u16 ID/u16 position/u8 type.
                        # Reading it avoids materializing 1.37 million dictionaries.
                        ids.update(record[0] for record in PLACEMENT_RECORD.iter_unpack(placements._raw))
                    else:
                        ids.update(obj['id'] for obj in placements)
                self._placed_ids=frozenset(ids)
            return self._placed_ids

    def objects(self,name='',plane=None,bounds=None,limit=200,include_unplaced=False):
        query=str(name).strip().casefold()
        ids=self.definition_ids(query)
        if not ids:
            return []
        result=[]
        for region_id,region in sorted(self.world.regions.items()):
            if bounds and (region['rx']*64>bounds[2] or region['rx']*64+63<bounds[0]
                           or region['ry']*64>bounds[3] or region['ry']*64+63<bounds[1]):
                continue
            for index,obj in self._matching_placements(region,ids):
                effective=obj.get('effectivePlane',obj['plane'])
                if effective<0 or (plane is not None and effective!=plane):
                    continue
                if bounds and not (bounds[0]<=obj['x']<=bounds[2] and bounds[1]<=obj['y']<=bounds[3]):
                    continue
                row=self.planner._summary(obj)
                row.update(kind='object',objectId=obj['id'],instanceId=f'object-{region_id}-{index}',definitionOnly=False)
                result.append(self.locations.annotate(row))
                if len(result)>=limit:
                    return result
        if include_unplaced and bounds is None:
            placed=self.placed_ids()
            for ident in sorted(ids-placed):
                definition=self.world.definitions[ident]
                result.append(self.locations.annotate({'kind':'object','id':ident,'objectId':ident,
                    'instanceId':f'object-definition-{ident}','name':definition['name'],
                    'x':None,'y':None,'plane':None,'definitionOnly':True,'actions':definition.get('actions',[])}))
                if len(result)>=limit:
                    break
        return result

    @staticmethod
    def _matching_placements(region,ids):
        placements=region['objects']
        if not isinstance(placements,LazyPlacements):
            yield from ((i,obj) for i,obj in enumerate(placements) if obj['id'] in ids)
            return
        # Filter the validated compact records first. Sparse searches should not
        # allocate all world placements or evict the renderer's nearby regions.
        for index,(ident,packed,info) in enumerate(PLACEMENT_RECORD.iter_unpack(placements._raw)):
            if ident not in ids:
                continue
            raw_plane=packed>>12
            corrected=0 if raw_plane==1 and ident in SERVER_PLANE_CORRECTIONS else raw_plane
            bridged=bool(region['flags'][4096+(packed&4095)]&2)
            yield index,{'id':ident,'x':region['rx']*64+((packed>>6)&63),
                         'y':region['ry']*64+(packed&63),'plane':corrected,'rawPlane':raw_plane,
                         'effectivePlane':corrected-int(bridged),'type':info>>2,'rotation':info&3}

    def zones(self,name='',plane=None,bounds=None,limit=200):
        query=str(name).strip().casefold()
        entries=self.catalog.get('entries',[])
        by_id={e['id']:e for e in entries}
        result=[]
        for entry in entries:
            if entry.get('kind') not in ('zone','subzone'):
                continue
            parent=by_id.get(entry.get('parentId'))
            options=[by_id[p]['name'] for p in entry.get('parentOptions',[]) if p in by_id]
            dynamic=not parent and (len(options)>1 or 'runtime-dependent' in entry.get('parentRelationship','').lower())
            parent_name=(parent['name'] if parent else
                         ('Dynamic parent'+(' ('+' / '.join(options)+')' if options else '') if dynamic else
                          ' / '.join(options)))
            haystack=' '.join(str(v) for v in (entry['name'],entry['id'],entry.get('identifier',''),parent_name)).casefold()
            if query and query not in haystack and query.replace(' ','_') not in haystack:
                continue
            levels=entry.get('planes')
            if plane is not None and levels is not None and plane not in levels:
                continue
            preferred=plane if plane is not None else (entry.get('inside') or {}).get('plane',levels[0] if levels else 0)
            if levels and preferred not in levels:
                preferred=levels[0]
            extent=bounds_for_plane(entry,preferred)
            if bounds and (not extent or extent['maxX']<bounds[0] or extent['minX']>bounds[2]
                           or extent['maxY']<bounds[1] or extent['minY']>bounds[3]):
                continue
            label=parent_name+' → '+entry['name'] if parent_name else entry['name']
            warnings=[] if extent else ['Source boundary could not be resolved.']
            if dynamic:
                warnings.append('The travel parent depends on runtime state; no fixed parent is inferred.')
            registered=entry.get('activation',{}).get('status')=='registered'
            if not registered:
                warnings.append('Source registration or activation is unresolved or conditional.')
            context={'status':'known' if extent and registered else 'unknown','label':label,'dynamicParent':dynamic,
                     'matches':[{'id':entry['id'],'name':entry['name'],'kind':entry['kind']}],
                     'warnings':warnings}
            result.append({'id':entry['id'],'catalogId':entry['id'],'instanceId':entry['id'],
                'name':entry['name'],'kind':entry['kind'],'parentId':entry.get('parentId'),
                'parentName':parent_name,'parentOptions':entry.get('parentOptions',[]),
                'parentRelationship':entry.get('parentRelationship'),'parentExpression':entry.get('parentExpression'),
                'planes':levels,'plane':preferred,'bounds':extent,'source':entry.get('source'),
                'activation':entry.get('activation',{}),'locationLabel':label,'context':context,
                'x':None,'y':None,'definitionOnly':False})
        result.sort(key=lambda r:(query not in (r['name'].casefold(),r['id'].casefold()),
                                  not r['name'].casefold().startswith(query),r['kind']!='zone',r['name']))
        return result[:limit]
