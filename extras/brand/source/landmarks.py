"""The landmarks the loading indicator shows (LandmarkLoader), drawn and animated as path data.

    python3 extras/brand/source/landmarks.py app/src/main/res/drawable [svg-preview-dir]

writes one animated vector per landmark (landmark_<name>.xml) and, when asked, a still SVG of each for a quick look
in the browser. Change a picture here and run it again rather than editing the vectors by hand.

Every picture is 120 x 80 with the ground at y = 75, so tall and wide ones stand on the same line. A picture is a
list of Fill, Stroke and Group, black unless a Fill names a colour; a picture with colours of its own is shown as it
is, not tinted. Fill path data may be a list of pieces, each its own <path>: solid parts that overlap must not share
an even-odd path (the overlap would become a hole), and only opaque fills may be split, as translucent pieces would
add up where they overlap.

The motion runs on the render thread (an AnimatedVectorDrawable does there), so it stays smooth while the app is
busy loading. Only what the render thread can animate is used: a group's rotation, scale and translation, a path's
alpha and trim, the whole picture's alpha. Every picture fades in, stands, fades out and leaves a moment of nothing
in one CYCLE; the loader changes picture in that moment (LandmarkLoader's Swap* constants match ROOT_FADE).

The render thread follows the box's animator duration scale, which nothing can override, so the durations are
written for the 0.5x most boxes run at: a picture's turn is 2.5 s there and 5 s at 1x.
"""
import math
import os
import sys
from dataclasses import dataclass, field

W, H = 120, 80
GROUND = 75
CYCLE = 5000


@dataclass
class Fill:
    d: object  # path data, or a list of pieces
    alpha: float = 1.0
    even_odd: bool = False
    colour: str = None
    name: str = None


@dataclass
class Stroke:
    d: str
    alpha: float
    width: float
    name: str = None
    trim_end: float = None


@dataclass
class Group:
    children: list
    name: str = None
    pivot: tuple = (0, 0)
    rotation: float = 0
    scale_x: float = 1
    translate_x: float = 0
    clip: str = None


@dataclass
class Anim:
    """One property of one named part. values is (from, to); keyframes is [(fraction, value, easing)]."""
    target: str
    prop: str
    duration: int
    values: tuple = None
    keyframes: list = field(default_factory=list)
    easing: str = "linear"
    reverse: bool = False


EASINGS = {
    "linear": "@android:anim/linear_interpolator",
    "in_out": "@android:anim/accelerate_decelerate_interpolator",
    "in": "@android:anim/accelerate_interpolator",
    "standard": "@android:interpolator/fast_out_slow_in",
}

# Fade in over the first tenth, stand, fade out to nothing by 0.94, then nothing until the turn ends.
ROOT_FADE = Anim("landmark", "alpha", CYCLE, keyframes=[
    (0, 0, "linear"), (0.1, 1, "standard"), (0.84, 1, "linear"), (0.94, 0, "standard"), (1, 0, "linear"),
])


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
    return Stroke(f"M6,{f(GROUND + 0.9)}H114", 0.32, 1.4)


def water(y=GROUND + 1.2):
    """Two rows of waves drifting apart, each one wavelength (8) per loop so the loop has no seam. Each row is
    drawn a wavelength longer than it shows and clipped, so its ends stay put while it drifts."""
    front = Group([Stroke(waves(y, 4, 124, amp=2), 0.38, 1.3)], name="waves_front")
    back = Group([Stroke(waves(y + 2.8, 8, 104, 8, 1.2), 0.2, 1.0)], name="waves_back")
    return [Group([front], clip=rect(4, y - 3, 116, y + 2)), Group([back], clip=rect(16, y + 1, 104, y + 5))]


WATER_MOTION = [
    Anim("waves_front", "translateX", 3200, (0, -8)),
    Anim("waves_back", "translateX", 4800, (0, 8)),
]


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
    return [Fill(glass, 0.62, True), Fill(base, 1.0, True), Fill(pillars)] + water(), WATER_MOTION


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
    picture = [
        Fill(plinth, 0.5),
        Fill(donkey + ears + mane + tail),
        Fill(dog + dog_ear + dog_tail, 0.62),
        Fill(cat + cat_tail),
        Group([Fill(rooster + comb + rooster_tail, 0.62)], name="rooster", pivot=(56.8, 26.4)),
        ground(),
    ]
    # Once a turn the rooster throws its head back and crows, then settles on its feet again.
    crow = Anim("rooster", "rotation", CYCLE, keyframes=[
        (0, 0, "linear"), (0.3, 0, "linear"), (0.38, -8, "standard"), (0.56, -8, "linear"), (0.66, 0, "standard"),
        (1, 0, "linear"),
    ])
    return picture, [crow]


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
    # La Ola: a bright stretch of crowd running round the stands, between the pitch and the roof.
    ola = Stroke(ellipse(cx, cy, 39, 8.6), 0.75, 2.4, name="ola", trim_end=0.14)
    picture = [Fill(facade, 0.6, True), Fill(stands, 0.38, True), Fill(pitch, 0.16, True), ola, Fill(roof, 1.0, True), ground()]
    return picture, [Anim("ola", "trimPathOffset", 4000, (0, 1))]


# ---------------------------------------------------------------- HSV-Raute
def hsv_raute():
    """The crest, in its own colours: a blue diamond in a white band with a black edge."""
    def diamond(half):
        return poly((60, 40 - half), (60 + half, 40), (60, 40 + half), (60 - half, 40))

    edge, band = 2.2, 4.4  # measured across the band, so the diagonal shrinks by each times the root of 2
    outer = 34
    white = outer - edge * math.sqrt(2)
    blue = white - band * math.sqrt(2)
    # Once a turn a soft shine passes over the blue, from off its left edge to off its right.
    shine = Group([Fill(poly((56, 6), (64, 6), (68, 74), (60, 74)), 0.25, colour="#FFFFFF")], name="shine", translate_x=-40)
    picture = [
        Fill(diamond(outer), colour="#000000"),
        Fill(diamond(white), colour="#FFFFFF"),
        Fill(diamond(blue), colour="#0A5BA8"),
        Group([shine], clip=diamond(blue)),
    ]
    sweep = Anim("shine", "translateX", CYCLE, keyframes=[
        (0, -40, "linear"), (0.22, -40, "linear"), (0.5, 40, "standard"), (1, 40, "linear"),
    ])
    return picture, [sweep]


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
    # The one that stands still: a church tower in the middle of the moving ones.
    return [
        Fill(nave, 0.58, True),
        Fill([tower, cornice1 + stage + bell + cap, cornice2 + lantern, spire, circle(46, 3.4, 1.05)], 1.0, True),
        ground(),
    ], []


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
    return [Stroke(cables, 0.72, 0.7), Fill(pylons + piers), Fill(deck)] + water(), WATER_MOTION


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
    picture = [
        Group([Fill(beams, 0.14)], name="beams", pivot=(60, 23.2)),
        Fill([caisson + dark + dome, erkers, gallery, spike, circle(60, 13.6, 1.1)], 1.0, True),
        Fill(light + lantern_glass, 0.55, True),
        Fill(circle(60, 23, 6.5), 0, name="flash"),
    ] + water()
    # The lamp turns: seen from the side its two beams shorten to nothing as they swing towards and away from
    # us and lengthen again, a cosine (accelerate-decelerate is exactly that), and the lantern flashes each time
    # a beam points straight at us.
    turn = Anim("beams", "scaleX", 2000, (1, -1), easing="in_out", reverse=True)
    flash = Anim("flash", "fillAlpha", 1000, (0, 0.45), easing="in", reverse=True)
    return picture, [turn, flash] + WATER_MOTION


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
    ship = Group(
        [Fill(sails + jibs + spanker, 0.55), Fill([hull, bowsprit + masts, flag], 1.0, True)],
        name="ship", pivot=(60, 68.6),
    )
    # She rocks on the swell, a degree and a half each way about her waterline.
    rock = Anim("ship", "rotation", 3200, (-1.4, 1.4), easing="in_out", reverse=True)
    return [ship] + water(GROUND - 4.6), [rock] + WATER_MOTION


LANDMARKS = [
    ("elbphilharmonie", "Elbphilharmonie, Hamburg", elbphilharmonie),
    ("stadtmusikanten", "Bremer Stadtmusikanten", stadtmusikanten),
    ("volksparkstadion", "Volksparkstadion, Hamburg", volksparkstadion),
    ("hsv_raute", "HSV-Raute", hsv_raute),
    ("michel", "St. Michaelis (Michel), Hamburg", michel),
    ("koehlbrandbruecke", "Köhlbrandbrücke, Hamburg", koehlbrandbruecke),
    ("roter_sand", "Leuchtturm Roter Sand, Wesermündung", roter_sand),
    ("rickmer_rickmers", "Rickmer Rickmers, Hamburger Hafen", rickmer_rickmers),
]


def own_colours(picture):
    for part in picture:
        if isinstance(part, Group) and own_colours(part.children):
            return True
        if isinstance(part, Fill) and part.colour:
            return True
    return False


# ---------------------------------------------------------------- SVG (a still, for a look in the browser)
def svg(picture, colour="#000", state=None, ids="c"):
    """state maps a part's name to properties to draw it with instead ({"landmark": {"alpha": 0.5}} for the
    whole picture), so a preview can show any moment of the motion. ids prefixes the clip ids, which must not
    repeat between SVGs on one page."""
    state = state or {}
    defs = []

    def emit(parts):
        out = []
        for part in parts:
            over = state.get(part.name, {}) if part.name else {}
            if isinstance(part, Group):
                px, py = part.pivot
                tx = over.get("translateX", part.translate_x)
                rot = over.get("rotation", part.rotation)
                sx = over.get("scaleX", part.scale_x)
                transform = f"translate({f(tx + px)},{f(py)}) rotate({f(rot)}) scale({sx:.4f},1) translate({f(-px)},{f(-py)})"
                clip = ""
                if part.clip:
                    defs.append(f'<clipPath id="{ids}{len(defs)}"><path d="{part.clip}"/></clipPath>')
                    clip = f' clip-path="url(#{ids}{len(defs) - 1})"'
                out.append(f'<g transform="{transform}"><g{clip}>{"".join(emit(part.children))}</g></g>')
            elif isinstance(part, Stroke):
                dash = ""
                if part.trim_end is not None:
                    offset = over.get("trimPathOffset", 0)
                    dash = f' pathLength="1" stroke-dasharray="{part.trim_end} {1 - part.trim_end}" stroke-dashoffset="{-offset:.4f}"'
                out.append(
                    f'<path d="{part.d}" fill="none" stroke="{colour}" stroke-opacity="{over.get("strokeAlpha", part.alpha)}" '
                    f'stroke-width="{part.width}" stroke-linecap="round" stroke-linejoin="round"{dash}/>'
                )
            else:
                alpha = over.get("fillAlpha", part.alpha)
                rule = ' fill-rule="evenodd"' if part.even_odd else ""
                for piece in part.d if isinstance(part.d, list) else [part.d]:
                    out.append(f'<path d="{piece}" fill="{part.colour or colour}" fill-opacity="{alpha}"{rule}/>')
        return out

    body = "".join(emit(picture))
    alpha = state.get("landmark", {}).get("alpha", 1)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}"><defs>{"".join(defs)}</defs>'
        f'<g opacity="{alpha:.3f}">{body}</g></svg>'
    )


# ---------------------------------------------------------------- Android animated vector
def vector_parts(parts, indent):
    pad = " " * indent
    out = []
    for part in parts:
        if isinstance(part, Group):
            attrs = []
            if part.name:
                attrs.append(f'android:name="{part.name}"')
            if part.pivot != (0, 0):
                attrs += [f'android:pivotX="{f(part.pivot[0])}"', f'android:pivotY="{f(part.pivot[1])}"']
            if part.translate_x:
                attrs.append(f'android:translateX="{f(part.translate_x)}"')
            out.append(pad + "<group" + "".join("\n" + pad + "    " + a for a in attrs) + ">")
            if part.clip:
                out.append(f'{pad}    <clip-path android:pathData="{part.clip}" />')
            out += vector_parts(part.children, indent + 4)
            out.append(pad + "</group>")
        elif isinstance(part, Stroke):
            attrs = [
                f'android:pathData="{part.d}"', 'android:strokeColor="#FF000000"', f'android:strokeAlpha="{part.alpha}"',
                f'android:strokeWidth="{part.width}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"',
            ]
            if part.name:
                attrs.insert(0, f'android:name="{part.name}"')
            if part.trim_end is not None:
                attrs.append(f'android:trimPathEnd="{part.trim_end}"')
            out.append(pad + "<path" + "".join("\n" + pad + "    " + a for a in attrs) + " />")
        else:
            pieces = part.d if isinstance(part.d, list) else [part.d]
            assert part.alpha == 1.0 or len(pieces) == 1, "translucent pieces would add up where they overlap"
            assert part.name is None or len(pieces) == 1, "a named part is one path"
            colour = "#FF" + (part.colour[1:] if part.colour else "000000")
            for piece in pieces:
                attrs = [f'android:pathData="{piece}"', f'android:fillColor="{colour}"']
                if part.name:
                    attrs.insert(0, f'android:name="{part.name}"')
                if part.alpha != 1.0:
                    attrs.append(f'android:fillAlpha="{part.alpha}"')
                if part.even_odd:
                    attrs.append('android:fillType="evenOdd"')
                out.append(pad + "<path" + "".join("\n" + pad + "    " + a for a in attrs) + " />")
    return out


def animator(anim, indent):
    pad = " " * indent
    common = [
        f'android:duration="{anim.duration}"',
        'android:repeatCount="infinite"',
        f'android:repeatMode="{"reverse" if anim.reverse else "restart"}"',
    ]
    if anim.values:
        attrs = [
            f'android:propertyName="{anim.prop}"', f'android:valueFrom="{f(anim.values[0])}"',
            f'android:valueTo="{f(anim.values[1])}"', 'android:valueType="floatType"',
        ] + common + [f'android:interpolator="{EASINGS[anim.easing]}"']
        return [pad + "<objectAnimator" + "".join("\n" + pad + "    " + a for a in attrs) + " />"]
    out = [pad + "<objectAnimator" + "".join("\n" + pad + "    " + a for a in common) + ">"]
    out.append(f'{pad}    <propertyValuesHolder android:propertyName="{anim.prop}" android:valueType="floatType">')
    for fraction, value, easing in anim.keyframes:
        out.append(
            f'{pad}        <keyframe android:fraction="{fraction}" android:value="{f(value)}"'
            + (f' android:interpolator="{EASINGS[easing]}"' if fraction else "") + " />"
        )
    out.append(f"{pad}    </propertyValuesHolder>")
    out.append(f"{pad}</objectAnimator>")
    return out


def animated_vector(title, picture, motion):
    look = (
        "     In its own colours on 120 x 80; the loader shows it as it is."
        if own_colours(picture)
        else "     Black on 120 x 80 with the ground at y 75; the loader tints it the accent."
    )
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {title}: one of the landmarks the loading indicator shows (LandmarkLoader).",
        look,
        "     Generated by extras/brand/source/landmarks.py: change it there, not here. -->",
        '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    xmlns:aapt="http://schemas.android.com/aapt">',
        '    <aapt:attr name="android:drawable">',
        "        <vector",
        '            android:name="landmark"',
        '            android:width="120dp"',
        '            android:height="80dp"',
        f'            android:viewportWidth="{W}"',
        f'            android:viewportHeight="{H}">',
    ]
    out += vector_parts(picture, 12)
    out += ["        </vector>", "    </aapt:attr>"]
    by_target = {}
    for anim in [ROOT_FADE] + motion:
        by_target.setdefault(anim.target, []).append(anim)
    for target, anims in by_target.items():
        out += [f'    <target android:name="{target}">', '        <aapt:attr name="android:animation">']
        if len(anims) == 1:
            out += animator(anims[0], 12)
        else:
            out.append("            <set>")
            for anim in anims:
                out += animator(anim, 16)
            out.append("            </set>")
        out += ["        </aapt:attr>", "    </target>"]
    out.append("</animated-vector>")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    res = sys.argv[1]
    preview = sys.argv[2] if len(sys.argv) > 2 else None
    for name, title, fn in LANDMARKS:
        picture, motion = fn()
        with open(os.path.join(res, f"landmark_{name}.xml"), "w", encoding="utf-8") as fh:
            fh.write(animated_vector(title, picture, motion))
        if preview:
            with open(os.path.join(preview, f"{name}.svg"), "w", encoding="utf-8") as fh:
                fh.write(svg(picture))
