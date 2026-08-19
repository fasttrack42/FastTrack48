#!/usr/bin/env python3
"""Bring phone screenshots inside Play's mandatory aspect-ratio rule.

Play requires that "the maximum dimension of your screenshot can't be more than
twice as long as the minimum dimension". A modern 20:9 phone capture is
1080x2400, i.e. 2.222:1, which is rejected. Cropping would cut content, so this
widens the canvas to exactly 2:1 by replicating the leftmost and rightmost
column outwards — on the app's vertical gradient that extension is seamless,
where a flat pad colour would show a seam.

Play also requires screenshots to be JPEG or 24-bit PNG with no alpha, so the
output is flattened to opaque RGB on the way through.

sips handles the JPEG codec at both ends; the geometry is done here so it stays
exact. Pure stdlib otherwise.
"""
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                '..', 'icon'))
from png_rgba import _chunk, _unfilter  # noqa: E402

import struct  # noqa: E402
import zlib  # noqa: E402

MAX_RATIO = 2.0


def _read_png(path):
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('%s: not a PNG' % path)
    idat, ihdr, pos = bytearray(), None, 8
    while pos < len(data):
        (length,) = struct.unpack('>I', data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        if tag == b'IHDR':
            ihdr = data[pos + 8:pos + 8 + length]
        elif tag == b'IDAT':
            idat += data[pos + 8:pos + 8 + length]
        elif tag == b'IEND':
            break
        pos += 12 + length
    w, h, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', ihdr)
    if depth != 8 or ctype not in (2, 6) or interlace:
        raise ValueError('%s: unsupported PNG (depth=%d colour=%d interlace=%d)'
                         % (path, depth, ctype, interlace))
    bpp = 3 if ctype == 2 else 4
    return w, h, bpp, _unfilter(zlib.decompress(bytes(idat)), w, h, bpp)


def _write_rgb_png(path, w, h, rows):
    body = bytearray()
    for row in rows:
        body.append(0)
        body += row
    out = bytearray(b'\x89PNG\r\n\x1a\n')
    out += _chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    out += _chunk(b'IDAT', zlib.compress(bytes(body), 9))
    out += _chunk(b'IEND', b'')
    open(path, 'wb').write(bytes(out))


def widen(src_png, dst_png):
    """Extend to 2:1 by edge replication, flattening any alpha. Returns the new size."""
    w, h, bpp, pix = _read_png(src_png)
    lo, hi = min(w, h), max(w, h)
    if hi <= lo * MAX_RATIO:
        target_w = w
    elif h > w:
        target_w = -(-h // 2)          # ceil, so the ratio lands at or under 2.0
    else:
        raise ValueError('%s: landscape shots need a taller canvas, not wider' % src_png)

    pad = target_w - w
    left, right = pad // 2, pad - pad // 2
    stride = w * bpp
    rows = []
    for y in range(h):
        line = pix[y * stride:(y + 1) * stride]
        rgb = bytearray(w * 3)
        rgb[0::3], rgb[1::3], rgb[2::3] = line[0::bpp], line[1::bpp], line[2::bpp]
        rows.append(bytes(rgb[:3]) * left + bytes(rgb) + bytes(rgb[-3:]) * right)
    _write_rgb_png(dst_png, target_w, h, rows)
    return target_w, h


def sips(*args):
    subprocess.run(['sips'] + list(args), check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def process(path):
    """Normalise one screenshot in place, keeping its filename and format."""
    ext = os.path.splitext(path)[1].lower()
    with tempfile.TemporaryDirectory() as tmp:
        as_png = os.path.join(tmp, 'in.png')
        if ext == '.png':
            as_png = path
        else:
            sips('-s', 'format', 'png', path, '--out', as_png)
        widened = os.path.join(tmp, 'out.png')
        size = widen(as_png, widened)
        if ext == '.png':
            os.replace(widened, path)
        else:
            sips('-s', 'format', 'jpeg', '-s', 'formatOptions', 'best',
                 widened, '--out', path)
    return size


if __name__ == '__main__':
    for p in sys.argv[1:]:
        print('%-20s -> %dx%d' % ((os.path.basename(p),) + process(p)))
