#!/bin/sh
# Cut a release: `make tag` (the version is now's), or `make tag VERSION=14.1003.10`
# to give it (a leading "v" is accepted). The tag is the release; this writes what
# app stores read from the source at that tag (F-Droid reads app/build.gradle.kts
# with a regex, not the version catalog and not git history) and points the tag at
# the commit that holds it:
#
#   1. versionName and versionCode in app/build.gradle.kts, as literals;
#   2. fastlane/metadata/android/en-US/changelogs/<versionCode>.txt, which must
#      already be written (F-Droid shows at most 500 bytes of it);
#   3. one commit, "chore(release): v<versionName>", and the annotated tag
#      v<versionName> on it.
#
# versionName is YY.MMDD.HH, the time elapsed since 2012-01-23 (scripts/version.sh);
# versionCode is the name without its dots:
#   14.1003.10 -> 14100310
# so the code follows from the version, is known before any build, rises with
# every hour and two names never share a code. It must not go below the code
# already in the file (Play and F-Droid refuse an update whose code is not
# higher), nor below any pre-release tag's on origin (.github/workflows/apk.yml),
# whose testers could not update. FORCE=1 skips both checks.
#
# A computed version moves on every hour, and the notes are named after the
# code: notes written for this release under an earlier hour's code (any
# changelog above the file's code and below the new one) are carried over to the
# new code, together with the same file in every other language's changelogs.
# More than one such file is an error; delete the stale ones.
#
# DRY_RUN=1 prints what it would do and changes nothing.
set -eu

gradle=app/build.gradle.kts
changes=fastlane/metadata/android/en-US/changelogs

die() { echo "release: $*" >&2; exit 1; }

cd "$(git rev-parse --show-toplevel)"

if [ -n "${1:-}" ]; then
	ver=${1#v}
else
	ver=$(sh scripts/version.sh) || die "cannot compute the version (scripts/version.sh)"
	echo "release: version $ver, computed for now"
fi
tag=v$ver

echo "$ver" | grep -Eq '^[1-9][0-9]?\.[0-9]{4}\.[0-9]{2}$' || die "$ver: want YY.MMDD.HH, e.g. 14.1003.10"
code=$(printf '%s' "$ver" | tr -d .)

# Exactly one literal line of each, or the rewrite below is ambiguous.
[ "$(grep -Ec '^[[:space:]]*versionCode = [0-9]+$' "$gradle")" = 1 ] || die "$gradle: want exactly one literal versionCode line"
[ "$(grep -Ec '^[[:space:]]*versionName = "[^"]*"$' "$gradle")" = 1 ] || die "$gradle: want exactly one literal versionName line"
cur=$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)$/\1/p' "$gradle")
[ "$code" -ge "$cur" ] || [ "${FORCE:-}" = 1 ] || die "$tag: versionCode $code is below $cur (FORCE=1 to write it anyway)"
if [ "${FORCE:-}" != 1 ]; then
	refs=$(git ls-remote --tags --refs origin) || die "cannot list origin's tags to check pre-release codes (FORCE=1 skips)"
	pre=$(printf '%s\n' "$refs" | sed -n 's|.*refs/tags/v\([1-9][0-9]\{0,1\}\)\.\([0-9]\{4\}\)\.\([0-9]\{2\}\)-[0-9A-Za-z][0-9A-Za-z.]*$|\1\2\3|p' | sort -n | tail -n 1)
	[ -z "$pre" ] || [ "$code" -ge "$pre" ] || die "$tag: versionCode $code is below pre-release $pre on origin; its testers could not update (FORCE=1 to cut it anyway)"
fi

notes=$changes/$code.txt
from=
if [ ! -s "$notes" ]; then
	# Notes of this unreleased version under an earlier code: above every released
	# code (the file's), below the one now being cut.
	stale=
	for f in "$changes"/*.txt; do
		[ -f "$f" ] || continue
		n=${f##*/}
		n=${n%.txt}
		case $n in '' | *[!0-9]*) continue ;; esac
		if [ "$n" -gt "$cur" ] && [ "$n" -lt "$code" ]; then stale="$stale $f"; fi
	done
	set -- $stale
	[ $# -le 1 ] || die "several notes for an unreleased version:$stale; keep one"
	[ $# = 0 ] || from=$1
	[ -n "$from" ] || die "write $notes first: the release notes stores show for this version"
	[ -s "$from" ] || die "$from is empty: the release notes stores show for this version"
fi
src=${from:-$notes}

# The other languages' notes under the same earlier code travel with the English.
others=
if [ -n "$from" ]; then
	for f in fastlane/metadata/android/*/changelogs/"${from##*/}"; do
		[ -f "$f" ] && [ "$f" != "$from" ] || continue
		[ ! -e "${f%/*}/$code.txt" ] || die "${f%/*}/$code.txt and $f are both waiting; keep one"
		others="$others $f"
	done
fi
size=$(LC_ALL=C wc -c <"$src" | tr -d ' ')
[ "$size" -le 500 ] || die "$src is $size bytes; F-Droid shows 500 - shorten it"

git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "$tag already exists"

if [ "${DRY_RUN:-}" = 1 ]; then
	echo "tag $tag  versionName $ver  versionCode $code (was $cur)"
	[ -z "$from" ] || echo "notes $from would become $notes"
	for f in $others; do echo "notes $f would become ${f%/*}/$code.txt"; done
	echo "--- $src"
	cat "$src"
	exit 0
fi

git diff --quiet && git diff --cached --quiet || die "commit or stash your changes first: the tag must hold exactly what is committed"

# Renames a notes file to the new code, staging the rename if git tracks it.
carry() {
	if git ls-files --error-unmatch "$1" >/dev/null 2>&1; then git mv "$1" "$2"; else mv "$1" "$2"; fi
	echo "release: notes $1 -> $2"
}
moved=
if [ -n "$from" ]; then
	carry "$from" "$notes"
	for f in $others; do
		carry "$f" "${f%/*}/$code.txt"
		moved="$moved ${f%/*}/$code.txt"
	done
fi

sed -i.bak -e "s/^\([[:space:]]*versionCode = \)[0-9][0-9]*$/\1$code/" \
	-e "s/^\([[:space:]]*versionName = \)\"[^\"]*\"$/\1\"$ver\"/" "$gradle"
rm -f "$gradle.bak"
grep -Eq "^[[:space:]]*versionCode = $code\$" "$gradle" && grep -Fq "versionName = \"$ver\"" "$gradle" ||
	die "$gradle: the version lines were not rewritten"

# The other languages' notes for this code, carried or written for it directly.
for f in fastlane/metadata/android/*/changelogs/"$code.txt"; do
	[ ! -f "$f" ] || moved="$moved $f"
done
git add "$gradle" "$notes" $moved
# Nothing to commit when the file already held this version and the notes were
# committed: the tag then goes on HEAD as it is.
git diff --cached --quiet || git commit -q -m "chore(release): $tag"
git tag -a "$tag" -m "$tag"
echo "$tag: versionCode $code at $(git rev-parse HEAD)"
echo "push with: git push origin HEAD && git push origin $tag"
