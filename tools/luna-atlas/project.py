"""Read-only Luna project discovery and source-backed zone catalog. Author: lare96."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re


STORES = ('main_file_cache.dat',) + tuple(f'main_file_cache.idx{i}' for i in range(5))
CONVENTIONS = {
    'cache': ('data/game/cache', 'cache'),
    'config': ('data/luna.jsonc', 'data/luna.json'),
    'zoneSource': ('src/main/kotlin/api/api/bot/zone/Zone.kt',),
    'subzoneSource': ('src/main/kotlin/api/api/bot/zone/SubZone.kt',),
    'controllers': ('src/main/kotlin/engine/engine/controllers/Controllers.kt',),
    'npcSpawns': ('data/game/world/npc_spawns.jsonc', 'data/game/world/npc_spawns.json'),
}


def _diagnostic(code, message, severity='warning', source=None):
    result = {'severity': severity, 'code': code, 'message': message}
    if source:
        result['source'] = source
    return result


def _relative(path, root):
    try:
        return Path(path).resolve().relative_to(root).as_posix()
    except ValueError:
        return str(Path(path).resolve())


def _cache_issues(path):
    try:
        return _inspect_cache(path)
    except OSError as error:
        return ['Cannot inspect native cache: '+str(error)]


def _inspect_cache(path):
    if not path.is_dir():
        return ['Folder does not exist.']
    missing = [name for name in STORES if not (path/name).is_file()]
    if missing:
        return ['Missing native stores: ' + ', '.join(missing)]
    issues = []
    if not 520 <= (path/STORES[0]).stat().st_size <= 0x3c00000:
        issues.append('Native data store is outside the supported 377 size bounds.')
    for name in STORES[1:]:
        if (path/name).stat().st_size % 6:
            issues.append(name + ' has an incomplete six-byte index entry.')
    extra = [p.name for p in path.glob('main_file_cache.*') if p.name not in STORES]
    if extra:
        issues.append('Unsupported additional stores: ' + ', '.join(extra))
    return issues


def discover(root, overrides=None):
    """Resolve known Luna resources without executing source or exposing settings. Author: lare96."""
    root = Path(root).expanduser().resolve()
    overrides = overrides or {}
    report = {'usable': False, 'root': str(root), 'path': str(root), 'name': root.name,
              'identity': hashlib.sha256(str(root).casefold().encode()).hexdigest()[:20],
              'resources': {}, 'relativePaths': {},
              'choices': {}, 'missing': [], 'diagnostics': []}
    if not root.is_dir():
        report['diagnostics'].append(_diagnostic('PROJECT_DIRECTORY', 'Select an existing Luna project directory.', 'error'))
        return report
    marker = root/'src/main/java/io/luna/Luna.java'
    if not marker.is_file() or not (root/'build.gradle.kts').is_file():
        report['diagnostics'].append(_diagnostic('NOT_LUNA_PROJECT',
            'This folder does not contain Luna.java and build.gradle.kts at the expected project paths.', 'error'))
        return report
    for key, defaults in CONVENTIONS.items():
        candidates = [(root/p).resolve() for p in defaults]
        explicit = overrides.get(key)
        if explicit:
            selected = Path(explicit).expanduser()
            selected = (root/selected).resolve() if not selected.is_absolute() else selected.resolve()
            candidates.insert(0, selected)
        candidates = list(dict.fromkeys(candidates))
        valid = [p for p in candidates if (not _cache_issues(p) if key == 'cache' else p.is_file())]
        report['choices'][key] = [str(p) for p in valid]
        if explicit:
            selected = candidates[0]
            exists = selected in valid
        else:
            selected = valid[0] if valid else candidates[0]
            exists = bool(valid)
        if exists:
            report['resources'][key] = str(selected)
            report['relativePaths'][key] = _relative(selected, root)
            if len(valid) > 1 and not explicit:
                report['diagnostics'].append(_diagnostic('RESOURCE_CHOICES',
                    ('Multiple native cache folders were found; select the cache for this project.' if key=='cache' else
                     f'Multiple {key} resources were found; the conventional first path was selected. Use path overrides to choose another.'),
                    source={'path': _relative(selected, root), 'line': 1}))
                if key == 'cache':
                    report['resources'][key] = None
                    report['missing'].append(key)
                    report['diagnostics'].append(_diagnostic('AMBIGUOUS_CACHE',
                        'Select which native cache belongs to this Luna project before loading the world.', 'error'))
        else:
            report['resources'][key] = None
            report['missing'].append(key)
            reasons = _cache_issues(selected) if key == 'cache' else ['File does not exist.']
            report['diagnostics'].append(_diagnostic('MISSING_RESOURCE',
                f'Cannot load {key}: {_relative(selected, root)}. ' + ' '.join(reasons),
                'error' if key == 'cache' else 'warning'))
    report['usable'] = report['resources'].get('cache') is not None
    return report


def _mask_comments(text):
    """Retain offsets and strings while replacing Java/Kotlin/JSONC comments."""
    out = list(text)
    i = 0
    quoted = False
    while i < len(text):
        if quoted:
            if text[i] == '\\':
                i += 2
                continue
            if text[i] == '"':
                quoted = False
            i += 1
            continue
        if text[i] == '"':
            quoted = True
            i += 1
        elif text.startswith('//', i):
            end = text.find('\n', i)
            end = len(text) if end < 0 else end
            out[i:end] = ' '*(end-i)
            i = end
        elif text.startswith('/*', i):
            end = text.find('*/', i+2)
            if end < 0:
                raise ValueError('Unterminated block comment.')
            end += 2
            out[i:end] = ['\n' if c == '\n' else ' ' for c in text[i:end]]
            i = end
        else:
            i += 1
    return ''.join(out)


def _split(text, separator=','):
    parts, start, depth, quoted, escaped = [], 0, 0, False, False
    for i, char in enumerate(text):
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char in '([{':
            depth += 1
        elif char in ')]}':
            depth -= 1
            if depth < 0:
                raise ValueError('Unbalanced source delimiters.')
        elif depth == 0 and char == separator:
            parts.append((start, text[start:i]))
            start = i+1
    if depth or quoted:
        raise ValueError('Unbalanced source delimiters or string.')
    parts.append((start, text[start:]))
    return parts


def _code_only(text):
    """Hide string literal contents before locating registration or execution code."""
    result=list(text)
    quoted,escaped=False,False
    for i,char in enumerate(text):
        if quoted:
            if char!='\n':
                result[i]=' '
            if escaped:
                escaped=False
            elif char=='\\':
                escaped=True
            elif char=='"':
                quoted=False
        elif char=='"':
            quoted=True
            result[i]=' '
    return ''.join(result)


def _all_entry_loop(text, required):
    code=_code_only(text)
    for match in re.finditer(r'for\s*\(\s*zone\s+in\s+entries\s*\)\s*\{',code):
        opening=code.find('{',match.start())
        body=code[opening+1:_closing(code,opening)]
        if all(token in body for token in required) and not re.search(r'\b(?:if|when|continue|break|return)\b',body):
            return True
    return False


def _closing(text, start):
    opening = text[start]
    closing = {'(': ')', '[': ']', '{': '}'}[opening]
    depth, quoted, escaped = 0, False, False
    for i in range(start, len(text)):
        c = text[i]
        if quoted:
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c == '"':
                quoted = False
        elif c == '"':
            quoted = True
        elif c == opening:
            depth += 1
        elif c == closing:
            depth -= 1
            if not depth:
                return i
    raise ValueError('Unterminated source expression.')


def _enum_entries(text, name):
    header = re.search(r'\benum\s+class\s+'+re.escape(name)+r'\b', text)
    if not header:
        raise ValueError(name + ' enum declaration was not found.')
    at = header.end()
    while at < len(text) and text[at].isspace():
        at += 1
    if at < len(text) and text[at] == '(':
        at = _closing(text, at)+1
    opening = text.find('{', at)
    if opening < 0:
        raise ValueError('Missing enum body.')
    body = text[opening+1:_closing(text, opening)]
    # Enum constants end at the first semicolon outside nested expressions.
    chunks = _split(body, ';')
    constants = chunks[0][1]
    result = []
    for offset, raw in _split(constants):
        match = re.match(r'\s*([A-Z][A-Z0-9_]*)\s*\(', raw)
        if not match:
            if raw.strip():
                raise ValueError('Unsupported enum entry: '+raw.strip()[:100])
            continue
        left = raw.find('(', match.start())
        right = _closing(raw, left)
        args = {}
        positional = []
        for _, arg in _split(raw[left+1:right]):
            named = re.match(r'\s*(\w+)\s*=\s*(.*)', arg, re.S)
            if named:
                args[named[1]] = named[2].strip()
            elif arg.strip():
                positional.append(arg.strip())
        absolute = opening+1+offset+match.start(1)
        result.append((match[1], args, positional, absolute))
    return result


def _integers(text):
    if not re.fullmatch(r'\s*-?\d+(?:\s*,\s*-?\d+)*\s*,?\s*', text):
        raise ValueError('Expected literal integer arguments.')
    return [int(n.strip()) for n in text.strip().rstrip(',').split(',')]


def _position(expr, settings=None):
    literal = re.fullmatch(r'Position\(([^()]*)\)', expr.strip())
    if literal:
        values = _integers(literal[1])
        if len(values) not in (2, 3):
            raise ValueError('Position requires two or three literal coordinates.')
        return {'x': values[0], 'y': values[1], 'plane': values[2] if len(values) == 3 else 0}
    if re.fullmatch(r'Luna\.settings\(\)\.game\(\)\.startingPosition\(\)', expr.strip()):
        if settings is None:
            raise ValueError('Starting position requires the selected Luna configuration.')
        game = settings.get('game', {})
        if not isinstance(game,dict):
            raise ValueError('game configuration must be an object.')
        value = game.get('starting_position')
        if not isinstance(value, dict) or type(value.get('x')) is not int or type(value.get('y')) is not int:
            raise ValueError('game.starting_position is missing or malformed.')
        return {'x': value['x'], 'y': value['y'], 'plane': int(value.get('z', value.get('plane', 0)))}
    raise ValueError('Position is not a supported literal or starting-position reference.')


def _rectangle(expr):
    match = re.fullmatch(r'(?:SimpleBoxArea|Area)\.(of|ofRadius)\(([^()]*)\)', expr.strip())
    if not match:
        raise ValueError('Boundary is not a supported literal rectangular constructor.')
    values = _integers(match[2])
    if len(values) not in (4, 5):
        raise ValueError('Rectangle requires four coordinates/dimensions and optional plane.')
    x, y, a, b = values[:4]
    if match[1] == 'ofRadius':
        a, b = x+a-1, y+b-1
    plane = values[4] if len(values) == 5 else 0
    if plane not in (-1, 0, 1, 2, 3):
        raise ValueError('Rectangle uses a plane outside -1 or 0 through 3.')
    if any(n<0 or n>16383 for n in (x,y,a,b)):
        raise ValueError('Rectangle coordinates fall outside the supported world range 0 through 16383.')
    return {'type': 'rectangle', 'minX': min(x,a), 'minY': min(y,b),
            'maxX': max(x,a), 'maxY': max(y,b), 'plane': plane}


def _region_parts(regions):
    return [{'type': 'rectangle', 'minX': (r >> 8)*64, 'minY': (r & 255)*64,
             'maxX': (r >> 8)*64+63, 'maxY': (r & 255)*64+63, 'plane': -1}
            for r in sorted(set(regions))]


def _corner_regions(area):
    return sorted({((x >> 6) << 8) | (y >> 6) for x in (area['minX'], area['maxX'])
                   for y in (area['minY'], area['maxY'])})


def _union_bounds(parts):
    if not parts:
        return None
    planes = {p['plane'] for p in parts}
    return {'minX': min(p['minX'] for p in parts), 'minY': min(p['minY'] for p in parts),
            'maxX': max(p['maxX'] for p in parts), 'maxY': max(p['maxY'] for p in parts),
            'plane': next(iter(planes)) if len(planes) == 1 else -1}


def _source(path, text, at, root):
    return {'path': _relative(path, root), 'line': text.count('\n', 0, at)+1}


def _areas(expr):
    parts = []
    for match in re.finditer(r'\b(?:SimpleBoxArea|Area)\.(?:of|ofRadius)\(', expr):
        start = expr.find('(', match.start())
        end = _closing(expr, start)
        rectangle = _rectangle(expr[match.start():end+1])
        suffix = re.match(r'\s*\.onPlanes\(([^()]*)\)', expr[end+1:])
        if suffix:
            planes = _integers(suffix[1])
            if any(p not in range(4) for p in planes):
                raise ValueError('onPlanes contains an unsupported plane.')
            parts.extend(dict(rectangle, plane=p) for p in sorted(set(planes)))
        else:
            parts.append(rectangle)
    return parts


class _Predicate:
    """Safe bounded coordinate-predicate interpreter; no source execution. Author: lare96."""
    TOKEN = re.compile(r'\s*(?:([A-Za-z_]\w*\.[xyz])|(in\b)|(-?\d+)|(\.\.|&&|\|\||==|>=|<=|>|<|\(|\)))')

    def __init__(self, expression):
        self.tokens = []
        at = 0
        while at < len(expression):
            match = self.TOKEN.match(expression, at)
            if not match:
                if expression[at:].strip():
                    raise ValueError('Coordinate predicate contains an unresolved expression: '+expression[at:at+100].strip())
                break
            self.tokens.append(next(g for g in match.groups() if g is not None))
            at = match.end()
        self.at = 0

    def take(self):
        if self.at >= len(self.tokens):
            raise ValueError('Incomplete coordinate predicate.')
        token = self.tokens[self.at]
        self.at += 1
        return token

    def peek(self):
        return self.tokens[self.at] if self.at < len(self.tokens) else None

    def parse(self):
        parts = self.union()
        if self.peek() is not None:
            raise ValueError('Unconsumed coordinate expression.')
        result = []
        for p in parts:
            planes = p['planes']
            if planes == set(range(4)):
                result.append(dict(type='rectangle', minX=p['minX'], minY=p['minY'], maxX=p['maxX'], maxY=p['maxY'], plane=-1))
            else:
                result.extend(dict(type='rectangle', minX=p['minX'], minY=p['minY'], maxX=p['maxX'], maxY=p['maxY'], plane=plane) for plane in sorted(planes))
        return result

    def union(self):
        result = self.intersection()
        while self.peek() == '||':
            self.take()
            result += self.intersection()
        return result

    def intersection(self):
        result = self.atom()
        while self.peek() == '&&':
            self.take()
            other = self.atom()
            merged = []
            for a in result:
                for b in other:
                    p = {'minX': max(a['minX'],b['minX']), 'minY': max(a['minY'],b['minY']),
                         'maxX': min(a['maxX'],b['maxX']), 'maxY': min(a['maxY'],b['maxY']),
                         'planes': a['planes'] & b['planes']}
                    if p['minX'] <= p['maxX'] and p['minY'] <= p['maxY'] and p['planes']:
                        merged.append(p)
            result = merged
        return result

    def atom(self):
        if self.peek() == '(':
            self.take()
            result = self.union()
            if self.take() != ')':
                raise ValueError('Unbalanced coordinate predicate.')
            return result
        field = self.take()
        if not re.fullmatch(r'position\.[xyz]', field):
            raise ValueError('Unsupported coordinate predicate atom.')
        axis, operator = field[-1], self.take()
        value = int(self.take())
        low, high = (0,3) if axis == 'z' else (0,16383)
        if operator == 'in':
            if self.take() != '..':
                raise ValueError('Only literal inclusive ranges are supported.')
            low, high = value, int(self.take())
        elif operator == '==':
            low = high = value
        elif operator in ('>=', '>'):
            low = value + (operator == '>')
        elif operator in ('<=', '<'):
            high = value - (operator == '<')
        else:
            raise ValueError('Unsupported coordinate comparison.')
        part = {'minX': 0,'minY': 0,'maxX': 16383,'maxY': 16383,'planes': set(range(4))}
        if axis == 'z':
            part['planes'] = set(range(max(0,low),min(3,high)+1))
        else:
            part['min'+axis.upper()] = low
            part['max'+axis.upper()] = high
        return [part]


def _function_expression(text, name):
    match = re.search(r'\bfun\s+'+re.escape(name)+r'\s*\(([^)]*)\)\s*:\s*Boolean\s*=\s*', _code_only(text))
    if not match:
        raise ValueError('Coordinate predicate '+name+' is not an expression-bodied Boolean function.')
    parameter=re.fullmatch(r'\s*(\w+)\s*:\s*Position\s*',match[1])
    if not parameter:
        raise ValueError('Coordinate predicate must accept one Position argument.')
    at = match.end()
    depth = 0
    for end in range(at, len(text)):
        c = text[end]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
        elif c == '\n' and depth == 0:
            previous = text[at:end].rstrip()
            following = text[end+1:].lstrip()
            if not previous.endswith(('&&','||')) and not following.startswith(('&&','||')):
                return re.sub(r'(?<![\w.])'+re.escape(parameter[1])+r'\b', 'position',text[at:end].strip())
    raise ValueError('Cannot locate the end of coordinate predicate '+name+'.')


class _Catalog:
    """Source evidence and bounded supported native geometry. Author: lare96."""
    def __init__(self, root, report):
        self.root, self.report = root, report
        self.entries, self.diagnostics, self.reads = [], list(report['diagnostics']), {}
        self.zone_ids = set()
        self.zone_args = {}
        self.zone_anchors = {}
        self.settings = None
        self.wiring = {'zone': [], 'subzone': [], 'controller': []}

    def read(self, path):
        path = Path(path)
        if path in self.reads:
            return self.reads[path][1]
        raw = path.read_bytes()
        if len(raw) > 4_000_000:
            raise ValueError('Source file exceeds the supported inspection limit.')
        if raw != path.read_bytes():
            raise ValueError('Source changed while being read; refresh the project.')
        text = _mask_comments(raw.decode('utf-8-sig'))
        self.reads[path] = (hashlib.sha256(raw).hexdigest(), text)
        return text

    def add(self, ident, name, kind, parts, source, activation, **fields):
        planes = sorted({p['plane'] for p in parts if p['plane'] >= 0})
        entry = {'id': ident,'name': name,'kind': kind,'parentId': None,'parts': parts,
                 'bounds': _union_bounds(parts),'planes': None if any(p['plane'] == -1 for p in parts) else planes,
                 'source': source,'activation': activation,'diagnostics': [],**fields}
        self.entries.append(entry)
        return entry

    def fail(self, code, message, source=None, entry=None):
        diagnostic = _diagnostic(code,message,source=source)
        self.diagnostics.append(diagnostic)
        if entry is not None:
            entry['diagnostics'].append(diagnostic)

    def activation(self, status, detail, source, runtime):
        return {'status':status,'label': {'registered':'Registered','unregistered':'Definition only',
                'conditional':'Runtime conditional','unresolved':'Unresolved'}[status],
                'detail':detail,'runtime':runtime,'evidence':[source]}

    def load_settings(self):
        path = self.report['resources'].get('config')
        if path:
            try:
                self.settings = json.loads(self.read(path))
                if not isinstance(self.settings,dict):
                    self.settings=None
                    raise ValueError('Configuration root must be a JSON object.')
            except (ValueError,OSError) as error:
                self.fail('CONFIG_PARSE','Cannot interpret the selected configuration: '+str(error),{'path':_relative(path,self.root),'line':1})

    def runtime_wiring(self):
        probes = [
            ('src/main/kotlin/engine/engine/player/login.kts','Zone.updateZone','zone'),
            ('src/main/java/io/luna/game/model/Entity.java','Zone.Companion.updateZone','zone'),
            ('src/main/kotlin/engine/engine/player/login.kts','SubZone.updateSubZone','subzone'),
            ('src/main/java/io/luna/game/model/Entity.java','SubZone.Companion.updateSubZone','subzone'),
            ('src/main/java/io/luna/game/model/mob/controller/ControllerManager.java','getGLOBAL_LOCATABLE_CONTROLLERS','controller'),
            ('src/main/java/io/luna/game/model/mob/Player.java','controllers.checkPosition()','controller'),
            ('src/main/java/io/luna/game/model/Entity.java','player.getControllers().checkPosition()','controller'),
        ]
        for relative, token, kind in probes:
            path=self.root/relative
            if not path.is_file():
                continue
            text=self.read(path)
            at=_code_only(text).find(token)
            if at>=0:
                self.wiring[kind].append(_source(path,text,at,self.root))

    def apply_wiring(self,entry):
        evidence=self.wiring[entry['kind']]
        entry['activation']['evidence'].extend(evidence)
        entry['activation']['executionTraced']=bool(evidence)
        if not evidence and entry['activation']['status']=='registered':
            entry['activation']['status']='conditional'
            entry['activation']['label']='Registered; execution unresolved'
            entry['activation']['detail']+=' The normal login/movement execution chain was not found at the selected project conventions.'

    def position(self, expr, stack=()):
        if expr in stack or len(stack)>8:
            raise ValueError('Cyclic or overly deep position reference.')
        zone = re.fullmatch(r'([A-Z][A-Z0-9_]*)\.anchor', expr)
        if zone and zone[1] in self.zone_args:
            if zone[1] not in self.zone_anchors:
                self.zone_anchors[zone[1]] = self.position(self.zone_args[zone[1]].get('anchor',''),stack+(expr,))
            return dict(self.zone_anchors[zone[1]])
        native = re.fullmatch(r'(\w+)\.([A-Z][A-Z0-9_]*)\.(\w+)',expr)
        if native:
            targets = list((self.root/'src/main/kotlin').rglob(native[1]+'.kt'))
            if len(targets)!=1:
                raise ValueError('Cannot uniquely locate position reference '+expr+'.')
            text = self.read(targets[0])
            for name,args,positional,_ in _enum_entries(text,native[1]):
                if name == native[2]:
                    value = args.get(native[3])
                    # SpadeDigSpot's first constructor property is the native landing Position.
                    if value is None and native[1]=='SpadeDigSpot' and native[3]=='landing' and positional:
                        value = positional[0]
                    if value:
                        return self.position(value,stack+(expr,))
            raise ValueError('Cannot resolve position reference '+expr+'.')
        return _position(expr,self.settings)

    def zones(self):
        path = self.report['resources'].get('zoneSource')
        if not path:
            return
        text = self.read(path)
        constants = _enum_entries(text,'Zone')
        self.zone_ids = {c[0] for c in constants}
        self.zone_args = {c[0]:c[1] for c in constants}
        registration = _all_entry_loop(text,('zone.regions.forEach','putIfAbsent'))
        evidence = _source(path,text,text.find('val REGIONS'),self.root)
        owners = {}
        conflicts = []
        for name,args,_,at in constants:
            source = _source(path,text,at,self.root)
            activation = self.activation('registered' if registration else 'unresolved',
                'Zone.REGIONS indexes every enum entry; duplicate region ownership prevents initialization.' if registration else
                'The known all-entry Zone.REGIONS registration pattern was not established.',evidence,
                'Per-bot occupancy and activity eligibility require live bot position/state; safe is a destination filter, not an enable flag.')
            safe=args.get('safe','true')
            entry = self.add('bot-zone:'+name,name.replace('_',' ').title(),'zone',[],source,activation,
                             identifier=name, geometry='region-union', safe=True if safe=='true' else False if safe=='false' else None)
            if entry['safe'] is None:
                self.fail('UNRESOLVED_SAFE_FLAG',name+': safe destination flag requires runtime or unsupported source evaluation.',source,entry)
            self.apply_wiring(entry)
            try:
                expr = args.get('regions','')
                literal = re.fullmatch(r'setOf\((.*)\)',expr,re.S)
                if literal:
                    regions = _integers(literal[1])
                elif expr.endswith('.touchedRegions'):
                    regions = _corner_regions(_rectangle(expr[:-len('.touchedRegions')]))
                else:
                    raise ValueError('Region ownership is not a literal set or native rectangle.touchedRegions.')
                if any(r < 0 or r > 65535 for r in regions):
                    raise ValueError('Region ID outside unsigned-short bounds.')
                entry['regions'] = sorted(set(regions))
                entry['parts'] = _region_parts(regions)
                entry['bounds'] = _union_bounds(entry['parts'])
                entry['planes'] = None
                for region in set(regions):
                    if region in owners:
                        conflicts.append((region,owners[region],name))
                    owners[region] = name
            except ValueError as error:
                self.fail('UNRESOLVED_ZONE_BOUNDARY',name+': '+str(error),source,entry)
            try:
                entry['inside'] = self.position(args.get('anchor',''))
            except ValueError as error:
                self.fail('UNRESOLVED_ZONE_ANCHOR',name+': '+str(error),source,entry)
            if re.fullmatch(r'\d+',args.get('price','')):
                entry['travelPrice'] = int(args['price'])
        if conflicts:
            detail = '; '.join(f'{r}: {a} / {b}' for r,a,b in conflicts)
            self.fail('ZONE_REGISTRATION_CONFLICT','Zone.REGIONS would fail duplicate ownership checks: '+detail,evidence)
            for entry in self.entries:
                if entry['kind'] == 'zone':
                    entry['activation'] = self.activation('unresolved','Registration would fail: '+detail,evidence,
                                                        'Server initialization must succeed before occupancy can be inferred.')

    def subzones(self):
        path = self.report['resources'].get('subzoneSource')
        if not path:
            return
        text = self.read(path)
        registration = _all_entry_loop(text,('zone.area.touchedRegions','map.put(region, zone)'))
        evidence = _source(path,text,max(0,text.find('val LOCAL')),self.root)
        for name,args,_,at in _enum_entries(text,'SubZone'):
            source = _source(path,text,at,self.root)
            entry = self.add('subzone:'+name,name.replace('_',' ').title(),'subzone',[],source,
                self.activation('registered' if registration else 'unresolved',
                    'SubZone.LOCAL indexes every enum entry using area.touchedRegions; exact containment is checked after the region prefilter.'
                    if registration else 'The known all-entry SubZone.LOCAL registration pattern was not established.',evidence,
                    'Membership depends on each bot position; choosing this activity can also depend on script, skills, inventory and entry rules.'),identifier=name)
            self.apply_wiring(entry)
            try:
                area = _rectangle(args.get('area',''))
                entry['definedBounds'] = {k:v for k,v in area.items() if k!='type'}
                touched = _corner_regions(area)
                parts = []
                for region in _region_parts(touched):
                    parts.append(dict(area,minX=max(area['minX'],region['minX']),minY=max(area['minY'],region['minY']),
                                      maxX=min(area['maxX'],region['maxX']),maxY=min(area['maxY'],region['maxY'])))
                entry['parts'], entry['bounds'], entry['planes'] = parts,_union_bounds(parts),None if area['plane']==-1 else [area['plane']]
                entry['touchedRegions'] = touched
                full = (area['maxX']-area['minX']+1)*(area['maxY']-area['minY']+1)
                effective = sum((p['maxX']-p['minX']+1)*(p['maxY']-p['minY']+1) for p in parts)
                entry['registrationCoverage'] = {'definedTiles':full,'effectiveTiles':effective,'prefilterGapTiles':full-effective}
                if effective != full:
                    self.fail('SUBZONE_PREFILTER_GAPS',f'{name}: native touchedRegions indexes only corner regions; {full-effective} interior tiles are not tested by SubZone.LOCAL.',source,entry)
            except ValueError as error:
                self.fail('UNRESOLVED_SUBZONE_BOUNDARY',name+': '+str(error),source,entry)
            try:
                entry['inside'] = self.position(args.get('inside',''))
            except ValueError as error:
                self.fail('UNRESOLVED_SUBZONE_ANCHOR',name+': '+str(error),source,entry)
            expr = args.get('parent','')
            direct = re.fullmatch(r'\{\s*([A-Z][A-Z0-9_]*)\s*\}',expr)
            options = sorted(set(re.findall(r'\b[A-Z][A-Z0-9_]*\b',expr)) & self.zone_ids)
            entry['parentOptions'] = ['bot-zone:'+p for p in options]
            if direct and direct[1] in self.zone_ids:
                entry['parentId'] = 'bot-zone:'+direct[1]
                entry['parentRelationship'] = 'Static travel parent'
            else:
                entry['parentExpression'] = ' '.join(expr.split())[:1000]
                entry['parentRelationship'] = 'Runtime-dependent travel parent'
                self.fail('RUNTIME_PARENT',name+': travel parent requires bot/runtime state; candidate parents are shown without choosing one.',source,entry)
            entry['bankExpression'] = ' '.join(args.get('bankZone','parent').split())[:1000]
        blocker = re.search(r'\bfun\s+entryBlocker\s*\([^)]*\)[^=]*=\s*when\s*\(\s*this\s*\)\s*\{',text)
        if blocker:
            opening=text.find('{',blocker.start())
            body=text[opening+1:_closing(text,opening)]
            cases=list(re.finditer(r'(?m)^\s*((?:[A-Z][A-Z0-9_]*\s*,\s*)*[A-Z][A-Z0-9_]*)\s*->',body))
            for i,case in enumerate(cases):
                code=body[case.end():cases[i+1].start() if i+1<len(cases) else len(body)]
                code=re.split(r'\belse\s*->',code,maxsplit=1)[0]
                names=set(re.findall(r'\b[A-Z][A-Z0-9_]*\b',case[1]))
                for entry in self.entries:
                    if entry['kind']=='subzone' and entry['identifier'] in names:
                        entry['eligibility']={'status':'conditional','label':'Bot-specific entry rule',
                            'expression':' '.join(code.split())[:1500],
                            'source':_source(path,text,opening+1+case.start(),self.root)}

    def controllers(self):
        registry_path = self.report['resources'].get('controllers')
        registry, registry_ok = set(), False
        registry_source = {'path':_relative(registry_path,self.root),'line':1} if registry_path else None
        if registry_path:
            text = self.read(registry_path)
            match = re.search(r'\bGLOBAL_LOCATABLE_CONTROLLERS\s*=\s*listOf\(',_code_only(text))
            if match:
                opening = text.find('(',match.start())
                identifiers = [raw.strip() for _,raw in _split(text[opening+1:_closing(text,opening)]) if raw.strip()]
                registry_ok = all(re.fullmatch(r'[A-Za-z_]\w*',name) for name in identifiers)
                registry = set(identifiers) if registry_ok else set()
                tail=text[_closing(text,opening)+1:].split('\n',1)[0].strip()
                if tail and not re.fullmatch(r'[};]+',tail):
                    registry_ok=False
                    self.fail('COMPUTED_CONTROLLER_REGISTRY','Global listener registry contains additional computation; registration cannot be determined from its literal list.',registry_source)
                registry_source = _source(registry_path,text,match.start(),self.root)
        registered_paths=set()
        if registry_path and registry_ok:
            text=self.read(registry_path)
            imports={m[2] or m[1].rsplit('.',1)[-1]:m[1] for m in
                     re.finditer(r'(?m)^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$',text)}
            actual_names=set()
            for name in registry:
                qualified=imports.get(name)
                actual_names.add(qualified.rsplit('.',1)[-1] if qualified else name)
                if qualified:
                    pieces=qualified.split('.')
                    target=self.root/'src/main/kotlin'/pieces[0]/Path(*pieces).with_suffix('.kt')
                else:
                    target=Path(registry_path).with_name(name+'.kt')
                if target.is_file():
                    registered_paths.add(target)
            registry=actual_names
        definitions = []
        source_root = self.root/'src/main/kotlin'
        candidates = registered_paths | {p for p in source_root.rglob('*.kt') if 'Listener' in p.name or 'Controller' in p.name}
        for path in sorted(candidates):
            # The class marker is inspected without executing source or importing modules.
            raw = path.read_bytes()
            if b'PlayerAreaListener' not in raw:
                continue
            try:
                text = self.read(path)
            except (OSError,ValueError) as error:
                self.fail('CONTROLLER_SOURCE_PARSE','Cannot inspect listener source: '+str(error),
                          {'path':_relative(path,self.root),'line':1})
                continue
            for match in re.finditer(r'\b(?:object|class)\s+(\w+)[^{;]*:\s*PlayerAreaListener\s*\(',_code_only(text)):
                opening = text.find('{',match.end())
                if opening < 0:
                    continue
                definitions.append((match[1],path,text,text[opening+1:_closing(text,opening)],match.start(1)))
        found = set()
        for name,path,text,body,at in definitions:
            found.add(name)
            source = _source(path,text,at,self.root)
            status = ('registered' if name in registry else 'unregistered') if registry_ok else 'unresolved'
            detail = ('Present in Controllers.GLOBAL_LOCATABLE_CONTROLLERS; ControllerManager.checkPosition tests it for each player.'
                      if status=='registered' else 'Definition exists but is absent from the static global area-listener registry.'
                      if status=='unregistered' else 'Global listener registration could not be resolved from directory source.')
            entry = self.add('controller:'+name,re.sub(r'(?<!^)(?=[A-Z])',' ',name),'controller',[],source,
                self.activation(status,detail,registry_source or source,'Per-player entry/process state requires live positions; callbacks may apply additional runtime rules.'),identifier=name)
            self.apply_wiring(entry)
            try:
                parts = self.listener_parts(path,body)
                if not parts:
                    raise ValueError('No supported static listener boundary was resolved.')
                entry['parts'],entry['bounds'] = parts,_union_bounds(parts)
                entry['planes'] = None if any(p['plane']==-1 for p in parts) else sorted({p['plane'] for p in parts})
            except (ValueError,OSError) as error:
                self.fail('UNRESOLVED_CONTROLLER_BOUNDARY',name+': '+str(error),source,entry)
        for name in sorted(registry-found):
            entry = self.add('controller:'+name,name,'controller',[],registry_source,
                self.activation('unresolved','Registry references a listener whose source declaration was not found.',registry_source,
                                'Compiled dependencies or additional source roots may be required.'),identifier=name)
            self.fail('MISSING_CONTROLLER_SOURCE','Cannot locate registered listener '+name,registry_source,entry)

    def listener_parts(self,path,body):
        match = re.search(r'\bcomputeLocatables\s*\([^)]*\)\s*:\s*[^=]+?=',body)
        if not match:
            raise ValueError('computeLocatables is not a supported expression-bodied function.')
        expression = body[match.end():]
        if expression.lstrip().startswith('ImmutableSet.of('):
            opening = expression.find('(')
            expression = expression[opening+1:_closing(expression,opening)]
            if 'object : Locatable' in expression:
                predicate = _function_expression(expression,'contains')
                expanded = self.expand_predicates(path,predicate)
                return _Predicate(expanded).parse()
            parts = []
            for _,operand in _split(expression):
                operand=operand.strip()
                if not operand:
                    continue
                area=re.fullmatch(r'((?:SimpleBoxArea|Area)\.(?:of|ofRadius)\([^()]*\))(\s*\.onPlanes\([^()]*\))?',operand)
                region=re.fullmatch(r'Region\(([^()]*)\)',operand)
                if area:
                    parts += _areas(operand)
                elif region:
                    values = _integers(region[1])
                    if len(values)==1:
                        rid=values[0]
                    elif len(values)==2 and all(0<=v<=255 for v in values):
                        rid=(values[0]<<8)|values[1]
                    else:
                        raise ValueError('Unsupported Region constructor.')
                    if not 0<=rid<=65535:
                        raise ValueError('Region ID outside the native range.')
                    parts += _region_parts([rid])
                else:
                    raise ValueError('Unresolved listener member: '+operand[:100])
            return parts
        if 'DarkArea.entries.flatMap' in expression:
            dark_path=path.with_name('DarkArea.kt')
            dark_text=self.read(dark_path)
            parts=[]
            for name,args,positional,_ in _enum_entries(dark_text,'DarkArea'):
                for value in positional:
                    parts += [dict(p,label=name.replace('_',' ').title()) for p in _areas(value)]
            return parts
        raise ValueError('Listener boundary is computed by an unsupported source expression.')

    def expand_predicates(self,path,expr,depth=0):
        if depth>8:
            raise ValueError('Coordinate predicate reference depth exceeded.')
        pattern=re.compile(r'\b([A-Z]\w*)\.(\w+)\((?:position|p|pos)\)')
        def replace(match):
            target=path.with_name(match[1]+'.kt')
            target_text=self.read(target)
            expression=_function_expression(target_text,match[2])
            return '('+self.expand_predicates(target,expression,depth+1)+')'
        return pattern.sub(replace,expr)


def catalog(root, overrides=None):
    """Return source-backed bot and global player-area definitions. Author: lare96."""
    report=discover(root,overrides)
    root=Path(report['root'])
    parser=_Catalog(root,report)
    if not (root/'src/main/java/io/luna/Luna.java').is_file():
        return {'entries':[],'diagnostics':report['diagnostics'],'sourceHash':None,'discovery':report}
    parser.load_settings()
    parser.runtime_wiring()
    for name,operation in [('zones',parser.zones),('subzones',parser.subzones),('controllers',parser.controllers)]:
        try:
            operation()
        except (ValueError,OSError,KeyError) as error:
            parser.fail('CATALOG_'+name.upper(),'Cannot finish '+name+' inspection: '+str(error))
    consistent=True
    for path,(checksum,_) in parser.reads.items():
        try:
            if hashlib.sha256(path.read_bytes()).hexdigest()!=checksum:
                raise ValueError('Source changed during the scan.')
        except (ValueError,OSError):
            consistent=False
            parser.fail('SOURCE_CHANGED_DURING_SCAN','Source changed during the scan; refresh before relying on registration.',{'path':_relative(path,root),'line':1})
    if not consistent:
        for entry in parser.entries:
            entry['activation']['status']='unresolved'
            entry['activation']['label']='Source changed; refresh required'
    digest=hashlib.sha256()
    sources=[]
    for path,(checksum,_) in sorted(parser.reads.items(),key=lambda item:str(item[0])):
        relative=_relative(path,root)
        digest.update(relative.encode());digest.update(checksum.encode())
        sources.append({'path':relative,'sha256':checksum})
    settings={}
    if isinstance(parser.settings,dict):
        bots=parser.settings.get('bots',{})
        count=bots.get('base_online_count') if isinstance(bots,dict) else None
        if type(count) is int:
            settings['automaticBotLogin']={'enabled':count>0,'baseOnlineCount':count,
                'detail':'This controls automatic scheduled bot login; it does not disable zone definitions or prove live occupancy.'}
    return {'entries':parser.entries,'diagnostics':parser.diagnostics,'sourceHash':digest.hexdigest(),'consistent':consistent,
            'sources':sources,'discovery':report,
            'settingsSummary':settings,
            'coverage':'Zone/SubZone enums at selected paths; registered import targets and conventional Listener/Controller Kotlin files. Supported static rectangles, region unions and coordinate predicates are interpreted; arbitrary Kotlin and runtime state are not executed.',
            'counts':{kind:sum(e['kind']==kind for e in parser.entries) for kind in ('zone','subzone','controller')},
            'runtimeStatus':'Registration is inferred from inspected source, not a running JVM. Occupancy, activity eligibility, dynamic parents and live callbacks require runtime state.'}


def contains(entry,x,y,plane):
    """Exact containment in the resolved effective rectangle union. Author: lare96."""
    return any(p['minX']<=x<=p['maxX'] and p['minY']<=y<=p['maxY'] and p['plane'] in (-1,plane)
               for p in entry.get('parts',[]))
