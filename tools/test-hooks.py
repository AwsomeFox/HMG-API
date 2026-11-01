#!/usr/bin/env python3
"""Test every hook point (and every mod's hooks) against every car we have stock files for.

usage: test-hooks.py [--update] [--only LABEL[,LABEL]] [--points] [-j N]

Cars come from tools/hook-trees.local (private, not committed): "<label> <stock tree>"
lines, e.g. "ev6-usa-260507 ~/hmg-private/stock/ev6-full". A tree may be partial
(framework plus a few apps): points of apps it doesn't hold count as "no files", not
as failures. Each tree is mapped with map-car.py --matrix (nothing is written).

Prints, per car: points found, not found (the app is there but the shape doesn't
match once), and without files; --points lists every point that isn't found. Then
which mods' hooks would load on which car.

The last result is kept in maps/coverage.json (point ids and car labels
only, no app code). Exit 1 when a point found on a car before isn't any more, or a
new point isn't found on a car whose files are there: a changed or added hook point
has to work on the cars it worked on. --update saves this run as the new baseline.
"""
import argparse, concurrent.futures, glob, json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
BASELINE = os.path.join(REPO, 'maps', 'coverage.json')


def trees():
    out = []
    p = os.path.join(HERE, 'hook-trees.local')
    for line in (open(p).read().split('\n') if os.path.isfile(p) else []):
        if line.strip() and not line.startswith('#'):
            label, tree = line.split()[:2]
            out.append((label, os.path.expanduser(tree)))
    return out


def matrix(tree):
    """{point: 'found' | 'no files' | 'not found: why'}"""
    r = subprocess.run([sys.executable, os.path.join(HERE, 'map-car.py'), tree, '--matrix'], capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(r.stderr.strip()[-300:])
    res = {}
    for line in r.stdout.split('\n'):
        if line.startswith('found    '):
            res[line[9:].strip()] = 'found'
        elif line.startswith('missing  '):
            pid, why = line[9:].split('  (', 1)
            why = why[:-1]
            res[pid.strip()] = 'no files' if why.startswith('no stock ') else 'not found: ' + why
    return res


def mods():
    """{mod id: [hook points it declares]}"""
    out = {}
    for mj in sorted(glob.glob(os.path.join(REPO, 'mods', '**', 'mod.json'), recursive=True)):
        m = json.load(open(mj))
        pts = sorted(((m.get('hooks') or {}).get('points') or {}).keys())
        if pts:
            out[m['id']] = pts
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--update', action='store_true')
    ap.add_argument('--only')
    ap.add_argument('--points', action='store_true')
    ap.add_argument('-j', type=int, default=2, help='cars mapped at once (each needs ~400 MB in the temp dir)')
    a = ap.parse_args()
    cars = trees()
    if a.only:
        cars = [c for c in cars if c[0] in a.only.split(',')]
    if not cars:
        sys.exit('no cars: add "<label> <stock tree>" lines to tools/hook-trees.local')
    catalogue = [p['id'] for f in sorted(glob.glob(os.path.join(REPO, 'core', 'hookpoints', '*.json')))
                 for p in json.load(open(f))['points']]
    results = {}
    with concurrent.futures.ThreadPoolExecutor(a.j) as ex:
        futs = {ex.submit(matrix, tree): label for label, tree in cars}
        for f in concurrent.futures.as_completed(futs):
            label = futs[f]
            try:
                results[label] = f.result()
            except Exception as e:
                print('FAIL  %s: map-car.py: %s' % (label, e))
                results[label] = None
    problems = []
    print('%-22s %6s %10s %9s' % ('car', 'found', 'not found', 'no files'))
    for label, _ in cars:
        r = results[label]
        if r is None:
            problems.append('%s: not mapped at all' % label)
            continue
        vals = [r.get(p, 'not found: not reported') for p in catalogue]
        print('%-22s %6d %10d %9d' % (label, vals.count('found'), sum(v.startswith('not found') for v in vals),
                                     vals.count('no files')))
        if a.points:
            for p in catalogue:
                v = r.get(p, 'not found: not reported')
                if v != 'found':
                    print('    %-62s %s' % (p, v))
    print()
    ms = mods()
    print('mod hooks (y: every point it declares is found; -: some app has no files in the tree; n: a point isn\'t found)')
    print('%-24s %s' % ('', ' '.join('%-8s' % l[:8] for l, _ in cars)))
    for mid, pts in ms.items():
        row = []
        for label, _ in cars:
            r = results[label] or {}
            vals = [r.get(p, 'not found') for p in pts]
            row.append('n' if any(v.startswith('not found') for v in vals) else '-' if 'no files' in vals else 'y')
        print('%-24s %s' % (mid[:24], ' '.join('%-8s' % x for x in row)))
    print()
    base = json.load(open(BASELINE)) if os.path.isfile(BASELINE) else {}
    for label, _ in cars:
        r, b = results[label], base.get(label)
        if r is None or b is None:
            if r is not None and b is None:
                print('new   %s (no baseline yet: --update to keep it)' % label)
            continue
        for p in catalogue:
            now, before = r.get(p, 'not found'), b.get(p)
            if before == 'found' and now != 'found':
                problems.append('%s: %s was found, now %s' % (label, p, now))
            elif before is None and now.startswith('not found'):
                problems.append('%s: new point %s isn\'t found (%s)' % (label, p, now[11:]))
            elif before is not None and before != 'found' and now == 'found':
                print('now   %s: %s found' % (label, p))
    if a.update:
        for label, _ in cars:
            if results[label] is not None:
                base[label] = {p: results[label].get(p, 'not found') for p in catalogue}
        json.dump(dict(sorted(base.items())), open(BASELINE, 'w'), indent=1, sort_keys=True)
        open(BASELINE, 'a').write('\n')
        print('saved %s' % os.path.relpath(BASELINE, REPO))
    for p in problems:
        print('FAIL  ' + p)
    print('%d hook points, %d car(s)%s' % (len(catalogue), len(cars), ': no regressions' if not problems else ''))
    sys.exit(1 if problems and not a.update else 0)


if __name__ == '__main__':
    main()
