#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HPCL icon library v7 -- ground-up rebuild.

Every glyph is hand-authored on a 0..24 grid so that SVGPath.setContent()
renders exactly what is drawn (no arc-bounding-box normalisation bugs).
Shapes follow PCL2's own FormMain.xaml / PageSetupLeft.xaml / PageDownloadLeft.xaml
Logo glyphs: play triangle, download tray, 3-band wifi, hexagon gear, 2x2 grid,
minimise line, T-shirt, cube, i-circle, ?-circle, magnifier, pickaxe, ...
"""
import math, json, re, sys

def poly(pts, close=True):
    s = "M" + " L".join(f"{x:.2f} {y:.2f}" for x, y in pts)
    return s + (" Z" if close else "")

def ring(cx, cy, r, n=16, rot=-90, ccw=False):
    """Regular polygon; ccw reverses winding (for even-odd holes)."""
    idx = range(n)[::-1] if ccw else range(n)
    pts = []
    for i in idx:
        t = 2 * math.pi * i / n + math.radians(rot)
        pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    return poly(pts)

def arc_band(cx, cy, r_out, r_in, a0, a1, steps=14):
    """A curved band (wifi arc) between two radii, angles in degrees."""
    outer, inner = [], []
    for i in range(steps + 1):
        t = math.radians(a0 + (a1 - a0) * i / steps)
        outer.append((cx + r_out * math.cos(t), cy + r_out * math.sin(t)))
        inner.append((cx + r_in * math.cos(t), cy + r_in * math.sin(t)))
    return poly(outer + inner[::-1])

def rect(x, y, w, h):
    return poly([(x, y), (x + w, y), (x + w, y + h), (x, y + h)])

LIB = {}

# ---------------------------------------------------------------- top bar
# PCL2 BtnTitleSelect0: play triangle
LIB['play'] = "M7 3.5 L20 12 L7 20.5 Z"
# PCL2 BtnTitleSelect1: download arrow into a tray
LIB['download'] = ("M10.5 2 h3 v9 h3.4 L12 16.8 L7.1 11 h3.4 Z "
                   "M3.5 17.5 h17 v1.8 a1.2 1.2 0 0 1 -1.2 1.2 H4.7 "
                   "a1.2 1.2 0 0 1 -1.2 -1.2 Z")
# PCL2 BtnTitleSelect2: three wifi bands + dot
_wifi = [arc_band(12, 19, 13.2, 10.6, 208, 332),
         arc_band(12, 19, 8.7, 6.1, 208, 332),
         arc_band(12, 19, 4.2, 1.9, 208, 332),
         ring(12, 19.6, 1.5, 12)]
LIB['wifi'] = " ".join(_wifi)
# PCL2 BtnTitleSelect3: hexagon gear (nut) -- outer hex ring + centre circle
LIB['gear_hex'] = (ring(12, 12, 11, 6, rot=0) + " " +
                   ring(12, 12, 7.6, 6, rot=0, ccw=True) + " " +
                   ring(12, 12, 3.9, 14))
# PCL2 BtnTitleSelect4: 2x2 grid of rounded squares
def grid_sq(x, y, w, h, t=1.9):
    return (poly([(x, y), (x+w, y), (x+w, y+h), (x, y+h)]) + " " +
            poly([(x+t, y+t), (x+t, y+h-t), (x+w-t, y+h-t), (x+w-t, y+t)]))
LIB['grid'] = " ".join(grid_sq(x, y, 9.4, 9.4) for x, y in [(2, 2), (12.6, 2), (2, 12.6), (12.6, 12.6)])
# PCL2 BtnTitleMin: F1 M0,0 h15 v2 h-15 v-2 Z  -> a plain line
LIB['minimize'] = rect(3, 11, 18, 2)
LIB['close'] = "M4.6 3 L12 10.4 L19.4 3 L21 4.6 L13.6 12 L21 19.4 L19.4 21 L12 13.6 L4.6 21 L3 19.4 L10.4 12 L3 4.6 Z"
LIB['back'] = ("M11 4 L3 12 l8 8 l1.7 -1.7 L6.4 12 l6.3 -6.3 Z "
               "M5.6 10.7 H21 v2.6 H5.6 Z")

# ---------------------------------------------------------------- geometry primitives
LIB['info'] = ring(12, 12, 10.5, 18) + " " + ring(12, 12, 8.1, 18, ccw=True) + " " + \
              rect(10.9, 10.5, 2.2, 7.2) + " " + rect(10.9, 6.6, 2.2, 2.2)
LIB['help'] = ring(12, 12, 10.5, 18) + " " + ring(12, 12, 8.1, 18, ccw=True) + " " + \
              arc_band(12, 9.2, 3.4, 1.7, 190, 350) + " " + rect(10.9, 11.4, 2.2, 3.4) + " " + rect(10.9, 16.4, 2.2, 2.2)
LIB['plus'] = rect(10.5, 3, 3, 18) + " " + rect(3, 10.5, 18, 3)
LIB['plus_circle'] = ring(12, 12, 10.5, 18) + " " + ring(12, 12, 8.1, 18, ccw=True) + " " + \
                     rect(10.9, 6.5, 2.2, 11) + " " + rect(6.5, 10.9, 11, 2.2)
LIB['check'] = "M9.3 17.6 L3.4 11.7 l2.1 -2.1 L9.3 13.4 L18.5 4.2 l2.1 2.1 Z"
LIB['check_circle'] = ring(12, 12, 10.5, 18) + " " + ring(12, 12, 8.1, 18, ccw=True) + " " + \
                      "M7.2 12.2 l2 -2 l2.2 2.2 l5.2 -5.2 l2 2 l-7.2 7.2 Z"
LIB['warning'] = "M12 1.8 L23 21.5 H1 Z " + "M10.9 8.6 h2.2 v7 h-2.2 Z M10.9 17.4 h2.2 v2.2 h-2.2 Z"
LIB['arrow_down'] = "M4 8 h16 l-8 10 Z"
LIB['arrow_up'] = "M4 16 h16 l-8 -10 Z"
LIB['arrow_right'] = "M8 4 l8 8 l-8 8 Z"
LIB['arrow_left'] = "M16 4 l-8 8 l8 8 Z"
LIB['list'] = ("M3 5 h3 v3 H3 Z M9 5.6 h12 v1.8 H9 Z M3 10.5 h3 v3 H3 Z M9 11.1 h12 v1.8 H9 Z "
               "M3 16 h3 v3 H3 Z M9 16.6 h12 v1.8 H9 Z")
LIB['menu'] = rect(3, 5, 18, 2) + " " + rect(3, 11, 18, 2) + " " + rect(3, 17, 18, 2)
LIB['dots_h'] = " ".join(rect(x, 10, 3, 3) for x in [3, 10.5, 18])
LIB['dots_v'] = " ".join(rect(10, y, 3, 3) for y in [3, 10.5, 18])
LIB['search'] = ring(10.3, 10.3, 7.2, 18) + " " + ring(10.3, 10.3, 5.1, 18, ccw=True) + " " + \
                "M15.6 15.6 L21.5 21.5 L19.9 23.1 L14 17.2 Z"
LIB['refresh'] = ("M12 3 a9 9 0 1 1 -8.5 6 h2.7 A6.4 6.4 0 1 0 12 5.6 c1.8 0 3.4 .7 4.6 1.9 L13 11 h8 V3 l-2.7 2.7 A8.9 8.9 0 0 0 12 3 Z")
LIB['undo'] = ("M9 7 L3.5 12 L9 17 v-3.2 c4.8 0 7.6 1.6 9.5 5 -0.9 -6 -3.7 -9 -9.5 -9 Z")
LIB['upload'] = "M10.5 3 h3 v11 h3.2 L12 19 L7.3 14 h3.2 Z M3 20 h18 v2 H3 Z"
LIB['download_tray'] = LIB['download']
LIB['close_x'] = LIB['close']

# ---------------------------------------------------------------- files / packages
# Clean isometric cube (three visible faces as outlines)
LIB['cube'] = ("M12 1.8 L22.2 7.4 v11.2 L12 24 L1.8 18.6 V7.4 Z "     # silhouette
               "M12 6.6 L18.3 10 v6.6 L12 20 L5.7 16.6 V10 Z "          # inner
               "M12 6.6 L12 13.2 L18.3 10 Z M12 13.2 L12 20 L18.3 16.6 Z M5.7 10 L12 13.2 L12 20 Z")
# Simpler filled isometric box (single tone, clearly a box)
LIB['box'] = ("M12 2 L22 7.5 L12 13 L2 7.5 Z "        # top
              "M2 7.5 L12 13 V22 L2 16.5 Z "          # left
              "M22 7.5 L12 13 V22 L22 16.5 Z")        # right
LIB['folder'] = ("M2 6 V5 a1 1 0 0 1 1-1 h6 l2 2 h9 a1 1 0 0 1 1 1 v1 Z "
                 "M2 8 h19 v10 a1 1 0 0 1 -1 1 H3 a1 1 0 0 1 -1 -1 Z")
LIB['folder_open'] = ("M2 6 V5 a1 1 0 0 1 1-1 h6 l2 2 h9 a1 1 0 0 1 1 1 v1 Z "
                      "M4.2 9.5 H22 l-3 10.5 a1 1 0 0 1 -1 .8 H3 a1 1 0 0 1 -1 -1.2 Z")
LIB['folder_add'] = LIB['folder'] + " " + rect(10.9, 11, 2.2, 8) + " " + rect(8, 13.9, 8, 2.2)
LIB['copy'] = (rect(8, 2, 13, 15) + " " + poly([(8,3),(8,2),(21,2),(21,17),(20,17),(20,3)],False))
# two overlapping sheets
LIB['copy'] = ("M8 3 h11 a1 1 0 0 1 1 1 v11 H8 Z "
               "M5 7 h1 v12 h11 v1 H5 Z")
LIB['pencil'] = "M4 16.5 L15.4 5.1 l3.5 3.5 L7.5 20 H4 Z M14 6.5 l3.5 3.5"
LIB['trash'] = (rect(5, 6, 14, 15) + " " + rect(3, 3, 18, 2.4) + " " + rect(9, 1.4, 6, 1.8) + " " +
                rect(9, 9, 1.6, 9) + " " + rect(13.4, 9, 1.6, 9))
LIB['chat'] = "M3 4 h18 v12 H9 l-5 4 v-4 H3 Z"
LIB['image'] = (poly([(2,4),(22,4),(22,20),(2,20)]) + " " +
                poly([(2,4),(22,4),(22,20),(2,20)]) )  # placeholder, replaced below
LIB['image'] = ("M2 4 h20 v16 H2 Z " + rect(4, 6, 16, 12) + " " +
                "M4 15 l4 -4 l3 3 l4 -5 l5 6 Z " + ring(8, 8.5, 1.4, 10))
LIB['puzzle'] = ("M4 4 h5 v-1.4 a2 2 0 0 1 4 0 V4 h5 v5 h1.4 a2 2 0 0 1 0 4 H18 v5 h-5 v1.4 "
                 "a2 2 0 0 1 -4 0 V18 H4 v-5 H2.6 a2 2 0 0 1 0 -4 H4 Z")
LIB['code'] = "M8.6 6 L2.5 12 l6.1 6 v-3.4 L4.9 12 l3.7 -2.6 Z M15.4 6 l6.1 6 l-6.1 6 v-3.4 L19.1 12 l-3.7 -2.6 Z"
LIB['window'] = (poly([(2,4),(22,4),(22,20),(2,20)]) + " " +
                 poly([(4,7),(20,7),(20,18),(4,18)], False) + " " + rect(2, 4, 20, 3))

# ---------------------------------------------------------------- people / accounts / status
LIB['person'] = ring(12, 7.6, 4.4, 16) + " " + \
                "M4 21 c0 -4.6 3.6 -7.5 8 -7.5 s8 2.9 8 7.5 Z"
LIB['shield'] = "M12 1.8 L21 5 v6.6 c0 5.6 -3.8 8.7 -9 11 c-5.2 -2.3 -9 -5.4 -9 -11 V5 Z"
LIB['shield_check'] = LIB['shield'] + " " + "M8 11.8 l2.2 -2.2 l2.2 2.2 l4.4 -4.4 l1.8 1.8 l-6.2 6.2 Z"
LIB['star'] = "M12 1.5 L14.9 8.7 L22.6 9.4 L16.8 14.6 L18.5 22.2 L12 18.2 L5.5 22.2 L7.2 14.6 L1.4 9.4 L9.1 8.7 Z"
LIB['home'] = "M12 2.5 L1.5 12 h3.2 v9.5 h5.6 v-6 h3.4 v6 h5.6 V12 h3.2 Z"
LIB['flag'] = rect(3.4, 2.5, 2.2, 19) + " " + "M6 3.5 c3 -2 6 2 9 0 c2 -1.3 4 -1 6 0 v9 c-2 -1 -4 -1.3 -6 0 c-3 2 -6 -2 -9 0 Z"
LIB['clock'] = ring(12, 12, 10.5, 18) + " " + ring(12, 12, 8.4, 18, ccw=True) + " " + \
               rect(11, 5.6, 2, 6.8) + " " + "M11 12 h5.4 l1 -2 Z"
LIB['sun'] = ring(12, 12, 4.6, 16) + " " + " ".join(
    rect(10.6, y, 2.8, 3) for y in []) + " " + " ".join(
    poly([(12 + 7.4*math.cos(math.radians(a)), 12 + 7.4*math.sin(math.radians(a))),
          (12 + 10.4*math.cos(math.radians(a-7)), 12 + 10.4*math.sin(math.radians(a-7))),
          (12 + 10.4*math.cos(math.radians(a+7)), 12 + 10.4*math.sin(math.radians(a+7)))])
    for a in range(0, 360, 45))
LIB['coffee'] = ("M3 6 h13 v5 a5 5 0 0 1 -5 5 H8 a5 5 0 0 1 -5 -5 Z "
                 "M16 7 h2.5 a2.5 2.5 0 0 1 0 5 H16 Z "
                 "M2 19 h16 v1.8 H2 Z " + rect(6, 2.6, 1.4, 2.6) + " " + rect(9, 2.2, 1.4, 3) + " " + rect(12, 2.6, 1.4, 2.6))
LIB['gamepad'] = ("M6.5 7 h11 a5 5 0 0 1 4.9 4 l.6 5.5 a2.6 2.6 0 0 1 -4.6 2 l-2 -2.4 H7.6 l-2 2.4 "
                  "a2.6 2.6 0 0 1 -4.6 -2 L1.6 11 A5 5 0 0 1 6.5 7 Z " +
                  rect(5.6, 10.4, 1.8, 4.6) + " " + rect(4.2, 11.8, 4.6, 1.8) + " " +
                  rect(16.4, 10.6, 1.7, 1.7) + " " + rect(18.6, 12.8, 1.7, 1.7))
LIB['chart'] = rect(3, 14, 3.6, 7) + " " + rect(10.2, 9, 3.6, 12) + " " + rect(17.4, 3, 3.6, 18)
LIB['feedback'] = "M12 2 L22 12 L12 22 L2 12 Z " + "M12 6.4 L17.6 12 L12 17.6 L6.4 12 Z"
LIB['globe'] = ring(12, 12, 9.5, 20) + " " + \
               "M2.5 12 h19 Z M12 2.5 c-4 4 -4 15 0 19 Z M12 2.5 c4 4 4 15 0 19 Z M3.6 7.5 h16.8 Z M3.6 16.5 h16.8 Z"
LIB['eye'] = ("M1.5 12 C4 7.4 7.7 5 12 5 s8 2.4 10.5 7 C20 16.6 16.3 19 12 19 s-8 -2.4 -10.5 -7 Z " +
              ring(12, 12, 3.4, 14))
LIB['eye_off'] = ("M1.5 12 C4 7.4 7.7 5 12 5 c1.4 0 2.7 .3 3.9 .8 l-2 2 A5 5 0 0 0 7.8 13.9 l-2 2 "
                  "C4 14.4 2.8 13.2 1.5 12 Z M22.5 12 C20 16.6 16.3 19 12 19 c-1.4 0 -2.7 -.3 -3.9 -.8 l2 -2 "
                  "A5 5 0 0 0 16.2 10.1 l2 -2 c1.8 1.5 3 2.7 4.3 3.9 Z " +
                  "M3.5 3.5 L20.5 20.5 L18.9 22.1 L1.9 5.1 Z")
LIB['external'] = (poly([(4,4),(20,4),(20,20),(4,20)]) + " " +
                   poly([(6,6),(6,18),(18,18),(18,14),(16,14),(16,16),(8,16),(8,6)], False) + " " +
                   "M14 3 h7 v7 h-2 V6.4 l-7.3 7.3 -1.4 -1.4 L17.6 5 H14 Z")
LIB['tshirt'] = ("M9 3 L6.5 4 L2.5 8.5 L6 12 L8 10 V21 h8 V10 l2 2 l3.5 -3.5 L17.5 4 L15 3 "
                 "c-1 1.6 -2 2.4 -3 2.4 S10 4.6 9 3 Z")
LIB['dresser'] = (poly([(3,3),(21,3),(21,21),(3,21)]) + " " +
                  rect(4.6, 4.6, 14.8, 4.4) + " " + rect(4.6, 9.8, 14.8, 4.4) + " " + rect(4.6, 15, 14.8, 4.4))
LIB['power'] = (arc_band(12, 12, 9.6, 7.4, 130, 410, 20) + " " + rect(10.9, 2.5, 2.2, 10))
LIB['host'] = LIB['wifi']
LIB['rocket'] = LIB['play']

# PCL2 MyLoading pickaxe (verbatim, normalised to 24x24 by uniform scale)
LIB['pickaxe'] = ("M22.99 20.66 L9.8 7.47 C12.12 5.27 14.53 4.88 16.96 3.25 C19.97 1.37 20.98 1.39 17.93 1.37 "
                  "C14.58 1.43 11.25 2.48 8.43 4.54 C7.39 3.9 6.01 4.03 5.12 4.93 C4.22 5.83 4.09 7.2 4.72 8.24 "
                  "C2.67 11.07 1.61 14.4 1.55 17.74 C1.51 21.27 1.55 20.16 3.44 16.78 C5.07 14.35 5.46 11.93 7.66 9.61 "
                  "L20.85 22.8 c0.01 0.2 0.04 0.2 0.05 0.19 l2.09 -1.9 c0.01 0.17 0.01 0.15 0 0.13 Z")

# ---------------------------------------------------------------- enum -> semantic
M = {}
def put(names, key):
    for n in names.split():
        M[n] = key

put('NONE', 'play')  # placeholder, overwritten below
M['NONE'] = ''

put('ROCKET_LAUNCH', 'play')
put('DOWNLOAD', 'download')
put('LINK', 'wifi')
put('HOST', 'wifi')
put('SETTINGS SETTINGS_FILL TUNE', 'gear_hex')
put('MORE_HORIZ MORE_GRID', 'grid')
put('MINIMIZE_CENTER', 'minimize')
put('CLOSE CANCEL', 'close')
put('ARROW_BACK', 'back')
put('ARROW_FORWARD OUTPUT', 'arrow_right')
put('ARROW_DROP_DOWN KEYBOARD_ARROW_DOWN UNFOLD_MORE', 'arrow_down')
put('ARROW_DROP_UP KEYBOARD_ARROW_UP', 'arrow_up')
put('OPEN_IN_NEW', 'external')
put('REFRESH REDO UPDATE RESTORE', 'refresh')
put('UNDO', 'undo')
put('HELP HELP_FILL', 'help')
put('INFO INFO_FILL ABOUT ALPHA_CIRCLE BETA_CIRCLE', 'info')
put('ERROR WARNING FRAME_BUG', 'warning')
put('CHECK RELEASE_CIRCLE', 'check')
put('CHECK_CIRCLE', 'check_circle')
put('VISIBILITY', 'eye')
put('VISIBILITY_OFF', 'eye_off')
put('SEARCH', 'search')
put('ADD', 'plus')
put('ADD_CIRCLE', 'plus_circle')
put('ARCHIVE ARCHIVE_FILL PACKAGE STADIA_CONTROLLER STADIA_CONTROLLER_FILL CHECKROOM PACKAGE2 PACKAGE2_FILL DRESSER', 'box')
put('FOLDER FOLDER_OPEN FILE_OPEN', 'folder')
put('CREATE_NEW_FOLDER', 'folder_add')
put('FOLDER_COPY CONTENT_COPY CONTENT_PASTE', 'copy')
put('CONTENT_CUT EDIT', 'pencil')
put('DELETE DELETE_FOREVER', 'trash')
put('DEPLOYED_CODE DEPLOYED_CODE_FILL EXTENSION EXTENSION_FILL', 'puzzle')
put('SCHEMA SCHEMA_FILL SCREENSHOT_MONITOR SCRIPT', 'code')
put('LANDSCAPE TEXTURE', 'image')
put('CHAT FEEDBACK FEEDBACK_FILL', 'feedback')
put('FEEDBACK FEEDBACK_FILL', 'feedback')
put('PUBLIC EXPLORE GLOBE_BOOK', 'globe')
put('HOME LOCATION_CITY', 'home')
put('FOR_YOU', 'star')
put('KEEP TRIP', 'flag')
put('FORT MICROSOFT', 'shield')
put('PERSON', 'person')
put('GAMEPAD', 'gamepad')
put('GRAPH2', 'chart')
put('FORMAT_LIST_BULLETED LIST LISTS SELECT_ALL', 'list')
put('MENU', 'menu')
put('MORE_VERT', 'dots_v')
put('DOTS', 'dots_h')
put('WB_SUNNY WB_SUNNY_FILL', 'sun')
put('LOCAL_CAFE LOCAL_CAFE_FILL', 'coffee')
put('UPLOAD', 'upload')
put('CLOCK', 'clock')
put('PICKAXE', 'pickaxe')
put('STYLE STYLE_FILL', 'tshirt')

# every enum currently in SVG.java must be covered
src = open('/workspace/hpcl/HMCL/src/main/java/org/jackhuang/hmcl/ui/SVG.java', encoding='utf-8').read()
names = [n for n, _ in re.findall(r'^\s{4}([A-Z0-9_]+)\("(.*?)"\)', src, re.M)]
missing = [n for n in names if n not in M]
if missing:
    print('WARN unmapped -> info:', missing, file=sys.stderr)
    for n in missing:
        M[n] = 'info'

# ---------------------------------------------------------------- overrides
# A handful of shapes are cleaner as the *original* PCL2-normalised glyphs
# (arcs / circles already verified correct in the old atlas); the rest of the
# library is hand-authored geometry.  Pull them from the current SVG.java.
old = dict(re.findall(r'^\s{4}([A-Z0-9_]+)\("(.*?)"\)', src, re.M))
LIB['refresh'] = old['REFRESH']        # dual circular arrows
LIB['undo'] = old['UNDO']              # undo arrow
LIB['help'] = old['HELP']              # ? in circle
LIB['box'] = old['CHECKROOM']          # clean wireframe cube
LIB['cube'] = old['CHECKROOM']
LIB['image'] = old['LANDSCAPE']        # picture with mountain
LIB['globe'] = old['EXPLORE']          # graticule globe
LIB['copy'] = old['CONTENT_COPY']      # two sheets
# eye: almond outline (outer cw, inner ccw = hole) + solid pupil
_al_outer = [(1.5,12),(5,8.2),(9,6),(15,6),(19,8.2),(22.5,12),(19,15.8),(15,18),(9,18),(5,15.8)]
_al_inner = [(4.6,12),(7.2,9.5),(9.6,8.3),(14.4,8.3),(16.8,9.5),(19.4,12),(16.8,14.5),(14.4,15.7),(9.6,15.7),(7.2,14.5)]
_eye_ring = poly(_al_outer) + " " + poly(_al_inner[::-1])
LIB['eye'] = _eye_ring + " " + ring(12, 12, 3.1, 14)
LIB['eye_off'] = _eye_ring + " " + "M2.4 4 L4 2.4 L21.6 20 L20 21.6 Z"
# folder_add: folder with a knocked-out '+' (reversed rectangles)
LIB['folder_add'] = ("M2 6 V5 a1 1 0 0 1 1-1 h6 l2 2 h9 a1 1 0 0 1 1 1 v1 Z " +
                     "M2 8 h19 v10 a1 1 0 0 1 -1 1 H3 a1 1 0 0 1 -1 -1 Z " +
                     poly([(10.9,10.5),(13.1,10.5),(13.1,16.5),(10.9,16.5)][::-1]) + " " +
                     poly([(8,12.9),(16,12.9),(16,15.1),(8,15.1)][::-1]))
# external: clean "open in new" = outlined box (4 bars) + NE arrow
LIB['external'] = (rect(2.5, 2.5, 13, 2.2) + " " + rect(2.5, 2.5, 2.2, 15) + " " +
                   rect(2.5, 15.3, 8, 2.2) + " " +
                   "M13 2.5 h8.5 v8.5 h-2.4 V6.3 L10.6 14.8 8.9 13.1 l8.5 -8.5 h-4.4 Z")
# dresser: three drawers with visible gaps (unused by enums, keep clean anyway)
LIB['dresser'] = rect(3, 3, 18, 5.3) + " " + rect(3, 9.35, 18, 5.3) + " " + rect(3, 15.7, 18, 5.3)
# power: C-shaped ring opening at top + vertical bar
LIB['power'] = arc_band(12, 12, 9.6, 7.2, 300, 600, 22) + " " + rect(10.9, 2.5, 2.2, 10)
# window: outlined window with a title bar (unused, keep sane)
LIB['window'] = (poly([(2,4),(22,4),(22,20),(2,20)]) + " " +
                 poly([(4,8),(20,8),(20,18),(4,18)][::-1]) + " " + rect(2, 4, 20, 3))
# shield_check: shield + knocked-out tick is not needed (MICROSOFT -> shield)
LIB['shield_check'] = LIB['shield']

json.dump({'lib': LIB, 'map': M}, open('/tmp/hpcl_icons_v7.json', 'w'), ensure_ascii=False, indent=1)
print('semantic glyphs:', len(LIB), 'enums:', len(names), 'mapped:', len(M))

# render atlas
import cairosvg, io
from PIL import Image, ImageDraw, ImageFont
keys = list(LIB)
cell, cols = 168, 7
rows = math.ceil(len(keys) / cols)
out = Image.new('RGB', (cell * cols, cell * rows), 'white')
dr = ImageDraw.Draw(out)
try:
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 11)
except Exception:
    font = ImageFont.load_default()
for i, k in enumerate(keys):
    p = LIB[k]
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="110" height="110" viewBox="-1 -1 26 26"><path d="{p}" fill="#1370F3"/></svg>'
    try:
        png = cairosvg.svg2png(bytestring=svg.encode(), output_width=110, output_height=110)
        im = Image.open(io.BytesIO(png)).convert('RGBA')
        bg = Image.new('RGBA', im.size, 'white'); bg.alpha_composite(im)
        x, y = i % cols * cell + 28, i // cols * cell + 4
        out.paste(bg.convert('RGB'), (x, y))
    except Exception as e:
        dr.text((i % cols * cell + 5, i // cols * cell + 50), 'ERR', fill='red', font=font)
    gx, gy = i % cols * cell, i // cols * cell
    dr.text((gx + 4, gy + 120), f'[{i}] {k}', fill='#33445D', font=font)
    dr.rectangle([gx, gy, gx + cell - 1, gy + cell - 1], outline='#D5E6FD')
out.save('/tmp/icon_lib_v7_atlas.png')
print('atlas', out.size)
