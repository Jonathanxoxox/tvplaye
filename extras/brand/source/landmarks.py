"""The landmarks the loading indicator shows (LandmarkLoader), drawn as path data.

    python3 extras/brand/source/landmarks.py app/src/main/res/drawable [svg-preview-dir]

writes one Android vector per landmark (landmark_<name>.xml) and, when asked, an SVG of each for a quick look
in the browser. Change a picture here and run it again rather than editing the vectors by hand.

Every picture is 120 x 80 with the ground at y = 75, so tall and wide ones stand on the same line.
A layer is (path data, alpha, even-odd?[, colour]) or a stroke ("stroke", path data, alpha, width). Layers are
black unless they name a colour; a picture with colours of its own is shown as it is, not tinted. Path data may be a
list of pieces, each its own <path>: solid parts that overlap must not share an even-odd path (the overlap
would become a hole), and only opaque layers may be split, as translucent pieces would add up where they overlap.
"""
import math
import os
import sys

W, H = 120, 80
GROUND = 75


def f(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def rect(x0, y0, x1, y1):
    return f"M{f(x0)},{f(y0)}H{f(x1)}V{f(y1)}H{f(x0)}Z"


def poly(*pts):
    p = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)
    return p + "Z"


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}A{f(r)},{f(r)} 0 1,0 {f(cx + r)},{f(cy)}A{f(r)},{f(r)} 0 1,0 {f(cx - r)},{f(cy)}Z"


def ellipse(cx, cy, rx, ry):
    return f"M{f(cx - rx)},{f(cy)}A{f(rx)},{f(ry)} 0 1,0 {f(cx + rx)},{f(cy)}A{f(rx)},{f(ry)} 0 1,0 {f(cx - rx)},{f(cy)}Z"


def arch(x0, y0, x1, y1):
    """A window with a round top: x0..x1 wide, y0 (top of the arc) .. y1 (sill)."""
    r = (x1 - x0) / 2
    return f"M{f(x0)},{f(y1)}V{f(y0 + r)}A{f(r)},{f(r)} 0 0,1 {f(x1)},{f(y0 + r)}V{f(y1)}Z"


def waves(y, x0=4, x1=116, step=8, amp=1.3):
    d = f"M{f(x0)},{f(y)}"
    x = x0
    up = True
    while x + step / 2 <= x1 + 0.01:
        d += f"Q{f(x + step / 4)},{f(y - amp if up else y + amp)} {f(x + step / 2)},{f(y)}"
        x += step / 2
        up = not up
    return d


def ground():
    return ("stroke", f"M6,{f(GROUND + 0.9)}H114", 0.32, 1.4)


def water(y=GROUND + 1.2):
    return [("stroke", waves(y, amp=2), 0.38, 1.3), ("stroke", waves(y + 2.8, 16, 104, 8, 1.2), 0.2, 1.0)]


# ---------------------------------------------------------------- Elbphilharmonie
def elbphilharmonie():
    base = rect(12, 54, 108, GROUND)
    for y in (57.5, 63, 68.5):
        x = 15.5
        while x < 105:
            base += rect(x, y, x + 3, y + 2.6)
            x += 6.5
    glass = (
        "M12,51.5V13.5"
        "C17,20 25,27.5 35,27.5"
        "C43,27.5 47,20.5 54,20"
        "C61,20.5 64,29 72,29"
        "C80,29 82,23.5 88.5,23.5"
        "C95,24 98,31 104,31"
        "C105.5,31 107,30.5 108,29.5"
        "V51.5Z"
    )
    # Floors, as the bands of the curved panes: dashed so they read as glass, not stripes.
    for y in (46, 40.5, 35):
        x = 14
        while x < 106:
            glass += rect(x, y, min(x + 7, 106), y + 1)
            x += 9
    pillars = "".join(rect(x, 51.5, x + 1.6, 54) for x in (16, 34, 52, 70, 88, 104.4))
    return [(glass, 0.62, True), (base, 1.0, True), (pillars, 1.0, False)] + water()


# ---------------------------------------------------------------- Bremer Stadtmusikanten
def stadtmusikanten():
    plinth = rect(38, 71, 84, GROUND)
    # Donkey, facing right: body, neck down to a long head, long ears, four straight legs, a tail.
    donkey = (
        "M43,55C43,50.5 46,49 50,49L68,49"
        "C70.5,49 72.5,48 74.5,46L77.4,43.4"  # neck rising to the head
        "C79,42 81.2,41.8 82.8,42.8L91.4,48.2"  # poll, forehead, down to the muzzle
        "C93.8,49.6 93.9,53 91.4,54.2C89.6,55 87.4,54.9 85.4,54.2"  # muzzle, chin
        "L81.6,53.2C80,53.6 78.8,55 77.8,57"  # jaw, throat
        "L76,59.8C75,61 73.6,61.4 72,61.4"  # chest
        "L71.5,71H68.6L68,62.4H66.6L65.6,71H62.8L62.6,61.6"  # front legs
        "H51.5L51,71H48.2L47.6,61.2"  # belly, hind leg
        "C47,61 46.4,61 46,61L45.4,71H42.6L42.8,60"  # back leg
        "C42.2,58.5 42.4,57 43,55Z"
    )
    # The long, leaf-shaped ears are what make it a donkey and not a deer.
    ears = (
        "M77.8,43.8C75.4,40.4 74.8,35.6 76,31C78.6,33.6 80.4,37.8 81,42.2Z"
        "M80.4,42.4C80,38 80.8,33.2 83,29.8C84.4,33.6 84.4,38.2 83,42.6Z"
    )
    mane = "M70.4,49C72.4,47.8 74.4,45.8 76.4,43.6L77.2,44.6C75.4,46.8 73.4,48.6 71.2,50Z"
    tail = (
        "M43.4,52.2C41,53.5 39.8,56 39.8,59.6L41.2,59.8C41.4,56.8 42.4,55.2 44,54.4Z"
        "M39.2,59.4C40,58.8 41.2,59 41.6,59.8C41.8,61.4 41.2,63 40.4,63.8C39.6,63 39,61.2 39.2,59.4Z"
    )
    # Dog, standing on the donkey's back, head up.
    dog = (
        "M46,48.8L46.8,43.6C46.2,41.5 46.6,39.5 48.6,38.6L62,38"
        "C63.4,37.6 64.2,36.6 64.6,35L65.4,31.4"  # neck
        "C65.8,29.8 67.4,29 69,29.4L71.4,30.2L74.4,31.4"  # head top to snout
        "C75.4,31.8 75.2,33.4 74,33.5L71.4,34"  # snout underside
        "L70,36.6C69,38.6 67.6,40.2 66,41.4L65.6,48.8"  # chest, front leg
        "H63.2L62.8,44.2H60.8L60.4,48.8H58"  # second front leg
        "L58,44.4H51.6L51.2,48.8H48.8L49,44.2L48.4,44.4L48.2,48.8Z"
    )
    dog_ear = "M68,29.6L66.4,26.2C66.2,25.6 66.8,25.3 67.2,25.7L70,29.6Z"
    dog_tail = "M47.6,39.6C45.6,38.4 44.6,36.2 44.8,33.6L46.2,33.8C46.4,35.6 47.2,37 48.8,38Z"
    # Cat, arched, on the dog's back.
    cat = (
        "M50.2,38L50.6,33.6C49.4,31 50.2,27.4 53.6,26.6"
        "C56.8,25.8 59.4,27 61,28.4L62.2,26.4"  # back to nape
        "L61.8,22.4L63.6,24.4H65.6L67.2,22.2L67.2,26.2"  # ears
        "C67.4,28 66.6,29.4 65,29.8L63.2,30.4"  # face
        "L62.8,38H60.6L60.4,33.4H56.2"  # front leg, belly
        "L55.8,38H53.6L53.2,34.2L52.6,38Z"
    )
    cat_tail = "M50.4,30C47.6,29.6 46.2,27 46.8,23.6C47,22.6 48.4,22.8 48.2,23.8C47.8,26.4 48.8,28 51,28.4Z"
    # Rooster, crowing: feet on the cat, round body, sickle tail, head up with comb, open beak.
    rooster = (
        "M54.6,26.4L55.2,22.6C52.2,21.8 50.6,19.4 50.8,16.6"
        "C51,13.2 54,11.4 57,11.6L58.6,9.4"  # back up to the neck
        "C58.4,7.8 59.4,6.4 61,6.2"  # back of head
        "L62.8,6.2L66.6,4.4L64.2,7.4L67.4,8.2L63.6,8.6"  # open beak
        "L62.6,10.6C63.6,13 63.4,16.4 62,18.8"  # breast
        "C61.2,20.4 59.8,21.8 58,22.6L58.6,26.4H57.2L56.6,23L56,26.4Z"
    )
    comb = "M59.4,6.4L59.4,4.2L60.4,5L61,3.4L61.8,5.2L62.8,4.4L62.6,6.4Z"
    rooster_tail = (
        "M52,15.4C48.6,14.2 46,11 46.2,6.6C46.3,5.8 47.4,5.8 47.4,6.6"
        "C47.6,9.8 49.8,12.4 53,13.2Z"
        "M52.8,13.4C50.6,11.2 50,8 51.2,4.8C51.5,4.1 52.6,4.4 52.4,5.2"
        "C51.6,7.8 52.2,10.2 54,11.8Z"
    )
    return [
        (plinth, 0.5, False),
        (donkey + ears + mane + tail, 1.0, False),
        (dog + dog_ear + dog_tail, 0.62, False),
        (cat + cat_tail, 1.0, False),
        (rooster + comb + rooster_tail, 0.62, False),
        ground(),
    ]


# ---------------------------------------------------------------- Volksparkstadion
def volksparkstadion():
    cx, cy, rx, ry = 60, 40, 54, 16
    gcy, grx, gry = 62, 51, 13
    facade = (
        f"M{f(cx - rx)},{f(cy)}L{f(cx - grx)},{f(gcy)}"
        f"A{f(grx)},{f(gry)} 0 0,0 {f(cx + grx)},{f(gcy)}"
        f"L{f(cx + rx)},{f(cy)}A{f(rx)},{f(ry)} 0 0,1 {f(cx - rx)},{f(cy)}Z"
    )
    # The ribs of the facade, closer together towards the sides as the bowl turns away.
    for i in range(-8, 9):
        a = i / 9.0 * (math.pi / 2) * 0.92
        x = cx + grx * math.sin(a)
        top = cy + ry * math.sqrt(max(0.0, 1 - ((x - cx) / rx) ** 2)) + 1.6
        bot = gcy + gry * math.sqrt(max(0.0, 1 - ((x - cx) / grx) ** 2)) - 1.6
        w = 1.5 * math.cos(a) + 0.35
        facade += rect(x - w / 2, top, x + w / 2, bot)
    roof = ellipse(cx, cy, rx, ry) + ellipse(cx, cy, 42, 10.5)
    # Through the roof: the far stands, and the pitch with its halfway line.
    pitch_pts = [(42, 35.4), (78, 35.4), (88, 45.5), (32, 45.5)]
    stands = ellipse(cx, cy, 42, 10.5) + poly(*pitch_pts)
    pitch = poly(*pitch_pts) + rect(59.5, 35.4, 60.5, 45.5)
    return [(facade, 0.6, True), (stands, 0.38, True), (pitch, 0.16, True), (roof, 1.0, True), ground()]


# ---------------------------------------------------------------- Michel (St. Michaelis)
def michel():
    nave = (
        "M54,75V53L50.5,53L58,41.5H95L102.5,53H99V75Z"
        + "".join(arch(x, 56, x + 4.2, 70.5) for x in (60, 69, 78, 87))
    )
    tower = (
        rect(38, 34.5, 54, GROUND)
        + arch(43.6, 52, 48.4, 64)
        + arch(43.4, 67, 48.6, 74.2)
        + circle(46, 41.2, 3.8)
        + circle(46, 41.2, 2.7)
    )
    cornice1 = rect(36.2, 32.8, 55.8, 35)
    stage = rect(39.4, 24.8, 52.6, 32.8) + "".join(rect(x, 26.4, x + 1.5, 31.4) for x in (41.6, 45.25, 48.9))
    cornice2 = rect(38, 23, 54, 24.8)
    bell = "M38.8,23C40.2,20.4 42.6,19 43.4,15.6H48.6C49.4,19 51.8,20.4 53.2,23Z"
    lantern = rect(43.6, 10.4, 48.4, 15.8) + rect(45.3, 11.8, 46.7, 14.6)
    cap = "M43,10.6C44.2,9 45.2,7.8 46,5.2C46.8,7.8 47.8,9 49,10.6Z"
    spire = rect(45.6, 1.2, 46.4, 5.6)
    return [
        (nave, 0.58, True),
        ([tower, cornice1 + stage + bell + cap, cornice2 + lantern, spire, circle(46, 3.4, 1.05)], 1.0, True),
        ground(),
    ]


# ---------------------------------------------------------------- Köhlbrandbrücke
def koehlbrandbruecke():
    def deck_y(x):
        t = (x - 2) / 116
        return 64 - 48 * t + 48 * t * t

    deck = "M2,64Q60,40 118,64L118,66.6Q60,42.6 2,66.6Z"
    piers = "".join(
        rect(x - 0.9, deck_y(x) + 2.2, x + 0.9, GROUND)
        for x in (12, 24, 96, 108)
    )
    pylons = ""
    cables = ""
    for px in (40, 80):
        pylons += poly((px - 1.9, GROUND), (px - 1.1, 10), (px + 1.1, 10), (px + 1.9, GROUND))
        for k in range(5):
            top = 13 + k * 2.4
            for side in (-1, 1):
                x = px + side * (7 + k * 4.6)
                cables += f"M{f(px)},{f(top)}L{f(x)},{f(deck_y(x) + 0.3)}"
    return [
        ("stroke", cables, 0.72, 0.7),
        (pylons + piers, 1.0, False),
        (deck, 1.0, False),
    ] + water()


# ---------------------------------------------------------------- Leuchtturm Roter Sand
def roter_sand():
    beams = poly((56.4, 23.2), (6, 14), (6, 31)) + poly((63.6, 23.2), (114, 31), (114, 14))

    def half_width(y):  # the tower tapers from 6.5 at y 65 to 4.4 at y 31
        return 4.4 + (y - 31) / (65 - 31) * 2.1

    def band(y0, y1):
        return poly((60 - half_width(y1), y1), (60 - half_width(y0), y0), (60 + half_width(y0), y0), (60 + half_width(y1), y1))

    caisson = "M50.5,75L52.4,65H67.6L69.5,75Z" + rect(55.6, 69, 57.6, 72) + rect(62.4, 69, 64.4, 72)
    dark = band(53.5, 65) + band(31, 41.5)
    light = band(41.5, 53.5) + "".join(rect(x, 45.6, x + 1.6, 49.4) for x in (59.2,))
    erkers = (
        "M50.4,40V33.4H55V40Z M50,33.4L52.7,28.8L55.4,33.4Z"
        "M65,40V33.4H69.6V40Z M64.6,33.4L67.3,28.8L70,33.4Z"
    )
    gallery = rect(52.6, 29.2, 67.4, 31) + "".join(rect(x, 26.8, x + 0.8, 29.2) for x in (53, 56.2, 59.6, 63, 66.6)) + rect(53, 26, 67.4, 26.8)
    lantern_glass = rect(56.4, 19.6, 63.6, 26.4) + rect(59.5, 20.6, 60.5, 25.6)
    dome = "M55.6,19.6A4.4,4.4 0 0,1 64.4,19.6Z"
    spike = rect(59.55, 11.6, 60.45, 15.6)
    return [
        (beams, 0.14, False),
        ([caisson + dark + dome, erkers, gallery, spike, circle(60, 13.6, 1.1)], 1.0, True),
        (light + lantern_glass, 0.55, True),
    ] + water()


# ---------------------------------------------------------------- Holstentor
def holstentor():
    def windows(x0, x1, rows, n, w=2, h=3.2):
        d = ""
        for y in rows:
            for i in range(n):
                cx = x0 + (x1 - x0) * (i + 1) / (n + 1)
                d += arch(cx - w / 2, y, cx + w / 2, y + h)
        return d

    centre = (
        "M42,75V35L60,16.5L78,35V75H67.4V62.4A7.4,7.4 0 0,0 52.6,62.4V75Z"
        + windows(42, 78, (38.5, 45, 51.5), 4)
        + rect(57.5, 27, 62.5, 29.4)
    )
    towers = ""
    cones = ""
    for x0, x1 in ((17.5, 42.5), (77.5, 102.5)):
        cx = (x0 + x1) / 2
        towers += f"M{f(x0)},75V31.6H{f(x1)}V75Z"
        towers += windows(x0 + 2, x1 - 2, (36, 43, 50, 57, 64), 3)
        towers += rect(x0 + 1.5, 69, x1 - 1.5, 70.2)
        # The cone: a touch concave, as the real roofs are, with a short spike.
        cones += (
            f"M{f(x0 - 1)},32.2C{f(x0 + 6)},28 {f(cx - 2.5)},20 {f(cx - 0.6)},9.2"
            f"L{f(cx - 0.4)},5.2H{f(cx + 0.4)}L{f(cx + 0.6)},9.2"
            f"C{f(cx + 2.5)},20 {f(x1 - 6)},28 {f(x1 + 1)},32.2Z"
        )
    return [(centre, 0.66, True), ([towers, cones], 1.0, True), ground()]


# ---------------------------------------------------------------- Rickmer Rickmers
def rickmer_rickmers():
    hull = (
        "M10,56.6L14,58H96.5C101,57.6 105,56.2 109,53.4L111.6,52.4"
        "L109.4,57C106.6,62.6 102.4,66.8 96,68.6H23C18,67.6 14.4,64.6 12.6,60.6Z"
        + "".join(circle(x, 62.4, 0.85) for x in range(26, 96, 7))
    )
    bowsprit = "M106,53.8L118,47.6L118.4,48.4L107,55Z"
    masts = "".join(rect(x - 0.6, top, x + 0.6, 58.2) for x, top in ((34, 12), (60, 6), (84, 9)))
    sails = ""
    levels = [(52, 45.4, 11), (44.2, 38, 10.2), (36.8, 31, 8.6), (29.8, 25, 7), (23.8, 19.6, 5.4)]
    for x, top in ((60, 6), (84, 9)):
        shift = 0 if x == 60 else 2
        for i, (y1, y0, w) in enumerate(levels):
            y0s, y1s = y0 + shift, y1 + shift
            if y0s < top + 2:
                continue
            wt = w - 0.8
            sails += (
                f"M{f(x - wt)},{f(y0s)}H{f(x + wt)}"
                f"Q{f(x + w + 1)},{f((y0s + y1s) / 2)} {f(x + w)},{f(y1s)}"
                f"H{f(x - w)}Q{f(x - w - 1)},{f((y0s + y1s) / 2)} {f(x - wt)},{f(y0s)}Z"
            )
    jibs = poly((85, 13), (116.8, 48.2), (98, 52.4)) + poly((85, 22), (108, 52.6), (95.6, 54.6))
    spanker = poly((19, 19.6), (33.2, 24), (33.2, 54.6), (13.8, 54.6)) + poly((33.2, 12.6), (33.2, 22.4), (21.4, 18.2))
    flag = "M60.6,6L66.6,7.6L60.6,9.2Z"
    return [
        (sails + jibs + spanker, 0.55, False),
        ([hull, bowsprit + masts, flag], 1.0, True),
    ] + water(GROUND - 4.6)


# ---------------------------------------------------------------- HSV-Raute
def hsv_raute():
    """The crest, in its own colours: a blue diamond in a white band with a black edge."""
    def diamond(half):
        return poly((60, 40 - half), (60 + half, 40), (60, 40 + half), (60 - half, 40))

    edge, band = 2.2, 4.4  # measured across the band, so the diagonal shrinks by each times the root of 2
    outer = 34
    white = outer - edge * math.sqrt(2)
    blue = white - band * math.sqrt(2)
    return [
        (diamond(outer), 1.0, False, "#000000"),
        (diamond(white), 1.0, False, "#FFFFFF"),
        (diamond(blue), 1.0, False, "#0A5BA8"),
    ]


LANDMARKS = [
    ("elbphilharmonie", "Elbphilharmonie, Hamburg", elbphilharmonie),
    ("stadtmusikanten", "Bremer Stadtmusikanten", stadtmusikanten),
    ("volksparkstadion", "Volksparkstadion, Hamburg", volksparkstadion),
    ("hsv_raute", "HSV-Raute", hsv_raute),
    ("michel", "St. Michaelis (Michel), Hamburg", michel),
    ("koehlbrandbruecke", "Köhlbrandbrücke, Hamburg", koehlbrandbruecke),
    ("roter_sand", "Leuchtturm Roter Sand, Wesermündung", roter_sand),
    ("holstentor", "Holstentor, Lübeck", holstentor),
    ("rickmer_rickmers", "Rickmer Rickmers, Hamburger Hafen", rickmer_rickmers),
]


def svg(layers, color="#000"):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">']
    for layer in layers:
        if layer[0] == "stroke":
            _, d, a, w = layer
            out.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-opacity="{a}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>')
        else:
            d, a, eo = layer[:3]
            fill = layer[3] if len(layer) > 3 else color
            for piece in d if isinstance(d, list) else [d]:
                out.append(f'<path d="{piece}" fill="{fill}" fill-opacity="{a}"{" fill-rule=\"evenodd\"" if eo else ""}/>')
    out.append("</svg>")
    return "\n".join(out)


def own_colours(layers):
    return any(layer[0] != "stroke" and len(layer) > 3 for layer in layers)


def vector(name, title, layers):
    look = (
        "     In its own colours on 120 x 80; the loader shows it as it is."
        if own_colours(layers)
        else "     Black on 120 x 80 with the ground at y 75; the loader tints it the accent."
    )
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {title}: one of the landmarks the loading indicator shows (LandmarkLoader).",
        look,
        "     Generated by extras/brand/source/landmarks.py: change it there, not here. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="120dp"',
        '    android:height="80dp"',
        f'    android:viewportWidth="{W}"',
        f'    android:viewportHeight="{H}">',
    ]
    for layer in layers:
        if layer[0] == "stroke":
            _, d, a, w = layer
            out += [
                "    <path",
                f'        android:pathData="{d}"',
                '        android:strokeColor="#FF000000"',
                f'        android:strokeAlpha="{a}"',
                f'        android:strokeWidth="{w}"',
                '        android:strokeLineCap="round"',
                '        android:strokeLineJoin="round" />',
            ]
        else:
            d, a, eo = layer[:3]
            colour = "#FF" + (layer[3][1:] if len(layer) > 3 else "000000")
            assert a == 1.0 or not isinstance(d, list), "translucent pieces would add up where they overlap"
            for piece in d if isinstance(d, list) else [d]:
                attrs = [f'        android:pathData="{piece}"', f'        android:fillColor="{colour}"']
                if a != 1.0:
                    attrs.append(f'        android:fillAlpha="{a}"')
                if eo:
                    attrs.append('        android:fillType="evenOdd"')
                out += ["    <path"] + attrs[:-1] + [attrs[-1] + " />"]
    out.append("</vector>")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    res = sys.argv[1]
    preview = sys.argv[2] if len(sys.argv) > 2 else None
    for name, title, fn in LANDMARKS:
        layers = fn()
        with open(os.path.join(res, f"landmark_{name}.xml"), "w", encoding="utf-8") as fh:
            fh.write(vector(name, title, layers))
        if preview:
            with open(os.path.join(preview, f"{name}.svg"), "w", encoding="utf-8") as fh:
                fh.write(svg(layers))
