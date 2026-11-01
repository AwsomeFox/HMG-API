#!/usr/bin/env python3
"""Names the frames in a MICOM watch recording (hmgapid micom-log, or
/data/hmgapi/logs/hmgapi/micom.log) from the car's catalogue (tools/micom-catalogue.py).

    tools/micom-decode.py <micom.log> <catalogue.json>              every frame, named
    tools/micom-decode.py <micom.log> <catalogue.json> --changes    per message: which bytes changed, when, to what
    tools/micom-decode.py <micom.log> <catalogue.json> --only HEV   one category (name or hex id)

--changes is the mapping step: record while doing one thing at a time (seat heat
on, conditioning on, a door opened) and see which message and byte follow it; the
community CAN logs (Ioniq5_CAN, egmpdbc) give the bus side of the same signal.
"""
import json
import sys


def load(cat_path):
    cats = json.load(open(cat_path))
    by_cid = {}
    for name, c in cats.items():
        funcs = {code: fn for fn, code in c['functions'].items()}
        by_cid.setdefault(c['cid'], []).append((name, funcs))
    return by_cid


def name(by_cid, cid, func):
    for cat, funcs in by_cid.get(cid, []):
        if func in funcs:
            return cat, funcs[func]
    cats = by_cid.get(cid)
    return (cats[0][0] if cats else '0x%02x' % cid), '0x%04x' % func


def hx(d, i):
    return '--' if i >= len(d) else '__' if d[i] is None else '%02x' % d[i]


def frames(path):
    for line in open(path, encoding='utf-8', errors='replace'):
        w = line.split()
        if len(w) < 3 or w[0].startswith('#'):
            continue
        try:
            h = w[3] if len(w) > 3 else ''
            # "__": a byte the watch never writes (part of a text run: VIN, serials, names); kept as None
            yield int(w[0]), int(w[1], 16), int(w[2], 16), [None if h[i:i + 2] == '__' else int(h[i:i + 2], 16) for i in range(0, len(h), 2)]
        except ValueError:
            continue


def main():
    a = sys.argv[1:]
    if len(a) < 2 or a[0] in ('-h', '--help'):
        print(__doc__.strip())
        return 0 if a else 2
    by_cid = load(a[1])
    only = a[a.index('--only') + 1] if '--only' in a else None
    rows = []
    for ms, cid, func, data in frames(a[0]):
        cat, fn = name(by_cid, cid, func)
        if only and only not in (cat, '%02x' % cid, '0x%02x' % cid):
            continue
        rows.append((ms, cat, fn, data))
    if not rows:
        print('no frames')
        return 1
    t0 = rows[0][0]
    if '--changes' not in a:
        for ms, cat, fn, data in rows:
            print('%9.3f  %-14s %-40s %s' % ((ms - t0) / 1000.0, cat, fn, ' '.join('__' if x is None else '%02x' % x for x in data)))
        return 0
    last, seen = {}, {}
    for ms, cat, fn, data in rows:
        key = (cat, fn)
        seen[key] = seen.get(key, 0) + 1
        prev = last.get(key)
        last[key] = data
        if prev is None:
            continue
        diffs = ['[%d] %s->%s' % (i, hx(prev, i), hx(data, i))
                 for i in range(max(len(prev), len(data)))
                 if (prev[i] if i < len(prev) else None) != (data[i] if i < len(data) else None)]
        if diffs:
            print('%9.3f  %-14s %-40s %s' % ((ms - t0) / 1000.0, cat, fn, '  '.join(diffs)))
    print()
    print('messages heard (count):')
    for (cat, fn), n in sorted(seen.items(), key=lambda x: -x[1]):
        print('  %6d  %s %s' % (n, cat, fn))
    return 0


if __name__ == '__main__':
    sys.exit(main())
