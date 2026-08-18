#!/usr/bin/env python3
"""Play Console pre-upload validation.

Checks everything that can be checked locally against Google Play's published
listing and artifact rules, so a rejection is discovered here rather than three
minutes into an upload. Pure stdlib: no Pillow, no fastlane, no network.

Exit status 0 = clean (warnings may still be printed), 1 = at least one error.
"""
import os
import re
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
META = os.path.join(ROOT, 'fastlane', 'metadata', 'android')

ERRORS = []
WARNINGS = []


def err(msg):
	ERRORS.append(msg)


def warn(msg):
	WARNINGS.append(msg)


# --------------------------------------------------------------- image probes --
def png_info(path):
	"""(width, height, bit_depth, colour_type) from the IHDR chunk."""
	with open(path, 'rb') as fh:
		head = fh.read(26)
	if head[:8] != b'\x89PNG\r\n\x1a\n' or head[12:16] != b'IHDR':
		raise ValueError('%s: not a PNG' % path)
	w, h, depth, ctype = struct.unpack('>IIBB', head[16:26])
	return w, h, depth, ctype


_SOF = set(range(0xC0, 0xD0)) - {0xC4, 0xC8, 0xCC}


def jpeg_info(path):
	"""(width, height) from the first start-of-frame marker."""
	with open(path, 'rb') as fh:
		data = fh.read()
	if data[:2] != b'\xff\xd8':
		raise ValueError('%s: not a JPEG' % path)
	i = 2
	while i + 3 < len(data):
		if data[i] != 0xFF:
			i += 1
			continue
		marker = data[i + 1]
		if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
			i += 2
			continue
		(seglen,) = struct.unpack('>H', data[i + 2:i + 4])
		if marker in _SOF:
			h, w = struct.unpack('>HH', data[i + 5:i + 9])
			return w, h
		i += 2 + seglen
	raise ValueError('%s: no SOF marker' % path)


def image_size(path):
	if path.lower().endswith('.png'):
		w, h, _, _ = png_info(path)
		return w, h
	return jpeg_info(path)


# ------------------------------------------------------------------- helpers --
def toml_version(key):
	src = open(os.path.join(ROOT, 'gradle', 'libs.versions.toml')).read()
	m = re.search(r'^%s\s*=\s*"([^"]+)"' % re.escape(key), src, re.M)
	if not m:
		err('gradle/libs.versions.toml: no "%s" entry' % key)
		return None
	return m.group(1)


def app_id():
	src = open(os.path.join(ROOT, 'app', 'build.gradle.kts')).read()
	m = re.search(r'applicationId\s*=\s*"([^"]+)"', src)
	return m.group(1) if m else None


def locales():
	if not os.path.isdir(META):
		err('fastlane metadata directory is missing: %s' % META)
		return []
	return sorted(d for d in os.listdir(META) if os.path.isdir(os.path.join(META, d)))


def read(path):
	try:
		return open(path, encoding='utf-8').read()
	except OSError:
		return None


# -------------------------------------------------------------------- checks --
def check_identity():
	pkg = app_id()
	if not pkg:
		err('app/build.gradle.kts: applicationId not found')
		return
	appfile = read(os.path.join(ROOT, 'fastlane', 'Appfile')) or ''
	m = re.search(r'package_name\("([^"]+)"\)', appfile)
	if not m:
		err('fastlane/Appfile: no package_name')
	elif m.group(1) != pkg:
		err('fastlane/Appfile package_name is "%s" but applicationId is "%s" — '
			'fastlane would publish to the wrong listing' % (m.group(1), pkg))


def check_listing_text():
	# Play's hard caps. Title 30, short description 80, full description 4000.
	for loc in locales():
		for name, cap in (('title.txt', 30), ('short_description.txt', 80),
						  ('full_description.txt', 4000)):
			path = os.path.join(META, loc, name)
			body = read(path)
			if body is None:
				(err if loc == 'en-US' else warn)('%s/%s is missing' % (loc, name))
				continue
			body = body.strip()
			if not body:
				err('%s/%s is empty' % (loc, name))
			elif len(body) > cap:
				err('%s/%s is %d characters, Play allows %d'
					% (loc, name, len(body), cap))


def check_changelogs(version_code):
	if not version_code:
		return
	for loc in locales():
		folder = os.path.join(META, loc, 'changelogs')
		# Locales that have never carried release notes are not expected to start
		# now; only a locale that already maintains a changelogs/ directory is held
		# to it. en-US is mandatory either way.
		if loc != 'en-US' and not os.path.isdir(folder):
			continue
		path = os.path.join(folder, '%s.txt' % version_code)
		body = read(path)
		if body is None:
			(err if loc == 'en-US' else warn)(
				'%s/changelogs/%s.txt is missing — the release would ship with no '
				'release notes for this locale' % (loc, version_code))
			continue
		if len(body.strip()) > 500:
			err('%s/changelogs/%s.txt is %d characters, Play allows 500'
				% (loc, version_code, len(body.strip())))


def check_icon():
	path = os.path.join(META, 'en-US', 'images', 'icon.png')
	if not os.path.exists(path):
		err('en-US/images/icon.png is missing (Play requires a 512x512 store icon)')
		return
	w, h, depth, ctype = png_info(path)
	if (w, h) != (512, 512):
		err('store icon is %dx%d, Play requires 512x512' % (w, h))
	if not (depth == 8 and ctype in (4, 6)):
		err('store icon is not a 32-bit PNG (bit depth %d, colour type %d); '
			'run `make icons` to regenerate' % (depth, ctype))
	if os.path.getsize(path) > 1024 * 1024:
		err('store icon is %.1f KB, Play allows 1 MB' % (os.path.getsize(path) / 1024))


def check_feature_graphic():
	path = os.path.join(META, 'en-US', 'images', 'featureGraphic.png')
	if not os.path.exists(path):
		err('en-US/images/featureGraphic.png is missing '
			'(Play requires a 1024x500 feature graphic)')
		return
	w, h, depth, ctype = png_info(path)
	if (w, h) != (1024, 500):
		err('feature graphic is %dx%d, Play requires 1024x500' % (w, h))
	if ctype in (4, 6):
		warn('feature graphic carries an alpha channel; Play rejects transparency '
			 'in feature graphics. It must be fully opaque.')
	if os.path.getsize(path) > 15 * 1024 * 1024:
		err('feature graphic exceeds Play\'s 15 MB limit')


def check_screenshots():
	base = os.path.join(META, 'en-US', 'images')
	phone = os.path.join(base, 'phoneScreenshots')
	shots = []
	if os.path.isdir(phone):
		shots = sorted(f for f in os.listdir(phone)
					   if f.lower().endswith(('.png', '.jpg', '.jpeg')))
	if len(shots) < 2:
		err('Play requires at least 2 phone screenshots; found %d' % len(shots))
	if len(shots) > 8:
		err('Play allows at most 8 screenshots per device type; found %d' % len(shots))
	for name in shots:
		path = os.path.join(phone, name)
		try:
			w, h = image_size(path)
		except ValueError as exc:
			err(str(exc))
			continue
		lo, hi = min(w, h), max(w, h)
		if lo < 320 or hi > 3840:
			err('screenshot %s is %dx%d; each side must be 320-3840 px' % (name, w, h))
		if hi > lo * 2:
			err('screenshot %s is %dx%d; the long side may not exceed twice the short '
				'side' % (name, w, h))
		if os.path.getsize(path) > 8 * 1024 * 1024:
			err('screenshot %s exceeds Play\'s 8 MB limit' % name)

	# Not fatal, but Play surfaces a "not optimised for tablets" badge without these.
	for folder, label in (('sevenInchScreenshots', '7-inch tablet'),
						  ('tenInchScreenshots', '10-inch tablet')):
		path = os.path.join(base, folder)
		count = len(os.listdir(path)) if os.path.isdir(path) else 0
		if count < 2:
			warn('no %s screenshots (%s/); without them the listing is flagged as '
				 'not optimised for large screens' % (label, folder))


def check_bundle(version_code):
	aab = os.path.join(ROOT, 'app', 'build', 'outputs', 'bundle', 'release',
					   'FastTrack48-release.aab')
	if not os.path.exists(aab):
		warn('no release bundle at app/build/outputs/bundle/release/'
			 'FastTrack48-release.aab — run `make bundle` before uploading')
		return
	size = os.path.getsize(aab)
	if size > 150 * 1024 * 1024:
		err('release bundle is %.1f MB; Play caps the base download at 150 MB'
			% (size / 1048576.0))
	# An AAB is jar-signed: the upload key leaves a top-level META-INF/*.RSA|DSA|EC
	# next to MANIFEST.MF. (base/root/META-INF/... are ordinary packaged assets and
	# say nothing about signing, so the prefix has to be matched exactly.)
	with zipfile.ZipFile(aab) as zf:
		names = zf.namelist()
	signed = any(n.startswith('META-INF/') and n.count('/') == 1
				 and n.upper().endswith(('.RSA', '.DSA', '.EC')) for n in names)
	if not signed:
		err('release bundle is not signed; Play rejects an unsigned upload. Check '
			'that KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD are '
			'exported.')


def check_manifest():
	path = os.path.join(ROOT, 'app', 'src', 'main', 'AndroidManifest.xml')
	body = read(path) or ''
	if re.search(r'\bpackage\s*=\s*"', body):
		warn('AndroidManifest.xml still declares the deprecated package= attribute; '
			 'AGP 8+ takes the package from namespace and ignores it')
	if 'android:debuggable="true"' in body:
		err('AndroidManifest.xml hard-codes android:debuggable="true"')


def main():
	version_code = toml_version('versionCode')
	version_name = toml_version('versionName')

	check_identity()
	check_listing_text()
	check_changelogs(version_code)
	check_icon()
	check_feature_graphic()
	check_screenshots()
	check_bundle(version_code)
	check_manifest()

	print('Play preflight — %s (versionCode %s)' % (version_name, version_code))
	for msg in WARNINGS:
		print('  warn   %s' % msg)
	for msg in ERRORS:
		print('  ERROR  %s' % msg)
	if not ERRORS and not WARNINGS:
		print('  ok     everything checkable locally passes')
	elif not ERRORS:
		print('\n%d warning(s), no blocking errors.' % len(WARNINGS))
	else:
		print('\n%d error(s), %d warning(s).' % (len(ERRORS), len(WARNINGS)))
	return 1 if ERRORS else 0


if __name__ == '__main__':
	sys.exit(main())
