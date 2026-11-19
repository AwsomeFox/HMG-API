#!/bin/sh
# A test build: one branch's change for a tester to try on their car, before it's merged.
# It keeps the version it's built on (1.1.0-rc.5: the app offers it as "Reinstall HMG-API",
# and the next real release updates over it); its files and tag carry a name, and its
# Car Reports say which build it is (installed.txt's build stamp).
#
#   tools/mktestbuild.sh <name> [--pr N] [--issue N] [--title "..."] [--note "..."] [--tests]
#       builds into out/test-builds/<name>/ (the downloads, SHA256SUMS.txt, notes.md) and stops:
#       read notes.md, try a download if you like
#   tools/mktestbuild.sh <name> --publish
#       tags the commit it was built from test-<name>, pushes the tag, publishes out/test-builds/<name>/ as a
#       pre-release (never Latest) with notes.md as its notes
#   tools/mktestbuild.sh <name> --delete
#       deletes the pre-release and its tag (once a real release carries the change)
#
# <name>: lowercase letters, digits and dashes, ending in its number: report-oom-1, selinux-2.
# Built from HEAD with no uncommitted changes, and HEAD must be on GitHub (the tag is pushed
# there). Signed like a release: the app with HMGAPI_KEY (default ~/hmg-private/keys/leafia-app.p12),
# the update with HMGAPI_RELEASE_KEY (default ~/hmg-private/keys/leafia-release.key); a
# throwaway key would make the app fail to install over the tester's. Mods are bundled as a
# release bundles them (tools/release-mods.txt through the catalogue).
set -e
cd "$(dirname "$0")/.."

die() { echo "mktestbuild: $*" >&2; exit 1; }

NAME=${1:-}
[ -n "$NAME" ] || die "usage: tools/mktestbuild.sh <name> [--pr N] [--issue N] [--title ...] [--note ...] [--tests] | --publish | --delete"
shift
echo "$NAME" | grep -qE '^[a-z0-9]+(-[a-z0-9]+)*-[0-9]+$' || die "$NAME: lowercase letters, digits and dashes, ending in its number (report-oom-1)"
TAG=test-$NAME
OUT=out/test-builds/$NAME
REPO=${HMGAPI_REPO:-Open-HMG/HMG-API}

PR= ISSUE= TITLE= NOTE= TESTS= ACTION=build
while [ $# -gt 0 ]; do
    case "$1" in
        --pr) PR=$2; shift ;;
        --issue) ISSUE=$2; shift ;;
        --title) TITLE=$2; shift ;;
        --note) NOTE=$2; shift ;;
        --tests) TESTS=1 ;;
        --publish) ACTION=publish ;;
        --delete) ACTION=delete ;;
        *) die "unknown option $1" ;;
    esac
    shift
done

if [ "$ACTION" = delete ]; then
    gh release delete "$TAG" -R "$REPO" --cleanup-tag --yes
    git tag -d "$TAG" 2>/dev/null || true
    echo "deleted the test build $TAG"
    exit 0
fi

if [ "$ACTION" = publish ]; then
    [ -f "$OUT/notes.md" ] && [ -f "$OUT/commit" ] || die "no build in $OUT: run tools/mktestbuild.sh $NAME first"
    C=$(cat "$OUT/commit")   # the commit it was built from, whatever is checked out now
    git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1 && die "tag $TAG is on GitHub already: pick the next number"
    git tag -a "$TAG" -m "Test build $NAME" "$C"
    git push -q origin "refs/tags/$TAG"
    gh release create "$TAG" -R "$REPO" --prerelease --latest=false --verify-tag \
        --title "$(cat "$OUT/title")" --notes-file "$OUT/notes.md" "$OUT"/*.zip "$OUT/SHA256SUMS.txt"
    exit 0
fi

# ---- build
git diff --quiet HEAD && git diff --cached --quiet || die "uncommitted changes: commit them (the build stamp must name a commit)"
C=$(git rev-parse HEAD)
git fetch -q origin
[ -n "$(git branch -r --contains "$C")" ] || die "HEAD $(git rev-parse --short HEAD) isn't on GitHub: push the branch first"
git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1 && die "tag $TAG is on GitHub already: pick the next number"

export HMGAPI_KEY=${HMGAPI_KEY:-$HOME/hmg-private/keys/leafia-app.p12}
export HMGAPI_RELEASE_KEY=${HMGAPI_RELEASE_KEY:-$HOME/hmg-private/keys/leafia-release.key}
[ -f "$HMGAPI_KEY" ] || die "no app key $HMGAPI_KEY (HMGAPI_KEY)"
[ -f "$HMGAPI_RELEASE_KEY" ] || die "no release key $HMGAPI_RELEASE_KEY (HMGAPI_RELEASE_KEY)"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ -z "$PR" ] && [ "$BRANCH" != HEAD ]; then
    PR=$(gh pr list -R "$REPO" --head "$BRANCH" --state open --json number -q '.[0].number' 2>/dev/null || true)
fi

if [ -n "$TESTS" ]; then tools/test-all.sh; fi
./build.sh
rm -rf out/packages
tools/catalog.py fetch out/packages --list tools/release-mods.txt
tools/mkinstallers.sh

V=$(cat out/payload/VERSION)
B=$(cat out/payload/BUILD)
case "$B" in *+*) die "the build says $B: built with uncommitted changes" ;; esac
rm -rf "$OUT"
mkdir -p "$OUT"
for k in usb nemesis nemesis-package adb; do
    cp "out/dist/hmgapi-$V-$k.zip" "$OUT/hmgapi-$V-$NAME-$k.zip"
done
cp "out/releases/hmgapi-$V.zip" "$OUT/hmgapi-$V-$NAME-update.zip"
(cd "$OUT" && sha256sum ./*.zip | sed 's#  \./#  #' > SHA256SUMS.txt)
echo "$C" > "$OUT/commit"

[ -n "$TITLE" ] || TITLE=$( [ -n "$PR" ] && gh pr view "$PR" -R "$REPO" --json title -q .title 2>/dev/null || echo "$BRANCH")
echo "Test build: $TITLE ($TAG)" > "$OUT/title"
S=$(git rev-parse --short "$C")
{
    echo "**This is a test build, not a release.** It's HMG-API $V plus one change under test (build \`$S\`$( [ -n "$PR" ] && echo ", PR #$PR")). Install it only if you're helping test."
    echo
    [ -n "$NOTE" ] && { echo "$NOTE"; echo; }
    echo "**Install:** unzip \`hmgapi-$V-$NAME-usb.zip\` onto a USB stick, plug it in, and open HMG-API → Updates: it offers **Reinstall HMG-API** (same version number, new build). The other downloads install the same build: \`-nemesis\` (NEMESIS 1.16+), \`-adb\`, and \`-update\` (only the signed update, for a stick's \`HMGAPI/updates\` folder)."
    echo
    echo "**Check it's this build:** a Car Report from it says \`HMG-API $V (build $S …)\`."
    echo
    echo "**Going back:** reinstall the $V release the same way, or let the next release update over this one."
    echo
    echo "Tested off-car only, unless it says otherwise above.$( [ -n "$ISSUE" ] && echo " Post results on #$ISSUE.") Checksums are in \`SHA256SUMS.txt\`."
} > "$OUT/notes.md"

echo
echo "test build $NAME: HMG-API $V, build $B"
ls -1 "$OUT"
echo
echo "read $OUT/notes.md (edit it if you like), then: tools/mktestbuild.sh $NAME --publish"
