#!/usr/bin/env python3
"""The on-car mapper (hmgapi.engine.MapProbe, `engine map-car`) against tools/map-car.py,
on every car in tools/hook-trees.local: both must find the same points with the same
names. MapProbe reads the dex in each .odex instead of baksmali's output; points that
only a smali pattern finds, it takes "by reference" from the committed maps of other
cars (the car's own map is left out, so that path is really tested).

usage: probe-test.py [--only LABEL[,LABEL]]
"""
import argparse, glob, json, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from importlib import import_module
th = import_module('test-hooks')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only')
    a = ap.parse_args()
    cars = th.trees()
    if a.only:
        cars = [c for c in cars if c[0] in a.only.split(',')]
    if not cars:
        print('ok    no tools/hook-trees.local: the on-car mapper isn\'t compared')
        return
    problems = []
    with tempfile.TemporaryDirectory(prefix='hmgapi-probe-') as t:
        cp = os.path.join(t, 'cp')
        os.makedirs(cp)
        subprocess.run(['javac', '--release', '8', '-Xlint:-options', '-nowarn', '-d', cp]
                       + glob.glob(os.path.join(REPO, 'core', 'runtime', 'java', 'hmgapi', 'engine', '*.java')),
                       check=True, stderr=subprocess.DEVNULL)
        for label, tree in cars:
            # map-car.py's answer: a partial map in a scratch maps dir
            ref = os.path.join(t, 'ref-' + label)
            r = subprocess.run([sys.executable, os.path.join(HERE, 'map-car.py'), tree, '--make', 'x', '--model', 'x',
                                '--region', 'x', '--firmware', 'x', '--products', 'X.', '--maps', ref],
                               capture_output=True, text=True)
            if r.returncode:
                problems.append('%s: map-car.py: %s' % (label, r.stderr.strip()[-200:]))
                continue
            d = os.path.join(ref, 'x', 'x', 'x', 'x')
            want, want_missing = {}, json.load(open(os.path.join(d, 'car.json'))).get('missing', {})
            for f in glob.glob(os.path.join(d, '*.json')):
                if not f.endswith('car.json'):
                    want.update(json.load(open(f)))
            # the committed maps of other cars, as references
            maps = os.path.join(t, 'maps-' + label)
            shutil.copytree(os.path.join(REPO, 'maps'), maps, ignore=shutil.ignore_patterns('coverage.json'))
            for cj in glob.glob(os.path.join(maps, '*', '*', '*', '*', 'car.json')):
                if os.path.realpath(tree) == os.path.realpath(own_tree(cj)):
                    shutil.rmtree(os.path.dirname(cj))
            r = subprocess.run(['java', '-cp', cp, 'hmgapi.engine.MapProbe', tree, os.path.join(REPO, 'core', 'hookpoints'), maps],
                               capture_output=True, text=True)
            if r.returncode:
                problems.append('%s: MapProbe: %s' % (label, r.stderr.strip()[-300:]))
                continue
            got = json.loads(r.stdout)
            pts, miss, byref = got['points'], got['missing'], got['by_reference']
            diff = 0
            # smali-only points with no other car's map to borrow names from: missing, as expected
            noref = {pid for pid, why in miss.items() if pid in want and ('no other car' in why or why.startswith('needs '))}
            for pid in sorted(set(want) | set(pts)):
                if pid in noref:
                    continue
                if want.get(pid) != pts.get(pid):
                    diff += 1
                    if diff <= 8:
                        problems.append('%s: %s: map-car %s, MapProbe %s' % (label, pid, want.get(pid) or 'missing (%s)' % want_missing.get(pid),
                                                                           pts.get(pid) or 'missing (%s)' % miss.get(pid)))
            print('%s    %s: %d points the same as map-car.py, %d by reference, %d need another car\'s map, %d missing in both' % (
                'ok  ' if not diff else 'FAIL', label, len(pts) - diff, len(byref), len(noref), len(set(miss) & set(want_missing))))
    for p in problems:
        print('FAIL  ' + p)
    sys.exit(1 if problems else 0)


def own_tree(car_json):
    """the stock tree a committed map was made from (tools/map-trees.local), or ''"""
    folder = os.path.relpath(os.path.dirname(car_json), os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(car_json))))))
    p = os.path.join(HERE, 'map-trees.local')
    for line in (open(p).read().split('\n') if os.path.isfile(p) else []):
        if line.strip() and not line.startswith('#') and line.split()[0] == folder:
            return os.path.expanduser(line.split()[1])
    return ''


if __name__ == '__main__':
    main()
