// Draws the Mayo TV launcher banner, launcher icon, wordmark and splash name as layered SVG and renders
// them crisp into app/src/main/res (supersampled in Chromium, then scaled down with Lanczos).
// Usage (from the repo root): node extras/brand/source/render.js [outDir]
// Needs Playwright with Chromium and ImageMagick (convert). Fredoka (OFL) ships next to this file.
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '../../..');
const RES = path.join(ROOT, 'app/src/main/res');
const PREVIEW = process.argv[2];
const FONT = fs.readFileSync(path.join(__dirname, 'Fredoka.woff2')).toString('base64');

// Deterministic "random" so every run draws the same sky.
function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

const fontFace = `<style>@font-face{font-family:'Fredoka';src:url(data:font/woff2;base64,${FONT}) format('woff2');font-weight:300 700;}
text{font-family:'Fredoka';font-weight:600;}</style>`;

// ---- shared pieces ----

function stars({ w, h, count, seed, avoid = [], maxY, scale = 1 }) {
  const r = rng(seed);
  const out = [];
  let guard = 0;
  while (out.length < count && guard++ < count * 40) {
    const x = r() * w;
    const y = Math.pow(r(), 1.35) * maxY;
    if (avoid.some(([ax, ay, ad]) => Math.hypot(x - ax, y - ay) < ad)) continue;
    const big = r() < 0.09;
    const rad = (big ? 1.9 : 0.7 + r() * 1.1) * scale;
    const a = (0.35 + r() * 0.55) * (1 - (y / maxY) * 0.55);
    const tint = r() < 0.25 ? '#ffe9c4' : r() < 0.4 ? '#cfeff0' : '#ffffff';
    out.push(`<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${rad.toFixed(2)}" fill="${tint}" opacity="${a.toFixed(2)}"/>`);
    if (big) {
      const l = rad * 4.5;
      out.push(`<g opacity="${(a * 0.8).toFixed(2)}" fill="${tint}">
        <ellipse cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" rx="${l.toFixed(1)}" ry="${(rad * 0.28).toFixed(2)}"/>
        <ellipse cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" rx="${(rad * 0.28).toFixed(2)}" ry="${l.toFixed(1)}"/></g>`);
    }
  }
  return out.join('\n');
}

// A floodlight head: frame, 2 × 4 bulbs, hot core, halo and a soft anamorphic flare. Tilted by [tilt]°.
function lamp({ x, y, w, tilt = 0, id, flare = 1 }) {
  const h = w * 0.48;
  const br = w * 0.078;
  const gx = w * 0.205;
  const bulbs = [];
  for (let row = 0; row < 2; row++) {
    for (let col = 0; col < 4; col++) {
      const bx = x + (col - 1.5) * gx;
      const by = y + (row - 0.5) * h * 0.44;
      bulbs.push(`<circle cx="${bx}" cy="${by}" r="${br * 1.9}" fill="url(#${id}-bulbGlow)"/>`);
      bulbs.push(`<circle cx="${bx}" cy="${by}" r="${br}" fill="#fffef9"/>`);
    }
  }
  return `
  <defs>
    <radialGradient id="${id}-halo" cx="${x}" cy="${y}" r="${w * 2.1}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#fff8e6" stop-opacity="0.95"/>
      <stop offset="0.16" stop-color="#ffe4a8" stop-opacity="0.5"/>
      <stop offset="0.45" stop-color="#ffc874" stop-opacity="0.14"/>
      <stop offset="1" stop-color="#ffc874" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="${id}-frame" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#fff0c6"/>
      <stop offset="1" stop-color="#f5bf62"/>
    </linearGradient>
    <radialGradient id="${id}-bulbGlow">
      <stop offset="0" stop-color="#ffffff" stop-opacity="0.9"/>
      <stop offset="1" stop-color="#fff2cc" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="${id}-flare" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#ffe7b0" stop-opacity="0"/>
      <stop offset="0.5" stop-color="#fffaf0" stop-opacity="0.8"/>
      <stop offset="1" stop-color="#ffe7b0" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <circle cx="${x}" cy="${y}" r="${w * 2.1}" fill="url(#${id}-halo)" style="mix-blend-mode:screen"/>
  <g transform="rotate(${tilt} ${x} ${y})">
    <rect x="${x - w / 2}" y="${y - h / 2}" width="${w}" height="${h}" rx="${h * 0.24}" fill="#fff3d0" opacity="0.9" filter="url(#soft8)" style="mix-blend-mode:screen"/>
    <rect x="${x - w / 2 - w * 0.04}" y="${y - h / 2 + w * 0.05}" width="${w * 1.08}" height="${h}" rx="${h * 0.24}" fill="#05101a" opacity="0.6" filter="url(#soft2)"/>
    <rect x="${x - w / 2}" y="${y - h / 2}" width="${w}" height="${h}" rx="${h * 0.24}" fill="url(#${id}-frame)"/>
    <rect x="${x - w / 2 + 2}" y="${y - h / 2 + 2}" width="${w - 4}" height="${h * 0.38}" rx="${h * 0.2}" fill="#ffffff" opacity="0.28"/>
    ${bulbs.join('\n    ')}
  </g>
  <g style="mix-blend-mode:screen" opacity="${flare}">
    <ellipse cx="${x}" cy="${y}" rx="${w * 1.9}" ry="${w * 0.03}" fill="url(#${id}-flare)"/>
    <ellipse cx="${x}" cy="${y}" rx="${w * 1.3}" ry="${w * 0.09}" fill="url(#${id}-flare)" opacity="0.45" filter="url(#soft2)"/>
  </g>`;
}

// A light cone from (ax, ay) spreading to the two far points, fading with distance. Three layers: wide haze,
// body and a hot core, all screened onto what lies below.
function beam({ ax, ay, far1, far2, reach, id, strength = 1 }) {
  const mid = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
  const cone = (p1, p2, spread) => {
    const c1 = mid(p2, p1, spread), c2 = mid(p1, p2, spread);
    return `M ${ax - 6} ${ay} L ${ax + 6} ${ay} L ${c1[0].toFixed(1)} ${c1[1].toFixed(1)} L ${c2[0].toFixed(1)} ${c2[1].toFixed(1)} Z`;
  };
  return `
  <defs>
    <radialGradient id="${id}" cx="${ax}" cy="${ay}" r="${reach}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#fff6dc" stop-opacity="${0.62 * strength}"/>
      <stop offset="0.35" stop-color="#ffe4a8" stop-opacity="${0.24 * strength}"/>
      <stop offset="0.75" stop-color="#ffd58a" stop-opacity="${0.06 * strength}"/>
      <stop offset="1" stop-color="#ffd58a" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <g style="mix-blend-mode:screen">
    <path d="${cone(far1, far2, 1.18)}" fill="url(#${id})" opacity="0.55" filter="url(#soft24)"/>
    <path d="${cone(far1, far2, 1)}" fill="url(#${id})" opacity="0.85" filter="url(#soft8)"/>
    <path d="${cone(far1, far2, 0.32)}" fill="url(#${id})" opacity="0.6" filter="url(#soft8)"/>
  </g>`;
}

const filters = `
  <filter id="soft2" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2"/></filter>
  <filter id="soft4" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="4"/></filter>
  <filter id="soft8" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="8"/></filter>
  <filter id="soft24" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="24"/></filter>
  <filter id="grain" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" seed="7" result="n"/>
    <feColorMatrix in="n" type="saturate" values="0"/>
  </filter>`;

// The "Mayo TV" lettering in layers: wide bloom, tight glow, drop shadow, an amber extrusion for depth,
// a fine amber outline, the cream-to-gold face and a gloss band over its top half.
function lettering({ x, y, size, id, glow = 1, depth = 1, spacing = 0.01 }) {
  const t = (extra = '') => `<text x="${x}" y="${y}" font-size="${size}" text-anchor="middle" letter-spacing="${size * spacing}" ${extra}>Mayo TV</text>`;
  const ext = [];
  const steps = Math.max(3, Math.round(size * 0.045 * depth));
  const off = size * 0.042 * depth;
  for (let i = steps; i >= 1; i--) {
    const k = i / steps;
    const col = mix('#d9953f', '#7a4318', k);
    ext.push(t(`fill="${col}" transform="translate(0 ${(off * k).toFixed(2)})"`));
  }
  const s = size / 220;
  return `
  <defs>
    <linearGradient id="${id}-face" x1="0" y1="${y - size * 0.72}" x2="0" y2="${y + size * 0.05}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#fffefa"/>
      <stop offset="0.45" stop-color="#fff3d6"/>
      <stop offset="1" stop-color="#ffd07a"/>
    </linearGradient>
    <linearGradient id="${id}-gloss" x1="0" y1="${y - size * 0.75}" x2="0" y2="${y - size * 0.3}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffffff" stop-opacity="0.75"/>
      <stop offset="1" stop-color="#ffffff" stop-opacity="0"/>
    </linearGradient>
    <clipPath id="${id}-clip">${t()}</clipPath>
    <filter id="${id}-bloom" x="-30%" y="-80%" width="160%" height="260%"><feGaussianBlur stdDeviation="${36 * s}"/></filter>
    <filter id="${id}-glow" x="-30%" y="-80%" width="160%" height="260%"><feGaussianBlur stdDeviation="${10 * s}"/></filter>
    <filter id="${id}-shadow" x="-30%" y="-80%" width="160%" height="260%"><feGaussianBlur stdDeviation="${7 * s}"/></filter>
  </defs>
  ${glow ? `<g style="mix-blend-mode:screen">${t(`fill="#ffb54f" opacity="${0.55 * glow}" filter="url(#${id}-bloom)"`)}
  ${t(`fill="#ffe3a8" opacity="${0.55 * glow}" filter="url(#${id}-glow)"`)}</g>` : ''}
  ${t(`fill="#02070b" opacity="0.55" transform="translate(0 ${(off + size * 0.05).toFixed(2)})" filter="url(#${id}-shadow)"`)}
  ${ext.join('\n  ')}
  ${t(`fill="none" stroke="#c9812f" stroke-width="${(size * 0.018).toFixed(2)}" stroke-linejoin="round"`)}
  ${t(`fill="url(#${id}-face)"`)}
  <rect x="${x - size * 3}" y="${y - size}" width="${size * 6}" height="${size * 0.62}" fill="url(#${id}-gloss)" clip-path="url(#${id}-clip)" opacity="0.6"/>`;
}

function mix(a, b, t) {
  const p = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
  const [x, y] = [p(a), p(b)];
  return '#' + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, '0')).join('');
}

// The pitch: a dome with mowing stripes, halfway line, centre circle, a warm light pool and a glowing teal rim.
function pitch({ w, h, crest, cx = w / 2, id, lines = true, stripes = 12 }) {
  const ctrl = 2 * crest - h; // quadratic control so the curve peaks at [crest]
  const d = `M ${-w * 0.05} ${h} Q ${cx} ${ctrl} ${w * 1.05} ${h} Z`;
  const rimD = `M ${-w * 0.05} ${h} Q ${cx} ${ctrl} ${w * 1.05} ${h}`;
  const st = [];
  const vpY = crest - (h - crest) * 3.2;
  for (let i = 0; i < stripes; i += 2) {
    const x0 = -w * 0.3 + (w * 1.6 * i) / stripes;
    const x1 = -w * 0.3 + (w * 1.6 * (i + 1)) / stripes;
    const top = (xx) => cx + (xx - cx) * ((crest - vpY) / (h - vpY));
    st.push(`<path d="M ${top(x0).toFixed(1)} ${crest} L ${top(x1).toFixed(1)} ${crest} L ${x1.toFixed(1)} ${h} L ${x0.toFixed(1)} ${h} Z" fill="#9fe8c8" opacity="0.045"/>`);
  }
  const ringY = crest + (h - crest) * 0.55;
  const s = w / 1280;
  return `
  <defs>
    <linearGradient id="${id}-turf" x1="0" y1="${crest}" x2="0" y2="${h}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#14403d"/>
      <stop offset="0.45" stop-color="#0b2a2b"/>
      <stop offset="1" stop-color="#051316"/>
    </linearGradient>
    <linearGradient id="${id}-rim" x1="0" y1="0" x2="${w}" y2="0" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#6fe0d6" stop-opacity="0.25"/>
      <stop offset="0.5" stop-color="#9ef3ea" stop-opacity="1"/>
      <stop offset="1" stop-color="#6fe0d6" stop-opacity="0.25"/>
    </linearGradient>
    <radialGradient id="${id}-pool" cx="${cx}" cy="${crest + (h - crest) * 0.35}" r="${w * 0.36}" gradientTransform="translate(0 ${(crest + (h - crest) * 0.35) * 0.78}) scale(1 0.22)" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffe2a0" stop-opacity="0.34"/>
      <stop offset="0.6" stop-color="#ffd27e" stop-opacity="0.08"/>
      <stop offset="1" stop-color="#ffd27e" stop-opacity="0"/>
    </radialGradient>
    <clipPath id="${id}-clip"><path d="${d}"/></clipPath>
  </defs>
  <path d="${d}" fill="url(#${id}-turf)"/>
  <g clip-path="url(#${id}-clip)">
    ${st.join('\n    ')}
    <rect x="0" y="${crest}" width="${w}" height="${h - crest}" fill="url(#${id}-pool)" style="mix-blend-mode:screen"/>
    ${lines ? `<g fill="none" stroke="#e9fff7" stroke-opacity="0.2" stroke-width="${2.6 * s}">
      <line x1="${cx}" y1="${crest}" x2="${cx}" y2="${h}"/>
      <ellipse cx="${cx}" cy="${ringY}" rx="${w * 0.15}" ry="${(h - crest) * 0.3}"/>
    </g>
    <ellipse cx="${cx}" cy="${ringY}" rx="${5 * s}" ry="${2.2 * s}" fill="#e9fff7" opacity="0.35"/>` : ''}
  </g>
  <path d="${rimD}" fill="none" stroke="#7beee3" stroke-width="${12 * s}" opacity="0.4" filter="url(#soft8)"/>
  <path d="${rimD}" fill="none" stroke="url(#${id}-rim)" stroke-width="${4 * s}"/>
  <path d="${rimD}" fill="none" stroke="#ffffff" stroke-opacity="0.5" stroke-width="${1.2 * s}"/>`;
}

// Far stands: a dark bowl behind the pitch with a faint lit roof edge and a scatter of crowd lights.
function stands({ w, h, top, crest, seed }) {
  const r = rng(seed);
  const d = `M 0 ${top} C ${w * 0.22} ${top + 20} ${w * 0.36} ${crest - 14} ${w / 2} ${crest - 10} C ${w * 0.64} ${crest - 14} ${w * 0.78} ${top + 20} ${w} ${top} L ${w} ${h} L 0 ${h} Z`;
  const roof = `M 0 ${top} C ${w * 0.22} ${top + 20} ${w * 0.36} ${crest - 14} ${w / 2} ${crest - 10} C ${w * 0.64} ${crest - 14} ${w * 0.78} ${top + 20} ${w} ${top}`;
  const dots = [];
  for (let i = 0; i < 90; i++) {
    const x = r() * w;
    const edge = top + (crest - 10 - top) * Math.pow(1 - Math.abs(x - w / 2) / (w / 2), 0.9);
    const y = edge + 8 + r() * 46;
    dots.push(`<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${(0.8 + r() * 0.9).toFixed(2)}" fill="${r() < 0.5 ? '#ffd99a' : '#bfe9ef'}" opacity="${(0.12 + r() * 0.25).toFixed(2)}"/>`);
  }
  return `
  <path d="${d}" fill="#071219"/>
  ${dots.join('\n  ')}
  <path d="${roof}" fill="none" stroke="#3d6470" stroke-opacity="0.55" stroke-width="2"/>`;
}

function mast({ x, top, bottom, w }) {
  return `
  <path d="M ${x - w * 0.5} ${top} L ${x + w * 0.5} ${top} L ${x + w * 0.85} ${bottom} L ${x - w * 0.85} ${bottom} Z" fill="#0a1820"/>
  <path d="M ${x + w * 0.1} ${top} L ${x + w * 0.5} ${top} L ${x + w * 0.85} ${bottom} L ${x + w * 0.35} ${bottom} Z" fill="#244550" opacity="0.55"/>
  <rect x="${x - w * 2.2}" y="${top - w * 0.2}" width="${w * 4.4}" height="${w * 0.7}" rx="${w * 0.3}" fill="#0d1d26"/>`;
}

function sky({ w, h, id, horizon }) {
  return `
  <defs>
    <linearGradient id="${id}-sky" x1="0" y1="0" x2="0" y2="${horizon}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#03080e"/>
      <stop offset="0.55" stop-color="#081a24"/>
      <stop offset="1" stop-color="#11313b"/>
    </linearGradient>
    <radialGradient id="${id}-vig" cx="${w / 2}" cy="${h * 0.45}" r="${Math.hypot(w, h) * 0.55}" gradientUnits="userSpaceOnUse">
      <stop offset="0.55" stop-color="#000" stop-opacity="0"/>
      <stop offset="1" stop-color="#000" stop-opacity="0.55"/>
    </radialGradient>
  </defs>
  <rect width="${w}" height="${h}" fill="url(#${id}-sky)"/>`;
}

const finish = (w, h, id) => `
  <rect width="${w}" height="${h}" fill="url(#${id}-vig)"/>
  <rect width="${w}" height="${h}" filter="url(#grain)" opacity="0.035" style="mix-blend-mode:overlay"/>`;

// ---- the pictures ----

function banner() {
  const W = 1280, H = 720, crest = 580;
  const L = 118, R = 1162, LY = 98;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  ${fontFace}<defs>${filters}
    <radialGradient id="haze" cx="640" cy="330" r="520" gradientTransform="translate(0 132) scale(1 0.6)" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffd890" stop-opacity="0.16"/>
      <stop offset="1" stop-color="#ffd890" stop-opacity="0"/>
    </radialGradient>
  </defs>
  ${sky({ w: W, h: H, id: 'b', horizon: crest })}
  ${stars({ w: W, h: H, count: 85, seed: 11, maxY: 470, avoid: [[L, LY, 170], [R, LY, 170], [640, 330, 330]] })}
  <rect width="${W}" height="${H}" fill="url(#haze)"/>
  ${stands({ w: W, h: H, top: 470, crest, seed: 5 })}
  ${mast({ x: L, top: 112, bottom: 690, w: 11 })}
  ${mast({ x: R, top: 112, bottom: 690, w: 11 })}
  ${beam({ ax: L, ay: LY + 14, far1: [1010, 455], far2: [470, 735], reach: 1050, id: 'beamL' })}
  ${beam({ ax: R, ay: LY + 14, far1: [270, 455], far2: [810, 735], reach: 1050, id: 'beamR' })}
  ${pitch({ w: W, h: H, crest, id: 'p' })}
  ${lettering({ x: 640, y: 392, size: 204, id: 'mt' })}
  ${lamp({ x: L, y: LY, w: 96, tilt: 9, id: 'lampL' })}
  ${lamp({ x: R, y: LY, w: 96, tilt: -9, id: 'lampR' })}
  ${finish(W, H, 'b')}
</svg>`;
}

function icon() {
  const W = 512, H = 512, crest = 402;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  ${fontFace}<defs>${filters}</defs>
  ${sky({ w: W, h: H, id: 'i', horizon: crest })}
  ${stars({ w: W, h: H, count: 16, seed: 3, maxY: 330, avoid: [[256, 160, 150]], scale: 1.9 })}
  ${mast({ x: 256, top: 176, bottom: 430, w: 16 })}
  ${beam({ ax: 256, ay: 180, far1: [40, 560], far2: [472, 560], reach: 480, id: 'beamI', strength: 1.1 })}
  ${pitch({ w: W, h: H, crest, id: 'pi', lines: false, stripes: 8 })}
  ${lamp({ x: 256, y: 150, w: 150, id: 'lampI', flare: 0.45 })}
  ${finish(W, H, 'i')}
</svg>`;
}

// Transparent lettering on its own: the in-app wordmark (903 × 180 frame) and the splash name (400 × 160).
function word(W, H, size, y, { glow = 0.8, mono = false } = {}) {
  const body = mono
    ? `<text x="${W / 2}" y="${y}" font-size="${size}" text-anchor="middle" letter-spacing="${size * 0.01}" fill="#ffffff">Mayo TV</text>`
    : lettering({ x: W / 2, y, size, id: 'w', glow, depth: 0.8 });
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  ${fontFace}<defs>${filters}</defs>${body}</svg>`;
}

async function render(browser, svg, w, h, outs, { transparent = false, ss = 3 } = {}) {
  const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: ss });
  await page.setContent(`<!doctype html><html><body style="margin:0;background:${transparent ? 'transparent' : '#000'}">${svg}</body></html>`);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(150);
  const tmp = path.join(os.tmpdir(), `mayo-${process.pid}-${Math.random().toString(36).slice(2)}.png`);
  await page.screenshot({ path: tmp, omitBackground: transparent, clip: { x: 0, y: 0, width: w, height: h } });
  await page.close();
  for (const [file, ow, oh] of outs) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const args = [tmp, '-filter', 'Lanczos', '-resize', `${ow}x${oh}!`];
    if (!transparent) args.push('-alpha', 'off');
    args.push('-strip', '-define', 'png:compression-level=9', file);
    execFileSync('convert', args);
  }
  fs.unlinkSync(tmp);
}

(async () => {
  const browser = await chromium.launch();
  const r = (p) => path.join(RES, p);
  await render(browser, banner(), 1280, 720, [
    [r('drawable-xhdpi/mayo_tv_banner_hd.png'), 640, 360],
    [r('drawable-xxhdpi/mayo_tv_banner_hd.png'), 960, 540],
    [r('drawable-xxxhdpi/mayo_tv_banner_hd.png'), 1280, 720],
  ], { ss: 2 });
  await render(browser, icon(), 512, 512, [
    [r('mipmap-mdpi/mayo_tv_icon_hd.png'), 48, 48],
    [r('mipmap-hdpi/mayo_tv_icon_hd.png'), 72, 72],
    [r('mipmap-xhdpi/mayo_tv_icon_hd.png'), 96, 96],
    [r('mipmap-xxhdpi/mayo_tv_icon_hd.png'), 144, 144],
    [r('mipmap-xxxhdpi/mayo_tv_icon_hd.png'), 192, 192],
  ], { ss: 2 });
  await render(browser, word(903, 180, 144, 122), 903, 180, [[r('drawable-nodpi/flutlicht_wordmark.png'), 1806, 360]], { transparent: true, ss: 4 });
  await render(browser, word(903, 180, 144, 122, { mono: true }), 903, 180, [[r('drawable-nodpi/flutlicht_wordmark_mono.png'), 1806, 360]], { transparent: true, ss: 4 });
  await render(browser, word(400, 160, 84, 102, { glow: 0.7 }), 400, 160, [
    [r('drawable-xhdpi/flutlicht_splash_name.png'), 400, 160],
    [r('drawable-xxhdpi/flutlicht_splash_name.png'), 600, 240],
    [r('drawable-xxxhdpi/flutlicht_splash_name.png'), 800, 320],
  ], { transparent: true, ss: 4 });
  if (PREVIEW) {
    fs.mkdirSync(PREVIEW, { recursive: true });
    for (const [name, svg] of [['banner.svg', banner()], ['icon.svg', icon()]]) fs.writeFileSync(path.join(PREVIEW, name), svg);
  }
  await browser.close();
})();
