#!/bin/sh
# Cut a release: `make tag VERSION=14.0907.16` (a leading "v" is accepted).
# The tag is the release; this writes what app stores read from the source at
# that tag (F-Droid reads app/build.gradle.kts with a regex, not the version
# catalog and not git history) and points the tag at the commit that holds it:
#
#   1. versionName and versionCode in app/build.gradle.kts, as literals;
#   2. fastlane/metadata/android/en-US/changelogs/<versionCode>.txt, which must
#      already be written (F-Droid shows at most 500 bytes of it);
#   3. one commit, "chore(release): v<versionName>", and the annotated tag
#      v<versionName> on it.
#
# versionName is MAJOR.MMDD.HH; versionCode is the name without its dots:
#   14.0907.16 -> 14090716
# so the code follows from the version, is known before any build, and two
# names never share a code. It must not go below the code already in the file
# (Play and F-Droid refuse an update whose code is not higher), nor below any
# pre-release tag's on origin (.github/workflows/apk.yml), whose testers could
# not update. FORCE=1 skips both checks.
#
# DRY_RUN=1 prints what it would do and changes nothing.
set -eu

ver=${1:?usage: release.sh MAJOR.MMDD.HH (e.g. 14.0907.16)}
ver=${ver#v}
tag=v$ver
gradle=app/build.gradle.kts
changes=fastlane/metadata/android/en-US/changelogs

die() { echo "release: $*" >&2; exit 1; }

cd "$(git rev-parse --show-toplevel)"

echo "$ver" | grep -Eq '^[1-9][0-9]?\.[0-9]{4}\.[0-9]{2}$' || die "$ver: want MAJOR.MMDD.HH, e.g. 14.0907.16"
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
[ -s "$notes" ] || die "write $notes first: the release notes stores show for this version"
size=$(LC_ALL=C wc -c <"$notes" | tr -d ' ')
[ "$size" -le 500 ] || die "$notes is $size bytes; F-Droid shows 500 - shorten it"

git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "$tag already exists"

if [ "${DRY_RUN:-}" = 1 ]; then
	echo "tag $tag  versionName $ver  versionCode $code (was $cur)"
	echo "--- $notes"
	cat "$notes"
	exit 0
fi

git diff --quiet && git diff --cached --quiet || die "commit or stash your changes first: the tag must hold exactly what is committed"

sed -i.bak -e "s/^\([[:space:]]*versionCode = \)[0-9][0-9]*$/\1$code/" \
	-e "s/^\([[:space:]]*versionName = \)\"[^\"]*\"$/\1\"$ver\"/" "$gradle"
rm -f "$gradle.bak"
grep -Eq "^[[:space:]]*versionCode = $code\$" "$gradle" && grep -Fq "versionName = \"$ver\"" "$gradle" ||
	die "$gradle: the version lines were not rewritten"

git add "$gradle" "$notes"
# Nothing to commit when the file already held this version and the notes were
# committed: the tag then goes on HEAD as it is.
git diff --cached --quiet || git commit -q -m "chore(release): $tag"
git tag -a "$tag" -m "$tag"
echo "$tag: versionCode $code at $(git rev-parse HEAD)"
echo "push with: git push origin HEAD && git push origin $tag"
