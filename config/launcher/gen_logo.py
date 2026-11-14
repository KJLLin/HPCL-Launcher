#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""HPCL square logo v2: PCL-blue tile, 2x2 HPCL block letters.

Drawn *per target size* (not downscaled from one big bitmap) so every icon
from 16px up stays crisp. A 5x5 square pixel font is laid out as a tight 2x2
(H P / C L) with a visible cross gutter. Minecraft feel comes from chunky
block letters + 1-2px extruded shadow; tech feel from the PCL-blue vertical
gradient, bevelled frame, inner keyline and corner brackets (detail sizes).
"""
from PIL import Image, ImageDraw
import struct, os, sys

# PCL blue palette
BLUE_TOP   = (26, 130, 248)
BLUE_MID   = (19, 112, 243)   # PCL C3
BLUE_BOT   = (11,  91, 203)   # C2
EDGE_DARK  = (7,  58, 150)
EDGE_LITE  = (120, 190, 255)
LETTER     = (255, 255, 255)
LETTER_SHA = (8,  56, 140)
CORNER     = (176, 218, 255)

# 5x5 square pixel font
FONT = {
    'H': ["10001", "10001", "11111", "10001", "10001"],
    'P': ["11110", "10001", "11110", "10000", "10000"],
    'C': ["01111", "10000", "10000", "10000", "01111"],
    'L': ["10000", "10000", "10000", "10000", "11111"],
}

def draw_logo(s, detail=True):
    im = Image.new('RGBA', (s, s))
    d = ImageDraw.Draw(im)
    # --- background: stepped vertical gradient (or flat for tiny sizes) ---
    if detail:
        for y in range(s):
            t = y / max(1, s - 1)
            if t < 0.5:
                f = t / 0.5
                c = tuple(int(BLUE_TOP[i] + (BLUE_MID[i]-BLUE_TOP[i])*f) for i in range(3))
            else:
                f = (t-0.5)/0.5
                c = tuple(int(BLUE_MID[i] + (BLUE_BOT[i]-BLUE_MID[i])*f) for i in range(3))
            d.line([(0, y), (s, y)], fill=c + (255,))
    else:
        d.rectangle([0, 0, s, s], fill=BLUE_MID + (255,))

    # --- bevelled frame ---
    b = max(1, round(s * 3 / 64))          # outer bevel thickness
    d.rectangle([0, 0, s-1, b-1], fill=EDGE_DARK + (255,))
    d.rectangle([0, 0, b-1, s-1], fill=EDGE_DARK + (255,))
    d.rectangle([0, s-b, s-1, s-1], fill=EDGE_DARK + (255,))
    d.rectangle([s-b, 0, s-1, s-1], fill=EDGE_DARK + (255,))
    hl = max(1, round(s * 2 / 64))
    d.rectangle([0, 0, s-b-1, hl-1], fill=EDGE_LITE + (255,))
    d.rectangle([0, 0, hl-1, s-b-1], fill=EDGE_LITE + (255,))

    if detail:
        # inner keyline
        k = max(b + 2, round(s * 5 / 64))
        d.line([(k, k), (s-1-k, k)], fill=EDGE_LITE + (255,))
        d.line([(k, k), (k, s-1-k)], fill=EDGE_LITE + (255,))
        d.line([(k, s-1-k), (s-1-k, s-1-k)], fill=EDGE_DARK + (255,))
        d.line([(s-1-k, k), (s-1-k, s-1-k)], fill=EDGE_DARK + (255,))
        # corner brackets, kept inside the frame band
        cl = max(2, round(s * 4 / 64))
        for (cx, cy, sx, sy) in [(b+1,b+1,1,1),(s-b-2,b+1,-1,1),
                                 (b+1,s-b-2,1,-1),(s-b-2,s-b-2,-1,-1)]:
            for i in range(cl):
                d.point((cx+sx*i, cy), fill=CORNER+(255,))
                d.point((cx, cy+sy*i), fill=CORNER+(255,))

    # --- 2x2 letters ---
    cell = max(1, s // 16)                  # one font pixel
    lw = 5 * cell
    gap = max(2, 2 * cell)
    total = 2 * lw + gap
    off = (s - total) // 2
    shadow = max(1, cell // 2) if detail else 0
    pos = {'H': (off, off), 'P': (off + lw + gap, off),
           'C': (off, off + lw + gap), 'L': (off + lw + gap, off + lw + gap)}
    for ch, (ox, oy) in pos.items():
        rows = FONT[ch]
        if shadow:
            for r, row in enumerate(rows):
                for c, v in enumerate(row):
                    if v == '1':
                        x, y = ox+c*cell+shadow, oy+r*cell+shadow
                        d.rectangle([x, y, x+cell-1, y+cell-1], fill=LETTER_SHA+(255,))
        for r, row in enumerate(rows):
            for c, v in enumerate(row):
                if v == '1':
                    x, y = ox+c*cell, oy+r*cell
                    d.rectangle([x, y, x+cell-1, y+cell-1], fill=LETTER+(255,))
    return im

def make_ico(images, path):
    """PNG-compressed ICO with one crisp frame per size (no resampling)."""
    sizes = sorted(images)
    n = len(sizes)
    header = struct.pack('<HHH', 0, 1, n)
    offset = 6 + 16 * n
    entries, blobs = [], []
    for s in sizes:
        png = images[s]
        w = 0 if s >= 256 else s
        entries.append(struct.pack('<BBBBHHII', w, w, 0, 0, 1, 32, len(png), offset))
        blobs.append(png)
        offset += len(png)
    with open(path, 'wb') as f:
        f.write(header)
        for e in entries:
            f.write(e)
        for b in blobs:
            f.write(b)

def main():
    out = sys.argv[1] if len(sys.argv) > 1 else '/workspace/hpcl/config/launcher'
    os.makedirs(out, exist_ok=True)
    sizes = [16, 20, 24, 32, 40, 48, 64, 96, 128, 256]
    pngs = {}
    for s in sizes:
        im = draw_logo(s, detail=(s >= 24))
        im.save(f'{out}/hpcl-logo-{s}.png')
        import io
        buf = io.BytesIO(); im.save(buf, format='PNG')
        pngs[s] = buf.getvalue()
    draw_logo(512, detail=True).save(f'{out}/hpcl-logo-512.png')
    make_ico(pngs, f'{out}/HPCL.ico')
    draw_logo(384, detail=True).save('/tmp/hpcl_logo_v2_preview.png')
    print('generated v2 per-size logo, ico bytes', os.path.getsize(f'{out}/HPCL.ico'))

if __name__ == '__main__':
    main()
