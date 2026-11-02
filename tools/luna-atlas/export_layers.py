"""PNG display layers sharing Atlas's native geometry and label layout.

All overlays are derived display data. This module never changes cache, source,
draft, or project settings.

@author lare96
"""
from functools import lru_cache
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import geometry
import grids as grid_math
import labels as map_labels


def _json(value, name, expected, maximum=100_000):
    if isinstance(value, str):
        if len(value) > maximum:
            raise ValueError(name + ' exceeds the display-settings budget.')
        try:
            value = json.loads(value)
        except (ValueError, RecursionError):
            raise ValueError(name + ' must be valid JSON.') from None
    if not isinstance(value, expected):
        raise ValueError(name + (' must be an object.' if expected is dict else ' must be a list.'))
    try:
        if len(json.dumps(value, allow_nan=False)) > maximum:
            raise ValueError(name + ' exceeds the display-settings budget.')
    except (TypeError, RecursionError):
        raise ValueError(name + ' must contain JSON display settings.') from None
    return value


def _flag(value, name):
    if type(value) is bool:
        return value
    if type(value) is int and value in (0, 1):
        return bool(value)
    if isinstance(value, str) and value.lower() in ('0', '1', 'false', 'true'):
        return value.lower() in ('1', 'true')
    raise ValueError(name + ' must be true or false.')


def _number(value, name, low, high):
    try:
        result = float(value) if not isinstance(value, bool) else float('nan')
    except (ValueError, TypeError, OverflowError):
        result = float('nan')
    if not math.isfinite(result) or not low <= result <= high:
        raise ValueError(f'{name} must be between {low} and {high}.')
    return result


def _options(args):
    layers = _json(args.get('layers', {}), 'layers', dict)
    styles = _json(args.get('labelStyles', {}), 'labelStyles', dict)
    grids = _json(args.get('grids', {}), 'grids', dict)
    ids = _json(args.get('catalogIds', []), 'catalogIds', list)
    if len(ids) > 2000 or any(not isinstance(key, str) or len(key) > 256 for key in ids):
        raise ValueError('catalogIds must be a bounded list of zone identifiers.')
    selected = {}
    for name in ('selectedCatalogId', 'selectedNpcId'):
        value = args.get(name)
        if value is not None and (not isinstance(value, str) or len(value) > 256):
            raise ValueError(name + ' must be a display identifier of at most 256 characters.')
        selected[name] = value or None
    labels = _flag(args.get('labels', True), 'labels')
    defaults = {'zoneBoundaries': True, 'subzoneBoundaries': True, 'controllerBoundaries': True,
                'zoneLabels': labels, 'subzoneLabels': labels, 'npcMarkers': False}
    layers = {key: _flag(layers.get(key, default), key) for key, default in defaults.items()}
    if not labels:
        layers['zoneLabels'] = layers['subzoneLabels'] = False
    if 'npcMarkers' in args:
        layers['npcMarkers'] = _flag(args['npcMarkers'], 'npcMarkers')
    grid_settings = {key: _flag(grids.get(key, False), key)
                     for key in ('regionLines', 'regionLabels', 'chunkLines', 'chunkLabels')}
    for kind in ('region', 'chunk'):
        key = 'custom' + kind.title() + 'Labels'
        grid_settings[key] = grid_math.clean_custom_labels(grids.get(key, {}), kind)
    return dict(layers=layers, grids=grid_settings,
                styles={kind: map_labels.normalise_style(styles.get(kind), kind) for kind in map_labels.DEFAULT_STYLES},
                ids=set(ids), zones=_flag(args.get('zones', True), 'zones'),
                existing=_flag(args.get('existingZones', False), 'existingZones'),
                opacity=_number(args.get('overlayOpacity', .18), 'overlayOpacity', 0, 1),
                dpi=_number(args.get('labelDpi', 96), 'labelDpi', 48, 384), **selected)


@lru_cache(maxsize=128)
def _font(family, size, bold=False, italic=False):
    """Resolve familiar Windows font variants, then a portable styled fallback."""
    known = {
        'segoe ui': ('segoeui.ttf', 'segoeuib.ttf', 'segoeuii.ttf', 'segoeuiz.ttf'),
        'arial': ('arial.ttf', 'arialbd.ttf', 'ariali.ttf', 'arialbi.ttf'),
        'consolas': ('consola.ttf', 'consolab.ttf', 'consolai.ttf', 'consolaz.ttf'),
    }
    variant = int(bold) + int(italic) * 2
    variants = known.get(family.casefold(), known['segoe ui'])
    fallback = ('DejaVuSans.ttf', 'DejaVuSans-Bold.ttf', 'DejaVuSans-Oblique.ttf', 'DejaVuSans-BoldOblique.ttf')
    for path in (str(Path('C:/Windows/Fonts') / variants[variant]), fallback[variant],
                 str(Path('C:/Windows/Fonts') / known['segoe ui'][variant]), 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            pass
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def _style_font(style, dpi):
    return _font(style['family'], max(1, round(style['size'] * dpi / 72)), style['bold'], style['italic'])


def _measure(text, font):
    # Use the full line height, matching Tk's font.metrics('linespace').
    box = font.getbbox(text)
    try:
        ascent, descent = font.getmetrics()
        height = ascent + descent
    except AttributeError:
        height = box[3] - box[1]
    return box[2] - box[0], height


def _text(draw, position, text, font, colour, stroke=1):
    draw.multiline_text(position, text, font=font, fill=colour, anchor='mm', align='center',
                        stroke_width=stroke, stroke_fill='#101a1f', spacing=2)


def _intersects(a, b):
    return (a['maxX'] >= b['minX'] and a['minX'] <= b['maxX']
            and a['maxY'] >= b['minY'] and a['minY'] <= b['maxY'])


def _part_bounds(part):
    if part.get('type', 'rectangle') == 'rectangle':
        return part
    vertices = part.get('vertices', [])
    if not vertices:
        return None
    xs, ys = zip(*vertices)
    return {'minX': min(xs), 'minY': min(ys), 'maxX': max(xs), 'maxY': max(ys)}


def _visible(catalog, bounds, options):
    entries = catalog.get('entries', []) if isinstance(catalog, dict) else catalog
    selected = options['ids']
    result = []
    for entry in entries or ():
        if entry['id'] not in selected and not (options['existing'] and entry.get('kind') == 'subzone'):
            continue
        parts = [part for part in geometry.parts_for_plane(entry, bounds['plane'])
                 if (extent := _part_bounds(part)) and _intersects(extent, bounds)]
        if parts:
            result.append((entry, parts))
    def order(item):
        area = sum((b['maxX']-b['minX']+1)*(b['maxY']-b['minY']+1)
                   for part in item[1] if (b := _part_bounds(part)))
        return item[0]['id'] == options['selectedCatalogId'], -area
    return sorted(result, key=order)


def _boundary_enabled(entry, layers):
    return layers[{'zone': 'zoneBoundaries', 'subzone': 'subzoneBoundaries',
                   'controller': 'controllerBoundaries'}.get(entry.get('kind'), 'zoneBoundaries')]


def _fills(image, visible, bounds, scale, project, options):
    if options['opacity'] == 0:
        return
    for entry, parts in visible:
        if not _boundary_enabled(entry, options['layers']):
            continue
        mask = Image.new('L', image.size, 0)
        paint = ImageDraw.Draw(mask)
        alpha = round(options['opacity'] * 255)
        for part in parts:
            if part.get('type', 'rectangle') == 'rectangle':
                left, top = project(max(bounds['minX'], part['minX']), min(bounds['maxY'], part['maxY']) + 1)
                right, bottom = project(min(bounds['maxX'], part['maxX']) + 1, max(bounds['minY'], part['minY']))
                paint.rectangle((left, top, right-1, bottom-1), fill=alpha)
            else:
                paint.polygon([project(x, y) for x, y in part['vertices']], fill=alpha)
        image.paste(geometry.catalog_colour(entry), (0, 0), mask)


def _line(draw, a, b, colour, width=1, dash=()):
    if not dash:
        draw.line((a, b), fill=colour, width=width)
        return
    distance = math.dist(a, b)
    if not distance:
        return
    unit = ((b[0]-a[0])/distance, (b[1]-a[1])/distance)
    offset = 0
    while offset < distance:
        stop = min(distance, offset + dash[0])
        draw.line(((a[0]+unit[0]*offset, a[1]+unit[1]*offset),
                   (a[0]+unit[0]*stop, a[1]+unit[1]*stop)), fill=colour, width=width)
        offset += sum(dash)


def _loaded_regions(world):
    regions = getattr(world, 'regions', {})
    return {key if isinstance(key, tuple) else (int(key)//256, int(key)%256) for key in regions}


def _grids(draw, bounds, scale, project, options, world):
    settings = options['grids']
    if settings['chunkLines'] and scale >= 2:
        for x0, y0, x1, y1 in grid_math.grid_lines(bounds, 'chunk'):
            _line(draw, project(x0,y0), project(x1,y1), '#536f78', dash=(2,4))
    if settings['regionLines']:
        for x0, y0, x1, y1 in grid_math.grid_lines(bounds, 'region'):
            _line(draw, project(x0,y0), project(x1,y1), '#b8d3da', width=2 if scale >= 2 else 1)
    occupied = []
    loaded = _loaded_regions(world)
    for kind, threshold in (('region', 2), ('chunk', 8)):
        if not settings[kind + 'Labels'] or scale < threshold:
            continue
        font = _font('Consolas', round((9 if kind == 'region' else 8)*options['dpi']/72), kind == 'region')
        for cell in grid_math.grid_cells(bounds, kind, bounds['plane'], limit=2048):
            if (cell['regionX'], cell['regionY']) not in loaded:
                continue
            b = cell['bounds']
            name = settings['custom' + kind.title() + 'Labels'].get(cell['key'], '')
            native = f"R{cell['regionId']}" if kind == 'region' else f"{cell['x']},{cell['y']}"
            maximum = max(5, int(cell['size']*scale/5)-2)
            text = ((name[:maximum-1]+'…' if len(name) > maximum else name)+'\n'+native) if name else native
            x,y = project((b['minX']+b['maxX']+1)/2, (b['minY']+b['maxY']+1)/2)
            box = draw.multiline_textbbox((x,y), text, font=font, anchor='mm', spacing=2)
            if any(map_labels.overlaps(box, old, gap=5) for old in occupied):
                continue
            occupied.append(box)
            _text(draw, (x,y), text, font, '#eef4dc' if kind == 'region' else '#bad0d4')


def _outlines(draw, visible, bounds, project, options):
    for entry, _ in visible:
        if not _boundary_enabled(entry, options['layers']):
            continue
        selected = entry['id'] == options['selectedCatalogId']
        colour = '#fff2c9' if selected else geometry.catalog_colour(entry)
        status = entry.get('activation', {}).get('status')
        dash = (7,4) if status == 'conditional' else ((2,4) if status in ('unresolved','unregistered') else ())
        for x0,y0,x1,y1 in geometry.outline_segments(entry, bounds['plane']):
            extent = {'minX':min(x0,x1),'minY':min(y0,y1),'maxX':max(x0,x1),'maxY':max(y0,y1)}
            if _intersects(extent, bounds):
                a,b = project(x0,y0), project(x1,y1)
                _line(draw, a,b, '#172326', width=5 if selected else 3)
                _line(draw, a,b, colour, width=3 if selected else 1, dash=dash)


def _labels(draw, visible, bounds, scale, project, options, size):
    measure = lambda text, style: _measure(text, _style_font(style, options['dpi']))
    placed = map_labels.layout([entry for entry,_ in visible], bounds['plane'], bounds, project,
        measure, options['styles'], scale, size, selected=options['selectedCatalogId'],
        enabled={'zone':options['layers']['zoneLabels'], 'subzone':options['layers']['subzoneLabels'], 'controller':False})
    entries = {entry['id']:entry for entry,_ in visible}
    for label in placed:
        colour = '#fff2c9' if label['selected'] else geometry.catalog_colour(entries[label['id']])
        _text(draw, (label['x'],label['y']), label['text'], _style_font(label['style'], options['dpi']), colour)


def _npcs(draw, entities, bounds, scale, project, options):
    if entities is None or not (options['layers']['npcMarkers'] or options['selectedNpcId']):
        return
    data = entities.data(plane=bounds['plane'], bounds=bounds) if hasattr(entities, 'data') else entities
    spawns = data.get('spawns', []) if isinstance(data, dict) else data
    # Desktop buckets traverse regions in x/y order, then retain source order.
    visible = [item for item in spawns if item.get('plane') == bounds['plane']
               and bounds['minX'] <= item['x'] <= bounds['maxX'] and bounds['minY'] <= item['y'] <= bounds['maxY']]
    visible.sort(key=lambda item:(item['x']//64,item['y']//64))
    occupied = set()
    spacing = 13 if scale < 2 else 5
    radius = 2 if scale < 4 else 3
    selected = None
    for item in visible:
        if str(item.get('instanceId', item.get('id', ''))) == options['selectedNpcId']:
            selected = item
        if not options['layers']['npcMarkers']:
            continue
        x,y = project(item['x']+.5,item['y']+.5)
        cell = round(x/spacing), round(y/spacing)
        if cell in occupied or len(occupied) >= 1200:
            continue
        occupied.add(cell)
        draw.ellipse((x-radius,y-radius,x+radius,y+radius), fill='#f4de80', outline='#4b3d1a')
    if selected:
        x,y = project(selected['x']+.5,selected['y']+.5)
        draw.ellipse((x-8,y-8,x+8,y+8), outline='#ffffff', width=2)
        draw.text((x+11,y-12), selected.get('name','Selected entity'), anchor='ls', fill='#fff2c9',
                  font=_font('Segoe UI',round(10*options['dpi']/72),True),stroke_width=1,stroke_fill='#101a1f')


def _drafts(draw, drafts, bounds, scale, project, options):
    font = _style_font(options['styles']['subzone'], options['dpi'])
    for zone in drafts or ():
        b = zone['bounds']
        if zone.get('baselineMatches') is False or b.get('plane',-1) not in (-1,bounds['plane']) or not _intersects(b,bounds):
            continue
        x0,y0 = project(b['minX'],b['maxY']+1)
        x1,y1 = project(b['maxX']+1,b['minY'])
        draw.rectangle((x0,y0,x1-1,y1-1),outline='#f0cb67',width=2)
        if options['layers']['subzoneLabels'] and scale >= 2:
            draw.text((max(0,x0)+3,max(0,y0)+3),zone.get('name','Subzone'),font=font,fill='#ffeaa3',stroke_width=1,stroke_fill='#101a1f')
        inside = zone.get('inside')
        if inside and inside.get('plane',bounds['plane']) == bounds['plane']:
            x,y = project(inside['x']+.5,inside['y']+.5)
            draw.ellipse((x-3,y-3,x+3,y+3),fill='#82ea97',outline='#0c2310')


def draw_layers(image, bounds, scale, catalog, drafts, entities, args, world):
    """Apply current Atlas display settings to a north-up inclusive map image.

    catalog is its data dictionary or entry list; drafts is a list; entities is
    EntityCatalog, its data dictionary, or None; world supplies regions. Region
    label names use stable packed IDs and chunk names use global x,y keys.

    @author lare96
    """
    options = _options(args)
    scale = _number(scale, 'scale', 1, 8)
    if scale != int(scale):
        raise ValueError('scale must be an integer.')
    scale = int(scale)
    clipped = grid_math.clipped_bounds(bounds)
    if clipped is None or any(clipped[key] != bounds[key] for key in clipped):
        raise ValueError('Map bounds must be inclusive supported world tiles.')
    plane = bounds.get('plane', 0)
    if type(plane) is not int or not 0 <= plane <= 3:
        raise ValueError('plane must be between 0 and 3.')
    bounds = dict(clipped, plane=plane)
    expected = ((bounds['maxX']-bounds['minX']+1)*scale,(bounds['maxY']-bounds['minY']+1)*scale)
    if max(expected) > 4096 or image.size != expected:
        raise ValueError('Overlay image dimensions must match map bounds and scale, up to 4096 pixels per side.')
    if image.mode != 'RGB':
        image = image.convert('RGB')
    project = lambda x,y: ((x-bounds['minX'])*scale,(bounds['maxY']-y+1)*scale)
    visible = _visible(catalog, bounds, options) if options['zones'] else []
    _fills(image, visible, bounds, scale, project, options)
    draw = ImageDraw.Draw(image)
    _grids(draw, bounds, scale, project, options, world)
    _outlines(draw, visible, bounds, project, options)
    _labels(draw, visible, bounds, scale, project, options, image.size)
    _npcs(draw, entities, bounds, scale, project, options)
    if options['zones']:
        _drafts(draw, drafts, bounds, scale, project, options)
    return image
