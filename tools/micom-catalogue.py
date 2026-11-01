#!/usr/bin/env python3
"""The MICOM protocol of one car, from its own framework: every category (CID), the
functions in it (name -> code; codes with 0x8000 come from the MICOM, the rest go to
it) and the named constants each category defines (signal indices, enum values).

    tools/micom-catalogue.py <deodexed automotive.jar dir> [--json out.json] [--md out.md]

The deodexed dir is baksmali's output for /system/framework/automotive.odex (the
classes under com/hkmc/micom/protocol). Output is derived from the car's software:
keep it with the private stock trees (wiki/private), not in the repo. HMG-API's
MICOM watch decodes on the car from the same classes, so it ships no table.
"""
import json
import os
import re
import sys

FIELD = re.compile(r'^\.field public static final (\w+):([IBS]) = (-?0x[0-9a-f]+)t?$')


def num(v, t):
    n = int(v, 16)
    if t == 'B':
        n &= 0xff
    return n


def classes(root):
    base = os.path.join(root, 'com', 'hkmc', 'micom', 'protocol')
    out = {}
    for dp, _, fs in os.walk(base):
        for f in fs:
            if f.endswith('.smali'):
                rel = os.path.relpath(os.path.join(dp, f), base)[:-6].replace(os.sep, '.')
                out[rel] = open(os.path.join(dp, f), encoding='utf-8', errors='replace').read()
    return out


def constants(text):
    return [(m.group(1), m.group(2), num(m.group(3), m.group(2))) for m in map(FIELD.match, text.splitlines()) if m]


def build(root):
    cls = classes(root)
    cats = {}
    for name, text in sorted(cls.items()):
        if '$' in name:
            continue
        cid = [c for c in constants(text) if c[0] == 'CID']
        if not cid:
            continue
        cat = {'cid': cid[0][2], 'functions': {}, 'groups': {}}
        for inner, itext in sorted(cls.items()):
            if not inner.startswith(name + '$'):
                continue
            group = inner[len(name) + 1:]
            consts = {c[0]: c[2] for c in constants(itext)}
            if not consts:
                continue
            if group == 'Func':
                cat['functions'] = consts
            else:
                cat['groups'][group] = consts
        top = {c[0]: c[2] for c in constants(text) if c[0] != 'CID'}
        if top:
            cat['groups']['(class)'] = top
        cats[name] = cat
    return cats


def markdown(cats):
    out = ['# MICOM protocol (from the car\'s framework)', '',
           'Category: CID. Functions: code (0x8000 set: from the MICOM; clear: to it).', '']
    for name, c in sorted(cats.items(), key=lambda x: x[1]['cid']):
        fr = sum(1 for v in c['functions'].values() if v & 0x8000)
        out.append('## %s (CID 0x%02x): %d functions (%d from the MICOM), %d groups' % (
            name, c['cid'], len(c['functions']), fr, len(c['groups'])))
        out.append('')
        for fn, code in sorted(c['functions'].items(), key=lambda x: x[1]):
            out.append('- `0x%04x` %s' % (code, fn))
        for g, consts in sorted(c['groups'].items()):
            out.append('- group `%s`: %s' % (g, ', '.join('%s=%d' % kv for kv in sorted(consts.items(), key=lambda x: x[1])[:40])
                                             + (' ...' if len(consts) > 40 else '')))
        out.append('')
    return '\n'.join(out) + '\n'


def main():
    a = sys.argv[1:]
    if not a or a[0] in ('-h', '--help'):
        print(__doc__.strip())
        return 0 if a else 2
    cats = build(a[0])
    if not cats:
        sys.exit('no protocol classes with a CID under ' + a[0])
    if '--json' in a:
        json.dump(cats, open(a[a.index('--json') + 1], 'w'), indent=1, sort_keys=True)
    if '--md' in a:
        open(a[a.index('--md') + 1], 'w').write(markdown(cats))
    print('%d categories, %d functions, %d named constants' % (
        len(cats), sum(len(c['functions']) for c in cats.values()),
        sum(len(g) for c in cats.values() for g in c['groups'].values())))
    for name, c in sorted(cats.items(), key=lambda x: x[1]['cid']):
        print('  0x%02x %-14s %4d functions' % (c['cid'], name, len(c['functions'])))
    return 0


if __name__ == '__main__':
    sys.exit(main())
