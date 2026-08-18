#!/usr/bin/env python3
"""FastTrack48 — "Pentad Aperture" brand mark generator.

Single source of truth for every launcher / store / listing asset.
Deterministic: rerunning reproduces byte-identical SVG sources.

GEOMETRY (icon units, 108x108 adaptive canvas, centre 54,54, safe r=34)
  phi              = 1.6180339887
  blades           = 5                       (pentagon: the only polygon whose
                                              diagonal:side ratio IS phi)
  pitch            = 360/5 = 72 deg
  blade sweep      = 72/phi = 44.50290 deg   (golden division of the pitch)
  aperture gap     = 72 - 72/phi = 27.49710 deg
  blade length     = r * sweep_rad = phi^2 * w   <- the invariant that fixes r,w
  outer envelope   = r + w/2 = 34             (adaptive safe circle)
    => w = 34 / (phi^2/ (72/phi * pi/180) + 1/2) = 8.78406
    => r = 29.60797
  rotation         : gap centred at 6 o'clock (the app dial's downward opening,
                     "a vessel being filled"); blade centres at
                     270, 342, 54, 126, 198 deg  (SVG y-down, 0 = 3 o'clock)
                     -> mirror-symmetric about the vertical axis,
                        one blade centred at 12 o'clock.
  colour           : the app's journey ramp, sampled at 5 golden stations and
                     interpolated in LINEAR LIGHT (gamma 2.2), laid down
                     clockwise from the 126 deg blade == the dial's 135 deg
                     START_ANGLE.
"""
import math, os, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import png_rgba

PHI  = (1 + 5 ** 0.5) / 2
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RSVG = '/opt/homebrew/bin/rsvg-convert'
OUT  = os.path.join(ROOT, 'playconsole')
SVGD = os.path.join(OUT, 'svg')

# ---------------------------------------------------------------- geometry ---
CX = CY = 54.0
SAFE  = 34.0
N     = 5
PITCH = 360.0 / N
SWEEP = PITCH / PHI                      # 44.502896 deg
SWEEP_R = math.radians(SWEEP)
W = SAFE / (PHI ** 2 / SWEEP_R + 0.5)    # 8.784063
R = SAFE - W / 2                         # 29.607969
A0 = 270.0 - SWEEP / 2                   # first blade start (top blade)
CENTRES = [270.0 + k * PITCH for k in range(N)]

# ------------------------------------------------------------------ colour ---
RAMP = ['#F5F0E1', '#EDD9A0', '#E8CD7E', '#FFB35C',
        '#FF7A6B', '#F48FB1', '#CE8FFF', '#A98BFF']

def _hex2rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))

def _rgb2hex(c):
    return '#%02X%02X%02X' % tuple(
        max(0, min(255, int(round(x * 255 + 1e-9)))) for x in c)

def ramp(t):
    """Sample RAMP at t in [0,1], interpolating in linear light (gamma 2.2)."""
    t = max(0.0, min(1.0, t))
    x = t * (len(RAMP) - 1)
    i = min(int(x), len(RAMP) - 2)
    f = x - i
    a, b = _hex2rgb(RAMP[i]), _hex2rgb(RAMP[i + 1])
    return _rgb2hex(tuple(((1 - f) * a[j] ** 2.2 + f * b[j] ** 2.2) ** (1 / 2.2)
                          for j in range(3)))

BLADE_COLOURS = [ramp(k / (N - 1.0)) for k in range(N)]     # ivory -> violet
# clockwise from the 126 deg blade (== dial START_ANGLE 135, clockwise travel)
ORDER = [126.0, 198.0, 270.0, 342.0, 54.0]
COLOUR_OF = {c: BLADE_COLOURS[i] for i, c in enumerate(ORDER)}

# ------------------------------------------------------------- ground tones --
BG_IN, BG_MID, BG_OUT = '#241C36', '#181228', '#0C0914'

# ------------------------------------------------------------ arc -> cubic ---
def _p(cx, cy, r, deg):
    a = math.radians(deg)
    return (cx + r * math.cos(a), cy + r * math.sin(a))

def arc_path(cx, cy, r, a0, sweep, prec=4):
    """Elliptical arc as cubic Beziers (vector-drawable has no 'A' command).
    Segments <= 90 deg; k = 4/3 tan(theta/4)."""
    segs = max(1, int(math.ceil(abs(sweep) / 90.0)))
    step = sweep / segs
    x, y = _p(cx, cy, r, a0)
    d = ['M%s,%s' % (round(x, prec), round(y, prec))]
    th = math.radians(step)
    k = 4.0 / 3.0 * math.tan(th / 4.0)
    for s in range(segs):
        b0 = a0 + s * step
        b1 = b0 + step
        x0, y0 = _p(cx, cy, r, b0)
        x1, y1 = _p(cx, cy, r, b1)
        t0 = (-math.sin(math.radians(b0)), math.cos(math.radians(b0)))
        t1 = (-math.sin(math.radians(b1)), math.cos(math.radians(b1)))
        c1 = (x0 + k * r * t0[0], y0 + k * r * t0[1])
        c2 = (x1 - k * r * t1[0], y1 - k * r * t1[1])
        d.append('C%s,%s %s,%s %s,%s' % tuple(
            round(v, prec) for v in (c1[0], c1[1], c2[0], c2[1], x1, y1)))
    return ' '.join(d)

def blades(cx=CX, cy=CY, scale=1.0, mono=None, opacity=None):
    """SVG <path> strings for the five blades, scaled about (cx,cy)."""
    r, w = R * scale, W * scale
    out = []
    for k in range(N):
        c = CENTRES[k] % 360.0
        col = mono or COLOUR_OF[c]
        op = '' if opacity is None else ' opacity="%s"' % opacity
        out.append(
            '<path d="%s" fill="none" stroke="%s" stroke-width="%s" '
            'stroke-linecap="round"%s/>'
            % (arc_path(cx, cy, r, c - SWEEP / 2, SWEEP), col,
               round(w, 4), op))
    return out

# ---------------------------------------------------------------- svg parts --
def defs_ground(idp=''):
    return (
      '<radialGradient id="g%s" cx="50%%" cy="42%%" r="72%%">'
      '<stop offset="0" stop-color="%s"/>'
      '<stop offset="0.55" stop-color="%s"/>'
      '<stop offset="1" stop-color="%s"/></radialGradient>' % (idp, BG_IN, BG_MID, BG_OUT))

def svg_icon(shape='square', px=512, bleed=True):
    """Legacy / store raster: the 108 canvas cropped to the 72dp mask window."""
    vb = '18 18 72 72' if bleed else '0 0 108 108'
    if shape == 'circle':
        clip = '<clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath>'
        ground = '<rect x="18" y="18" width="72" height="72" fill="url(#g)" clip-path="url(#c)"/>'
        body = '<g clip-path="url(#c)">%s</g>' % ''.join(blades())
    elif shape == 'squircle':
        clip = '<clipPath id="c"><rect x="18" y="18" width="72" height="72" rx="15.5" ry="15.5"/></clipPath>'
        ground = '<rect x="18" y="18" width="72" height="72" fill="url(#g)" clip-path="url(#c)"/>'
        body = '<g clip-path="url(#c)">%s</g>' % ''.join(blades())
        clip = clip
    else:
        clip = ''
        ground = '<rect x="18" y="18" width="72" height="72" fill="url(#g)"/>'
        body = ''.join(blades())
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
            'viewBox="%s"><defs>%s%s</defs>%s%s</svg>'
            % (px, px, vb, defs_ground(), clip, ground, body))

def svg_mark_only(px=1024):
    """Transparent-ground mark, tight to the safe circle."""
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
            'viewBox="20 20 68 68">%s</svg>' % (px, px, ''.join(blades())))

# --------------------------------------------------------------- wordmark ----
FACE   = 'Avenir Next'
IVORY  = '#F5F0E1'
GOLD   = ramp(0.25)
MUTED  = '#9C93B0'

def wordmark(x, y, size, anchor='start', sub=None, sub_size=None, sub_dy=None,
             sub_colour=MUTED, ls=None):
    ls = size * 0.006 if ls is None else ls
    t = ('<text x="%s" y="%s" text-anchor="%s" font-family="%s, Avenir, '
         'Helvetica Neue, sans-serif" font-weight="600" font-size="%s" '
         'letter-spacing="%s" fill="%s">FastTrack<tspan fill="%s">48</tspan></text>'
         % (round(x, 2), round(y, 2), anchor, FACE, round(size, 2),
            round(ls, 3), IVORY, GOLD))
    if sub:
        ss = sub_size or size / PHI ** 2.5
        dy = sub_dy or size * 0.62
        t += ('<text x="%s" y="%s" text-anchor="%s" font-family="%s, Avenir, '
              'Helvetica Neue, sans-serif" font-weight="500" font-size="%s" '
              'letter-spacing="%s" fill="%s">%s</text>'
              % (round(x, 2), round(y + dy, 2), anchor, FACE, round(ss, 2),
                 round(ss * 0.075, 3), sub_colour, sub))
    return t

TAGLINE = 'Intermittent &amp; extended fasting'

def svg_lockup(W_, H_, px_w=None):
    """Horizontal storefront lockup. Every station is a golden division of H_.

       mark centre x  = m + Rm      m = H_/phi^4   Rm = (H_/phi)/2
       title baseline = H_/phi - H_/phi^4
       sub   baseline = H_/phi
       text  left     = mark right + m
    """
    m  = H_ / PHI ** 4
    Rm = (H_ / PHI) / 2
    mx, my = m + Rm, H_ / 2
    tx = m + 2 * Rm + m
    ty = H_ / PHI - H_ / PHI ** 4
    sy = H_ / PHI
    scale = Rm / SAFE
    fs = (W_ - tx - m) / 5.9          # "FastTrack48" ~= 5.62em + tracking
    fs = min(fs, H_ * 0.19)
    body = ''.join(blades(mx, my, scale))
    txt = wordmark(tx, ty, fs, sub=TAGLINE, sub_size=fs / PHI ** 2.7,
                   sub_dy=sy - ty)
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
            'viewBox="0 0 %d %d"><defs>%s</defs>'
            '<rect width="%d" height="%d" fill="url(#g)"/>%s%s</svg>'
            % (px_w or W_, int(round((px_w or W_) * H_ / W_)), W_, H_,
               defs_ground(), W_, H_, body, txt))

def svg_stacked(W_, H_):
    """Centred stacked lockup (TV banner / hero): mark above, wordmark below.
       Mark centre sits on the upper golden section of the height."""
    cy = H_ / PHI ** 2 * 1.06
    Rm = H_ / PHI ** 2 / 2 * 1.10
    scale = Rm / SAFE
    fs = min(W_ / 7.2, H_ * 0.135)
    ty = cy + Rm + fs * 1.05
    sy = ty + fs / PHI ** 1.3
    body = ''.join(blades(W_ / 2, cy, scale))
    txt = wordmark(W_ / 2, ty, fs, anchor='middle', sub=TAGLINE,
                   sub_size=fs / PHI ** 2.7, sub_dy=sy - ty)
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
            'viewBox="0 0 %d %d"><defs>%s</defs>'
            '<rect width="%d" height="%d" fill="url(#g)"/>%s%s</svg>'
            % (W_, H_, W_, H_, defs_ground(), W_, H_, body, txt))

# ------------------------------------------------------- android resources ---
VD_HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- GENERATED by scripts/icon/gen_icon.py - do not hand-edit -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp"\n'
           '    android:height="108dp"\n'
           '    android:viewportWidth="108"\n'
           '    android:viewportHeight="108">\n')

def vd_foreground(mono=None):
    s = VD_HEAD
    for k in range(N):
        c = CENTRES[k] % 360.0
        col = mono or ('#FF' + COLOUR_OF[c][1:])
        s += ('    <path\n'
              '        android:pathData="%s"\n'
              '        android:strokeColor="%s"\n'
              '        android:strokeWidth="%s"\n'
              '        android:strokeLineCap="round" />\n'
              % (arc_path(CX, CY, R, c - SWEEP / 2, SWEEP), col, round(W, 4)))
    return s + '</vector>\n'

def vd_background():
    head = VD_HEAD.replace(
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"')
    return (head +
            '    <path android:pathData="M0,0h108v108h-108z">\n'
            '        <aapt:attr name="android:fillColor">\n'
            '            <gradient\n'
            '                android:type="radial"\n'
            '                android:centerX="54"\n'
            '                android:centerY="45.36"\n'
            '                android:gradientRadius="77.76">\n'
            '                <item android:offset="0" android:color="%s" />\n'
            '                <item android:offset="0.55" android:color="%s" />\n'
            '                <item android:offset="1" android:color="%s" />\n'
            '            </gradient>\n'
            '        </aapt:attr>\n'
            '    </path>\n'
            '</vector>\n' % ('#FF' + BG_IN[1:], '#FF' + BG_MID[1:],
                             '#FF' + BG_OUT[1:]))

ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '\t<background android:drawable="@drawable/ic_launcher_background" />\n'
            '\t<foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '\t<monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')

# ------------------------------------------------------------------ driver ---
def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(text)
    return path

def png(svg_text, out, wpx, hpx=None):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    tmp = out + '.svg'
    with open(tmp, 'w') as f:
        f.write(svg_text)
    cmd = [RSVG, '-w', str(wpx)] + (['-h', str(hpx)] if hpx else []) + \
          ['-f', 'png', '-o', out, tmp]
    subprocess.run(cmd, check=True)
    os.remove(tmp)

def main():
    res = os.path.join(ROOT, 'app', 'src', 'main', 'res')

    # ---- 1. android vector resources
    w(os.path.join(res, 'drawable', 'ic_launcher_foreground.xml'), vd_foreground())
    w(os.path.join(res, 'drawable', 'ic_launcher_monochrome.xml'),
      vd_foreground(mono='#FF000000'))
    w(os.path.join(res, 'drawable', 'ic_launcher_background.xml'), vd_background())
    w(os.path.join(res, 'values', 'ic_launcher_background.xml'),
      '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
      '    <color name="ic_launcher_background">%s</color>\n</resources>\n' % BG_MID)
    for n in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        w(os.path.join(res, 'mipmap-anydpi-v26', n), ADAPTIVE)

    # ---- 2. legacy raster ladder
    for dpi, sz in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96),
                    ('xxhdpi', 144), ('xxxhdpi', 192)):
        png(svg_icon('squircle'), os.path.join(res, 'mipmap-%s' % dpi, 'ic_launcher.png'), sz)
        png(svg_icon('circle'), os.path.join(res, 'mipmap-%s' % dpi, 'ic_launcher_round.png'), sz)

    # ---- 3. in-app about icon (96dp base)
    for dpi, sz in (('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144)):
        png(svg_icon('circle'), os.path.join(res, 'drawable-%s' % dpi, 'app_icon.png'), sz)

    # ---- 4. store icons
    store512 = svg_icon('square')
    png(store512, os.path.join(ROOT, 'app', 'src', 'main', 'ic_launcher-playstore.png'), 512)
    png(store512, os.path.join(ROOT, 'fastlane', 'metadata', 'android', 'en-US',
                               'images', 'icon.png'), 512)
    png(store512, os.path.join(OUT, 'icon-512.png'), 512)
    png(store512, os.path.join(OUT, 'icon-1024.png'), 1024)
    png(svg_icon('squircle'), os.path.join(OUT, 'icon-1024-squircle.png'), 1024)
    png(svg_icon('circle'), os.path.join(OUT, 'icon-1024-round.png'), 1024)
    png(svg_mark_only(), os.path.join(OUT, 'mark-2048-transparent.png'), 2048)

    # rsvg emits 24-bit truecolour whenever the render is fully opaque; Play asks
    # for the store icon as a 32-bit PNG, so the opaque square icons are widened
    # to RGBA. The feature graphic is deliberately left without an alpha channel:
    # Play rejects transparency there.
    png_rgba.to_rgba(os.path.join(ROOT, 'app', 'src', 'main', 'ic_launcher-playstore.png'))
    png_rgba.to_rgba(os.path.join(ROOT, 'fastlane', 'metadata', 'android', 'en-US',
                                  'images', 'icon.png'))
    for name in ('icon-512.png', 'icon-1024.png', 'icon-1024-squircle.png',
                 'icon-1024-round.png'):
        png_rgba.to_rgba(os.path.join(OUT, name))

    # ---- 5. storefront lockups
    fg = svg_lockup(1024, 500)
    png(fg, os.path.join(OUT, 'feature-graphic-1024x500.png'), 1024, 500)
    png(fg, os.path.join(ROOT, 'fastlane', 'metadata', 'android', 'en-US',
                         'images', 'featureGraphic.png'), 1024, 500)
    png(fg, os.path.join(OUT, 'feature-graphic-4096x2000.png'), 4096, 2000)
    png(svg_stacked(1280, 720), os.path.join(OUT, 'tv-banner-1280x720.png'), 1280, 720)
    png(svg_stacked(3840, 2160), os.path.join(OUT, 'hero-3840x2160.png'), 3840, 2160)

    # ---- 6. svg sources
    w(os.path.join(SVGD, 'icon.svg'), svg_icon('square', 1024))
    w(os.path.join(SVGD, 'icon-round.svg'), svg_icon('circle', 1024))
    w(os.path.join(SVGD, 'icon-squircle.svg'), svg_icon('squircle', 1024))
    w(os.path.join(SVGD, 'mark.svg'), svg_mark_only())
    w(os.path.join(SVGD, 'feature-graphic.svg'), svg_lockup(1024, 500))
    w(os.path.join(SVGD, 'lockup-stacked.svg'), svg_stacked(1280, 720))

    print('phi=%.9f sweep=%.6f w=%.6f r=%.6f envelope=%.6f'
          % (PHI, SWEEP, W, R, R + W / 2))
    print('blades:', ' '.join('%.0f=%s' % (c % 360, COLOUR_OF[c % 360]) for c in CENTRES))

if __name__ == '__main__':
    main()
