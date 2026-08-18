#!/usr/bin/env python3
"""Force a PNG to 32-bit RGBA (colour type 6).

rsvg-convert emits 24-bit truecolour (colour type 2) whenever the rendered
image happens to be fully opaque. The Play Console asks for the app icon as a
"32-bit PNG", and its uploader has historically rejected 24-bit files, so the
store icons are normalised through here.

Pure stdlib, deterministic, and deliberately narrow: it handles only the
non-interlaced 8-bit RGB / RGBA output that rsvg-convert produces, and refuses
anything else rather than guessing.
"""
import struct
import sys
import zlib

_PAETH = None


def _unfilter(raw, width, height, bpp):
    stride = width * bpp
    out = bytearray(stride * height)
    prev = bytearray(stride)
    pos = 0
    for y in range(height):
        ft = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        if ft == 0:
            pass
        elif ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        else:
            raise ValueError('unsupported PNG filter type %d' % ft)
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return out


def _chunk(tag, payload):
    return (struct.pack('>I', len(payload)) + tag + payload
            + struct.pack('>I', zlib.crc32(tag + payload) & 0xFFFFFFFF))


def to_rgba(path):
    """Rewrite `path` as 8-bit RGBA. No-op if it already is. Returns True if changed."""
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('%s: not a PNG' % path)

    idat = bytearray()
    ihdr = None
    pos = 8
    while pos < len(data):
        (length,) = struct.unpack('>I', data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        if tag == b'IHDR':
            ihdr = payload
        elif tag == b'IDAT':
            idat += payload
        elif tag == b'IEND':
            break
        pos += 12 + length

    if ihdr is None:
        raise ValueError('%s: no IHDR' % path)
    width, height, depth, ctype, comp, filt, interlace = struct.unpack('>IIBBBBB', ihdr)
    if ctype == 6:
        return False
    if (depth, ctype, comp, filt, interlace) != (8, 2, 0, 0, 0):
        raise ValueError('%s: unsupported PNG (depth=%d colour=%d interlace=%d)'
                         % (path, depth, ctype, interlace))

    rgb = _unfilter(zlib.decompress(bytes(idat)), width, height, 3)

    stride_in, stride_out = width * 3, width * 4
    body = bytearray()
    for y in range(height):
        body.append(0)                       # filter: none
        row = rgb[y * stride_in:(y + 1) * stride_in]
        rgba = bytearray(stride_out)
        rgba[0::4] = row[0::3]
        rgba[1::4] = row[1::3]
        rgba[2::4] = row[2::3]
        rgba[3::4] = b'\xff' * width
        body += rgba

    out = bytearray(b'\x89PNG\r\n\x1a\n')
    out += _chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    out += _chunk(b'IDAT', zlib.compress(bytes(body), 9))
    out += _chunk(b'IEND', b'')
    open(path, 'wb').write(bytes(out))
    return True


if __name__ == '__main__':
    for p in sys.argv[1:]:
        print('%s %s' % ('converted' if to_rgba(p) else 'already RGBA', p))
