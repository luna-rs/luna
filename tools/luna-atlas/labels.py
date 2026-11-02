"""Viewport-aware labels anchored in actual effective zone parts. Author: lare96."""
import math

import geometry


DEFAULT_STYLES = {
    'zone': {'family': 'Segoe UI', 'size': 14, 'bold': True, 'italic': False},
    'subzone': {'family': 'Segoe UI', 'size': 10, 'bold': False, 'italic': False},
    'controller': {'family': 'Segoe UI', 'size': 10, 'bold': False, 'italic': True},
}


def normalise_style(value, kind):
    base = dict(DEFAULT_STYLES.get(kind, DEFAULT_STYLES['subzone']))
    if not isinstance(value, dict):
        return base
    family = value.get('family')
    if isinstance(family, str) and family.strip():
        base['family'] = family.strip()[:80]
    size = value.get('size')
    if isinstance(size, (int, float)) and math.isfinite(size):
        base['size'] = max(7, min(32, round(size)))
    for key in ('bold', 'italic'):
        if type(value.get(key)) is bool:
            base[key] = value[key]
    return base


def anchor_candidates(entry, plane, view):
    """Prefer a visible native anchor, then centres inside visible effective parts."""
    points = []
    polygon_candidates = []
    inside = entry.get('inside') or {}
    if inside.get('plane', plane) == plane and 'x' in inside and 'y' in inside:
        points.append((inside['x'] + .5, inside['y'] + .5))
    boxes = []
    for part in geometry.parts_for_plane(entry, plane):
        if part.get('type', 'rectangle') == 'rectangle':
            x0, y0 = max(part['minX'], view['minX']), max(part['minY'], view['minY'])
            x1, y1 = min(part['maxX'], view['maxX']), min(part['maxY'], view['maxY'])
        else:
            vertices = part.get('vertices', [])
            if not vertices:
                continue
            xs, ys = zip(*vertices)
            x0, y0 = max(min(xs), view['minX']), max(min(ys), view['minY'])
            x1, y1 = min(max(xs) - 1, view['maxX']), min(max(ys) - 1, view['maxY'])
            for index, vertex in enumerate(vertices):
                before, after = vertices[index - 1], vertices[(index + 1) % len(vertices)]
                polygon_candidates.append(((before[0] + vertex[0] + after[0]) / 3,
                                           (before[1] + vertex[1] + after[1]) / 3))
        if x1 < x0 or y1 < y0:
            continue
        boxes.append(((x1 - x0 + 1) * (y1 - y0 + 1), (x0, y0, x1 + 1, y1 + 1)))
    for _, (x0, y0, x1, y1) in sorted(boxes, reverse=True):
        for fx, fy in ((.5, .5), (.25, .5), (.75, .5), (.5, .25), (.5, .75), (.25, .25), (.75, .75)):
            points.append((x0 + (x1 - x0) * fx, y0 + (y1 - y0) * fy))
    points.extend(polygon_candidates)
    result = []
    for x, y in points:
        if not (view['minX'] <= x < view['maxX'] + 1 and view['minY'] <= y < view['maxY'] + 1):
            continue
        if geometry.contains(entry, math.floor(x), math.floor(y), plane) and (x, y) not in result:
            result.append((x, y))
    return result


def overlaps(first, second, gap=5):
    return (first[0] - gap < second[2] and first[2] + gap > second[0]
            and first[1] - gap < second[3] and first[3] + gap > second[1])


def layout(entries, plane, view, project, measure, styles, scale, viewport,
           selected=None, enabled=None, blocked=()):
    """Place prominent parents first while suppressing collisions at low zoom.

    The selected label receives first choice. Labels never invent a geographic
    anchor outside the effective plane-specific geometry.

    @author lare96
    """
    enabled = enabled or {'zone': True, 'subzone': True, 'controller': False}
    occupied = list(blocked)
    output = []
    order = {'zone': 0, 'subzone': 1, 'controller': 2}
    candidates = sorted(entries, key=lambda e: (e['id'] != selected, order.get(e.get('kind'), 3), e.get('name', e['id'])))
    for entry in candidates:
        kind = entry.get('kind', 'zone')
        chosen = entry['id'] == selected
        if not enabled.get(kind, False):
            continue
        if not chosen and scale < (.5 if kind == 'zone' else 2):
            continue
        style = normalise_style(styles.get(kind), kind)
        text = entry.get('name', entry['id'])
        width, height = measure(text, style)
        if width > viewport[0] - 12 or height > viewport[1] - 12:
            continue
        for wx, wy in anchor_candidates(entry, plane, view):
            x, y = project(wx, wy)
            box = (x - width / 2 - 4, y - height / 2 - 3, x + width / 2 + 4, y + height / 2 + 3)
            if box[0] < 3 or box[1] < 3 or box[2] > viewport[0] - 3 or box[3] > viewport[1] - 3:
                continue
            if not any(overlaps(box, old) for old in occupied):
                output.append({'id': entry['id'], 'text': text, 'style': style, 'x': x, 'y': y,
                               'box': box, 'anchor': (wx, wy), 'selected': chosen})
                occupied.append(box)
                break
    return output
