#!/usr/bin/env node
// Writes one HTML page for each App Store screenshot.
// Usage: node compose.mjs <out-dir> [filter]
// A filter writes only the pages whose name (<locale>__<layout>__<shot>) holds it.
// Input: ../frames/shots.json and the raw simulator screenshots in ../raw/
// (<device>-<lang>-<shot>.png). The look is shared with the Google Play frames,
// so the night plates, the cast heads and Geist come from ../../../android/PlayStore/.
// tools/render-store.sh draws each page with headless Chromium.
// The pages place real app screenshots. They never draw or change app UI.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");
const play = resolve(root, "..", "..", "android", "PlayStore");
const out = resolve(process.argv[2] ?? join(root, ".build", "pages"));
mkdirSync(out, { recursive: true });

const filter = process.argv[3] ?? "";
const spec = JSON.parse(readFileSync(join(root, "frames", "shots.json"), "utf8"));
const ACCENT = "#FF4D6D";
const THREAD_W = 1080;
const THREAD_H = 1920;
const THREAD_CENTER = 1600;

const url = (...p) => pathToFileURL(join(...p)).href;
const font = (file) => readFileSync(join(play, "tools", "fonts", file)).toString("base64");
const fonts = [
  ["Geist-Medium.ttf", 500],
  ["Geist-Bold.ttf", 700],
  ["Geist-ExtraBold.ttf", 800],
]
  .map(([f, w]) => `@font-face{font-family:Geist;font-weight:${w};src:url(data:font/ttf;base64,${font(f)}) format("truetype")}`)
  .join("\n");

const pngSize = (file) => {
  const b = readFileSync(file);
  return [b.readUInt32BE(16), b.readUInt32BE(20)];
};

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
// "Line one\n{accent part}" -> one span per line, the braced part in the accent colour.
const title = (text) =>
  text
    .split("\n")
    .map((line) => `<span class="line">${esc(line).replace(/\{(.+?)\}/g, '<span class="hl">$1</span>')}</span>`)
    .join("");

// A text is one string per locale, or one set of strings per device.
const text = (value, device, loc) => (value[device]?.[loc] ?? value[loc]);
// A box is [x, y, w, h], or one box per locale.
const pick = (box, loc) => (Array.isArray(box) ? box : box[loc]);

const page = (w, h, css, body, fit) => `<!doctype html>
<html><head><meta charset="utf-8"><style>
${fonts}
*{margin:0;padding:0;box-sizing:border-box}
html,body{width:${w}px;height:${h}px;overflow:hidden;background:#140A16}
body{position:relative;font-family:Geist,sans-serif;color:#fff;-webkit-font-smoothing:antialiased}
.plate{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.tint{position:absolute;inset:0}
.thread{position:absolute;left:0;top:0}
.window,.card{position:absolute;overflow:hidden}
.bar{position:absolute;left:0;top:0;right:0;display:flex;align-items:center;gap:var(--dot-gap);padding-left:var(--dot-gap);background:#26262b}
.bar i{display:block;width:var(--dot);height:var(--dot);border-radius:50%;background:#ff5f57}
.bar i:nth-child(2){background:#febc2e}
.bar i:nth-child(3){background:#28c840}
.window img,.card img{position:absolute;max-width:none}
.ring{position:absolute;inset:0;border-radius:inherit;pointer-events:none}
.head{position:absolute}
.caption{position:absolute;left:50%;transform:translateX(-50%);display:flex;flex-direction:column;align-items:center;text-align:center}
.title .line{display:block;white-space:nowrap}
.title .hl{color:${ACCENT}}
${css}
</style></head><body>
${body}
<script>
// Shrink the title until every line fits its box.
(${fit})();
</script>
</body></html>`;

const fitTitle = function () {
  const t = document.querySelector(".title");
  const max = t.clientWidth;
  let size = parseFloat(getComputedStyle(t).fontSize);
  const lines = [...t.querySelectorAll(".line")];
  while (size > 40 && lines.some((l) => l.scrollWidth > max)) {
    size -= 2;
    t.style.fontSize = size + "px";
  }
  document.body.dataset.titleSize = String(size);
};

// The thread is drawn in Play units and scaled to the canvas width.
// Each frame shows its own slice, so the line runs on from frame to frame.
const thread = (d, index, W, H) => {
  const k = W / THREAD_W;
  const dy = 0.83 * H - THREAD_CENTER * k;
  return `<svg class="thread" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" aria-hidden="true">
  <defs><filter id="glow" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation="${22 * k}"/></filter></defs>
  <g transform="translate(${-index * W} ${dy}) scale(${k})">
    <path d="${d}" fill="none" stroke="#FF2A57" stroke-width="64" stroke-opacity="0.35" filter="url(#glow)" stroke-linecap="round"/>
    <path d="${d}" fill="none" stroke="#FF2A57" stroke-width="18" stroke-opacity="0.30" stroke-linecap="round"/>
    <path d="${d}" fill="none" stroke="${ACCENT}" stroke-width="6" stroke-linecap="round"/>
  </g>
</svg>`;
};

const css = (L, k) => `
.window{--dot:${L.chrome?.dot ?? 0}px;--dot-gap:${L.chrome?.gap ?? 0}px}
.tint{background:radial-gradient(${900 * k}px ${700 * k}px at 50% ${180 * k}px,rgba(124,44,112,0.55),rgba(124,44,112,0) 70%),linear-gradient(180deg,rgba(20,10,22,0.35),rgba(20,10,22,0) 40%)}
.caption{top:${L.caption.top}px;width:${L.caption.width}px;gap:${L.caption.gap}px}
.title{width:${L.caption.width}px;font-size:${L.caption.title}px;font-weight:700;line-height:1.04;letter-spacing:-0.03em}
.sub{width:${L.caption.subWidth}px;text-wrap:balance;font-size:${L.caption.sub}px;font-weight:500;line-height:1.3;color:rgba(255,255,255,0.78)}
.window{background:#111113;box-shadow:0 ${70 * k}px ${140 * k}px rgba(0,0,0,0.62),0 0 ${140 * k}px rgba(255,42,87,0.16)}
.window .ring{box-shadow:inset 0 0 0 ${2 * k}px rgba(255,255,255,0.12)}
.card{box-shadow:0 ${50 * k}px ${110 * k}px rgba(0,0,0,0.65),0 0 ${90 * k}px rgba(255,42,87,0.38)}
.card .ring{box-shadow:inset 0 0 0 ${3 * k}px rgba(255,77,109,0.6)}
.head{filter:drop-shadow(0 ${40 * k}px ${60 * k}px rgba(255,42,87,0.45))}
`;

const pages = [];
for (const [layoutName, L] of Object.entries(spec.layouts)) {
  const [W, H] = L.size;
  const k = W / THREAD_W;
  spec.shots.filter((shot) => shot.raw[L.device]).forEach((shot, i) => {
    for (const [loc, lang] of Object.entries(spec.locales)) {
      const name = `${loc}__${layoutName}__${shot.id}`;
      if (!name.includes(filter)) continue;
      const rawFile = join(root, "raw", `${L.rawDevice ?? L.device}-${lang}-${shot.raw[L.device]}.png`);
      const [rw, rh0] = pngSize(rawFile);
      const sc = { ...L.screen, ...(shot.screen?.[L.device] ?? {}) };
      const rh = sc.cropH ?? rh0;
      const s = sc.width / (sc.cropW ?? rw);
      const sx = Math.round((W - sc.width) / 2);
      const raw = url(rawFile);
      const bar = L.chrome?.bar ?? 0;
      const screenH = bar + Math.round(rh * s);
      const top = bar - Math.round((sc.cropY ?? 0) * s);
      const parts = [
        `<img class="plate" src="${url(play, "graphics", "source", "bg", shot.plate + ".jpg")}" alt="">`,
        `<div class="tint"></div>`,
        thread(spec.thread, i, W, H),
        `<div class="window" style="left:${sx}px;top:${sc.y}px;width:${sc.width}px;height:${screenH}px;border-radius:${sc.radius}px">
  <img src="${raw}" alt="" style="left:0;top:${top}px;width:${Math.round(rw * s)}px">${bar ? `<div class="bar" style="height:${bar}px"><i></i><i></i><i></i></div>` : ""}<div class="ring"></div></div>`,
      ];
      if (shot.card) {
        for (const c of [].concat(shot.card[L.device] ?? [])) {
          // A card lifts a box out of this screen, or out of another raw when c.src is set.
          const cFile = c.src ? join(root, "raw", `${L.device}-${lang}-${c.src}.png`) : rawFile;
          const [cw] = pngSize(cFile);
          const [bx, by, bw, bh] = pick(c.box, loc);
          const kc = c.width ? c.width / bw : L.card.scale * s;
          const width = Math.round(bw * kc);
          const height = Math.round(bh * kc);
          const x = c.width ? Math.round((W - width) / 2) : Math.round(Math.min(Math.max(sx + (bx + bw / 2) * s - width / 2, 40 * k), W - 40 * k - width));
          const y = c.y ?? Math.round(sc.y + (by + bh / 2) * s + (c.dy ?? 0) - height / 2);
          parts.push(`<div class="card" style="left:${x}px;top:${y}px;width:${width}px;height:${height}px;border-radius:${c.radius ?? L.card.radius}px">
  <img src="${url(cFile)}" alt="" style="left:${Math.round(-bx * kc)}px;top:${Math.round(-by * kc)}px;width:${Math.round(cw * kc)}px"><div class="ring"></div></div>`);
        }
      }
      if (shot.head && L.head) {
        parts.push(`<img class="head" src="${url(play, "raw", "cast", shot.head + ".png")}" alt="" style="left:${L.head.x}px;top:${L.head.y}px;width:${L.head.size}px">`);
      }
      parts.push(`<div class="caption"><div class="title">${title(text(shot.title, L.textDevice ?? L.device, loc))}</div><div class="sub">${esc(text(shot.sub, L.textDevice ?? L.device, loc))}</div></div>`);
      writeFileSync(join(out, name + ".html"), page(W, H, css(L, k), parts.join("\n"), fitTitle.toString()));
      pages.push({ name, width: W, height: H, target: `frames/${loc}/${layoutName}/${shot.id}.png` });
    }
  });
}

writeFileSync(join(out, "pages.json"), JSON.stringify(pages, null, 2));
console.log(`wrote ${pages.length} pages to ${out}`);
