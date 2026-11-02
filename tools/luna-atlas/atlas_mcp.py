"""Dependency-free MCP stdio access to the atlas core. Author: lare96."""
import json
import sys

NUMBER = {'type':'integer','minimum':0,'maximum':16383}
PLANE = {'type':'integer','minimum':0,'maximum':3}
BOUNDS = {key:NUMBER for key in ('minX','minY','maxX','maxY')}
PROTOCOLS = ('2024-11-05','2025-03-26','2025-06-18','2025-11-25')


def definition(name,description,properties,required=(),write=False):
    return {'name':'luna_atlas_'+name,'description':description,
            'inputSchema':{'type':'object','properties':properties,'required':list(required),'additionalProperties':False},
            'annotations':{'readOnlyHint':not write,'destructiveHint':False,'idempotentHint':not write,'openWorldHint':False}}


TOOLS = [
    definition('summary','Inspect the selected 377 cache fingerprint, complete map coverage and known limits.',{}),
    definition('catalog','Read Luna registered bot zones, subzones and player-area controllers, geometry, hierarchy, source evidence and runtime uncertainty.',{}),
    definition('contains','List catalog zones whose effective static geometry contains a tile. Registration and runtime eligibility remain distinct.',
               {'x':NUMBER,'y':NUMBER,'plane':PLANE},('x','y')),
    definition('position','Read exact terrain, placements and static collision at a world tile.',
               {'x':NUMBER,'y':NUMBER,'plane':PLANE},('x','y')),
    definition('search','Find source zones/subzones, declared NPC placements, or cache object placements. Results include exact placement and effective zone hierarchy; omitted kind retains legacy plane-0 object search.',
               dict(BOUNDS,name={'type':'string'},kind={'type':'string','enum':['zones','npcs','objects']},
                    includeUnplaced={'type':'boolean'},plane=PLANE,limit={'type':'integer','minimum':1,'maximum':2000})),
    definition('npcs','Read declared JSON NPC spawn anchors, distinct from live positions and cache definitions. Defaults to 200 markers; narrow by plane/bounds.',
               dict(BOUNDS,plane=PLANE,limit={'type':'integer','minimum':1,'maximum':2000})),
    definition('npc','Inspect one exact NPC spawn placement, options and source-backed zone context.',
               {'id':{'type':'string'}},('id',)),
    definition('grid','Inspect native 64-tile region or 8-tile chunk bounds and coordinates, including any project display label. Hashes are not coordinate IDs.',
               {'x':NUMBER,'y':NUMBER,'plane':PLANE,'kind':{'type':'string','enum':['region','chunk']}},('x','y')),
    definition('propose','Generate draft activity subzones around matching objects. Uses local static collision; does not verify live doors, global routes or semantic intent.',
               dict(BOUNDS,name={'type':'string'},plane=PLANE,radius={'type':'integer','minimum':1,'maximum':32},
                    padding={'type':'integer','minimum':1,'maximum':16},minObjects={'type':'integer','minimum':1,'maximum':100},
                    action={'type':'string'},actionableOnly={'type':'boolean'}),
               ('name','minX','minY','maxX','maxY')),
    definition('validate','Check inclusive rectangular bounds and select or verify a walkable inside tile.',
               dict(BOUNDS,plane=PLANE,inside={'type':'object','properties':{'x':NUMBER,'y':NUMBER,'plane':PLANE},'required':['x','y']}),
               ('minX','minY','maxX','maxY')),
    definition('drafts','List separately saved subzone drafts and whether their cache baseline still matches.',{}),
    definition('render','Render a north-up bounded cache overview to a local PNG for visual inspection. Both bounds are inclusive. Maximum512 by512 tiles.',
               dict(BOUNDS,plane=PLANE,scale={'type':'integer','minimum':1,'maximum':8},zones={'type':'integer','enum':[0,1]},
                    existingZones={'type':'integer','enum':[0,1]},catalogIds={'type':'array','items':{'type':'string'},'maxItems':2000},
                    overlayOpacity={'type':'number','minimum':0,'maximum':1},labels={'type':'integer','enum':[0,1]},
                    layers={'type':'object'},labelStyles={'type':'object'},grids={'type':'object'},npcMarkers={'type':'boolean'},
                    selectedCatalogId={'type':'string'},selectedNpcId={'type':'string'},
                    labelDpi={'type':'number','minimum':48,'maximum':384}),('minX','minY','maxX','maxY'),write=True),
    definition('save','Save a validated area draft to the atlas workspace. Never modifies cache, gameplay code or live server.',
               {'id':{'type':'string'},'name':{'type':'string'},'bounds':{'type':'object','properties':dict(BOUNDS,plane=PLANE),
                    'required':['minX','minY','maxX','maxY']},'inside':{'type':'object','properties':{'x':NUMBER,'y':NUMBER,'plane':PLANE},'required':['x','y']},
                    'source':{'type':'string'},'projectIdentity':{'type':'string'},
                    'fingerprints':{'type':'object','additionalProperties':{'type':'string'}},
                    'objects':{'type':'array','items':{'type':'object'},'maxItems':5000}},('name','bounds'),write=True),
]


def dispatch(atlas,request):
    if not isinstance(request,dict) or request.get('jsonrpc')!='2.0':
        return {'jsonrpc':'2.0','id':None,'error':{'code':-32600,'message':'Invalid request'}}
    if 'id' not in request:
        return None
    response={'jsonrpc':'2.0','id':request['id']}
    try:
        method=request.get('method')
        params=request.get('params',{})
        if not isinstance(params,dict):
            raise ValueError('Parameters must be an object.')
        if method=='initialize':
            response['result']={'protocolVersion':params.get('protocolVersion') if params.get('protocolVersion') in PROTOCOLS else PROTOCOLS[-1],
                                'capabilities':{'tools':{}},'serverInfo':{'name':'luna-atlas-377','version':'0.1.0'}}
        elif method=='ping':
            response['result']={}
        elif method=='tools/list':
            response['result']={'tools':TOOLS}
        elif method=='tools/call':
            name=params.get('name')
            tool=next((t for t in TOOLS if t['name']==name),None)
            args=params.get('arguments',{})
            if tool is None or not isinstance(args,dict):
                raise ValueError('Unknown tool or invalid arguments.')
            schema=tool['inputSchema']
            if set(args)-set(schema['properties']) or set(schema['required'])-set(args):
                raise ValueError('Unexpected or missing tool arguments.')
            try:
                if name=='luna_atlas_npcs':
                    args={'limit':200,**args}
                    if type(args['limit']) is not int or not 1<=args['limit']<=2000:
                        raise ValueError('NPC marker limit must be an integer between 1 and 2000.')
                data=atlas.execute(name.removeprefix('luna_atlas_'),args)
                response['result']={'content':[{'type':'text','text':json.dumps(data)}],'structuredContent':data,'isError':False}
            except (ValueError,KeyError,TypeError) as error:
                response['result']={'content':[{'type':'text','text':str(error)}],'isError':True}
        else:
            response['error']={'code':-32601,'message':'Method not found'}
    except (ValueError,KeyError,TypeError) as error:
        response['error']={'code':-32602,'message':str(error)}
    return response


def run(atlas):
    for line in sys.stdin:
        try:
            if len(line)>1_000_000:
                raise ValueError('Request exceeds the input limit.')
            response=dispatch(atlas,json.loads(line))
        except (ValueError,UnicodeError):
            response={'jsonrpc':'2.0','id':None,'error':{'code':-32700,'message':'Invalid JSON request'}}
        if response is not None:
            print(json.dumps(response,ensure_ascii=True),flush=True)
