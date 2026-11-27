#!/usr/bin/env node
// Writes one HTML page for each App Store creative asset (product page header, search results).
// Usage: node compose-creative.mjs <out-dir> [--safe]
// Input: ../frames/creatives.json and the raw iPhone screenshots in ../raw/.
// The look is shared with the screenshot frames, so the night plates, the cast heads and Geist
// come from ../../../android/PlayStore/. tools/render-store.sh draws each page.
// The pages place crops of real app screenshots. They never draw or change app UI.
// --safe draws the Apple art safe area on each page, for a visual check only.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");
const play = resolve(root, "..", "..", "android", "PlayStore");
const out = resolve(process.argv[2] ?? join(root, ".build", "pages"));
const showSafe = process.argv.includes("--safe");
mkdirSync(out, { recursive: true });

const spec = JSON.parse(readFileSync(join(root, "frames", "creatives.json"), "utf8"));
const ACCENT = "#FF4D6D";

const url = (...p) => pathToFileURL(join(...p)).href;
const font = (file) => readFileSync(join(play, "tools", "fonts", file)).toString("base64");
const fonts = [
  ["Geist-Medium.ttf", 500],
  ["Geist-Bold.ttf", 700],
]
  .map(([f, w]) => `@font-face{font-family:Geist;font-weight:${w};src:url(data:font/ttf;base64,${font(f)}) format("truetype")}`)
  .join("\n");

const pngWidth = (file) => readFileSync(file).readUInt32BE(16);
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const title = (text) =>
  text
    .split("\n")
    .map((line) => `<span class="line">${esc(line).replace(/\{(.+?)\}/g, '<span class="hl">$1</span>')}</span>`)
    .join("");

const fitTitle = function () {
  const t = document.querySelector(".title");
  if (!t) return;
  const max = t.clientWidth;
  let size = parseFloat(getComputedStyle(t).fontSize);
  const lines = [...t.querySelectorAll(".line")];
  while (size > 60 && lines.some((l) => l.scrollWidth > max)) {
    size -= 2;
    t.style.fontSize = size + "px";
  }
  document.body.dataset.titleSize = String(size);
};

const css = (W, H) => {
  const u = W / 1280;
  return `
${fonts}
*{margin:0;padding:0;box-sizing:border-box}
html,body{width:${W}px;height:${H}px;overflow:hidden;background:#140A16}
body{position:relative;font-family:Geist,sans-serif;color:#fff;-webkit-font-smoothing:antialiased}
.plate{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.glow,.shade,.thread,.safe{position:absolute;inset:0;pointer-events:none}
.shade{background:radial-gradient(ellipse 70% 85% at 50% 50%,rgba(20,10,22,0) 55%,rgba(20,10,22,0.55) 100%)}
.card{position:absolute;overflow:hidden;box-shadow:0 ${50 * u}px ${110 * u}px rgba(0,0,0,0.65),0 0 ${90 * u}px rgba(255,42,87,0.38)}
.card img{position:absolute;max-width:none}
.card .ring{position:absolute;inset:0;border-radius:inherit;box-shadow:inset 0 0 0 ${3 * u}px rgba(255,77,109,0.6)}
.head{position:absolute;filter:drop-shadow(0 ${16 * u}px ${26 * u}px rgba(120,10,40,0.55))}
.dim{opacity:0.58}
.card.dim{filter:blur(${0.8 * u}px);box-shadow:0 ${40 * u}px ${90 * u}px rgba(0,0,0,0.55)}
.card.dim .ring{box-shadow:inset 0 0 0 ${2 * u}px rgba(255,255,255,0.14)}
.head.dim{filter:drop-shadow(0 ${16 * u}px ${26 * u}px rgba(0,0,0,0.5)) blur(${0.8 * u}px)}
.caption{position:absolute;left:50%;transform:translateX(-50%);text-align:center}
.title{font-weight:700;line-height:1.04;letter-spacing:-0.03em}
.title .line{display:block;white-space:nowrap}
.title .hl{color:${ACCENT}}
.safe{border:6px dashed #00E5FF}
`;
};

const glows = (list) =>
  list.map((g) => `radial-gradient(${g.rx}px ${g.ry}px at ${g.x}px ${g.y}px,${g.color},rgba(0,0,0,0) 70%)`).join(",");

const thread = (d, W, H) => {
  const u = W / 1280;
  return `<svg class="thread" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" aria-hidden="true">
  <defs><filter id="glow" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation="${22 * u}"/></filter></defs>
  <path d="${d}" fill="none" stroke="#FF2A57" stroke-width="${64 * u}" stroke-opacity="0.35" filter="url(#glow)" stroke-linecap="round"/>
  <path d="${d}" fill="none" stroke="#FF2A57" stroke-width="${18 * u}" stroke-opacity="0.30" stroke-linecap="round"/>
  <path d="${d}" fill="none" stroke="${ACCENT}" stroke-width="${6 * u}" stroke-linecap="round"/>
</svg>`;
};

const pages = [];
for (const asset of spec.assets) {
  const [W, H] = asset.size;
  for (const [loc, lang] of Object.entries(spec.locales)) {
    const parts = [
      `<img class="plate" src="${url(play, "graphics", "source", "bg", asset.plate + ".jpg")}" alt="">`,
      `<div class="glow" style="background:${glows(asset.glows)}"></div>`,
      `<div class="shade"></div>`,
      thread(asset.thread, W, H),
    ];
    for (const layer of asset.layers) {
      const dim = layer.dim ? " dim" : "";
      if (layer.crop) {
        const c = spec.crops[layer.crop];
        const file = join(root, "raw", `iphone-${lang}-${c.src}.png`);
        const [bx, by, bw, bh] = c.box;
        const k = layer.width / bw;
        parts.push(`<div class="card${dim}" style="left:${layer.x}px;top:${layer.y}px;width:${layer.width}px;height:${Math.round(bh * k)}px;border-radius:${Math.round(c.radius * k)}px">
  <img src="${url(file)}" alt="" style="left:${Math.round(-bx * k)}px;top:${Math.round(-by * k)}px;width:${Math.round(pngWidth(file) * k)}px"><div class="ring"></div></div>`);
      } else if (layer.head) {
        parts.push(`<img class="head${dim}" src="${url(play, "raw", "cast", layer.head + ".png")}" alt="" style="left:${layer.x}px;top:${layer.y}px;width:${layer.size}px">`);
      }
    }
    if (asset.title) {
      const t = asset.title;
      parts.push(`<div class="caption" style="top:${t.top}px;width:${t.width}px"><div class="title" style="width:${t.width}px;font-size:${t.size}px">${title(t.text[loc])}</div></div>`);
    }
    if (showSafe) {
      const [x, y, w, h] = asset.safe;
      parts.push(`<div class="safe" style="inset:auto;left:${x}px;top:${y}px;width:${w}px;height:${h}px"></div>`);
    }
    const name = `${loc}__creative__${asset.id}`;
    const html = `<!doctype html>
<html><head><meta charset="utf-8"><style>${css(W, H)}</style></head><body>
${parts.join("\n")}
<script>(${fitTitle})();</script>
</body></html>`;
    writeFileSync(join(out, name + ".html"), html);
    pages.push({ name, width: W, height: H, target: `frames/${loc}/creative/${asset.id}.png` });
  }
}

writeFileSync(join(out, "creative-pages.json"), JSON.stringify(pages, null, 2));
console.log(`wrote ${pages.length} pages to ${out}`);
