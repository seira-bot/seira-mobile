#!/usr/bin/env node
// Writes one HTML page for each Play screenshot and feature graphic.
// Usage: node compose.mjs <out-dir>
// Input: ../frames/shots.json, renders in ../raw/, cast heads in ../raw/cast/,
// night plates in ../graphics/source/bg/, and Geist in ./fonts/.
// tools/render-store.sh draws each page with headless Chromium.
// The pages place real app renders. They never draw or change app UI.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");
const out = resolve(process.argv[2] ?? join(root, ".build", "pages"));
mkdirSync(out, { recursive: true });

const spec = JSON.parse(readFileSync(join(root, "frames", "shots.json"), "utf8"));
const locales = ["en-US", "id-ID"];
const RAW_W = 1233;
const ACCENT = "#FF4D6D";

const url = (...p) => pathToFileURL(join(root, ...p)).href;
const font = (file) => readFileSync(join(here, "fonts", file)).toString("base64");
const fonts = [
  ["Geist-Medium.ttf", 500],
  ["Geist-Bold.ttf", 700],
  ["Geist-ExtraBold.ttf", 800],
]
  .map(([f, w]) => `@font-face{font-family:Geist;font-weight:${w};src:url(data:font/ttf;base64,${font(f)}) format("truetype")}`)
  .join("\n");

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
// "Line one\n{accent part}" -> one span per line, the braced part in the accent colour.
const title = (text) =>
  text
    .split("\n")
    .map((line) => `<span class="line">${esc(line).replace(/\{(.+?)\}/g, '<span class="hl">$1</span>')}</span>`)
    .join("");

// A crop of a render, as a positioned window. box is [x, y, w, h] in render pixels.
const crop = (src, box, k, x, y, cls) => {
  const [bx, by, bw, bh] = box;
  return `<div class="${cls}" style="left:${Math.round(x)}px;top:${Math.round(y)}px;width:${Math.round(bw * k)}px;height:${Math.round(bh * k)}px">
  <img src="${src}" alt="" style="left:${Math.round(-bx * k)}px;top:${Math.round(-by * k)}px;width:${Math.round(RAW_W * k)}px">
</div>`;
};

const page = (w, h, css, body, fit) => `<!doctype html>
<html><head><meta charset="utf-8"><style>
${fonts}
*{margin:0;padding:0;box-sizing:border-box}
html,body{width:${w}px;height:${h}px;overflow:hidden;background:#140A16}
body{position:relative;font-family:Geist,sans-serif;color:#fff;-webkit-font-smoothing:antialiased}
.plate{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.tint{position:absolute;inset:0}
.thread{position:absolute;left:0;top:0}
.window,.card,.notice{position:absolute;overflow:hidden}
.window img,.card img,.notice img{position:absolute;max-width:none}
.ring{position:absolute;inset:0;border-radius:inherit;pointer-events:none}
.head{position:absolute}
.title .line{display:block;white-space:nowrap}
.title .hl{color:${ACCENT}}
${css}
</style></head><body>
${body}
<script>
// Shrink the title until every line fits its box. Keep one size per page.
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

const thread = (d, ox, w, h) => `<svg class="thread" width="${w}" height="${h}" viewBox="${ox} 0 ${w} ${h}" aria-hidden="true">
  <defs><filter id="glow" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation="22"/></filter></defs>
  <path d="${d}" fill="none" stroke="#FF2A57" stroke-width="64" stroke-opacity="0.35" filter="url(#glow)" stroke-linecap="round"/>
  <path d="${d}" fill="none" stroke="#FF2A57" stroke-width="18" stroke-opacity="0.30" stroke-linecap="round"/>
  <path d="${d}" fill="none" stroke="${ACCENT}" stroke-width="6" stroke-linecap="round"/>
</svg>`;

const shotCss = `
.tint{background:radial-gradient(900px 700px at 540px 180px,rgba(124,44,112,0.55),rgba(124,44,112,0) 70%),linear-gradient(180deg,rgba(20,10,22,0.35),rgba(20,10,22,0) 40%)}
.caption{position:absolute;left:70px;top:112px;width:940px;display:flex;flex-direction:column;align-items:center;gap:26px;text-align:center}
.title{width:940px;font-size:88px;font-weight:700;line-height:1.04;letter-spacing:-0.03em}
.sub{width:900px;font-size:38px;font-weight:500;line-height:1.3;color:rgba(255,255,255,0.78)}
.window{background:#111113;box-shadow:0 70px 140px rgba(0,0,0,0.62),0 0 140px rgba(255,42,87,0.16)}
.window .ring{box-shadow:inset 0 0 0 2px rgba(255,255,255,0.12)}
.card,.notice{box-shadow:0 50px 110px rgba(0,0,0,0.65),0 0 90px rgba(255,42,87,0.38)}
.card .ring,.notice .ring{box-shadow:inset 0 0 0 3px rgba(255,77,109,0.6)}
.head{filter:drop-shadow(0 40px 60px rgba(255,42,87,0.45))}
`;

const pages = [];
spec.shots.forEach((shot, i) => {
  const [W, H] = spec.size;
  const sc = { ...spec.screen, ...(shot.screen ?? {}) };
  const s = sc.width / RAW_W;
  const screenH = Math.round(2193 * s);
  for (const loc of locales) {
    const raw = url("raw", shot.raw[loc]);
    const parts = [
      `<img class="plate" src="${url("graphics", "source", "bg", shot.plate + ".jpg")}" alt="">`,
      `<div class="tint"></div>`,
      thread(spec.thread, i * W, W, H),
      `<div class="window" style="left:${sc.x}px;top:${sc.y}px;width:${sc.width}px;height:${screenH}px;border-radius:${sc.radius}px">
  <img src="${raw}" alt="" style="left:0;top:0;width:${sc.width}px"><div class="ring"></div></div>`,
    ];
    if (shot.card) {
      const k = spec.card.scale;
      const [bx, by, bw, bh] = shot.card.box;
      const cx = sc.x + (bx + bw / 2) * s;
      const cy = sc.y + (by + bh / 2) * s + (shot.card.dy ?? 0);
      const x = Math.min(Math.max(cx - (bw * k) / 2, 40), W - 40 - bw * k);
      parts.push(
        crop(raw, shot.card.box, k, x, cy - (bh * k) / 2, "card").replace("</div>", `<div class="ring"></div></div>`).replace('class="card" style="', `class="card" style="border-radius:${spec.card.radius}px;`)
      );
    }
    if (shot.notice) {
      const n = shot.notice;
      const k = n.width / n.box[2];
      parts.push(
        crop(url("raw", n.src), n.box, k, (W - n.width) / 2, n.y, "notice").replace("</div>", `<div class="ring"></div></div>`).replace('class="notice" style="', `class="notice" style="border-radius:44px;`)
      );
    }
    if (shot.head) {
      const hd = shot.head;
      parts.push(`<img class="head" src="${url("raw", "cast", hd.cast + ".png")}" alt="" style="left:${hd.x}px;top:${hd.y}px;width:${hd.size}px">`);
    }
    parts.push(`<div class="caption"><div class="title">${title(shot.title[loc])}</div><div class="sub">${esc(shot.sub[loc])}</div></div>`);
    const name = `${loc}__${shot.id}`;
    writeFileSync(join(out, name + ".html"), page(W, H, shotCss, parts.join("\n"), fitTitle.toString()));
    pages.push({ name, width: W, height: H, target: `frames/${loc}/${shot.id}.png` });
  }
});

const fg = spec.feature;
const fgCss = `
.tint{background:radial-gradient(640px 520px at 110px 60px,rgba(124,44,112,0.75),rgba(124,44,112,0) 72%),radial-gradient(520px 420px at 200px 470px,rgba(96,30,84,0.6),rgba(96,30,84,0) 70%),linear-gradient(90deg,rgba(58,18,52,0.55),rgba(58,18,52,0) 60%)}
.caption{position:absolute;left:64px;top:0;height:500px;width:460px;display:flex;flex-direction:column;justify-content:center;gap:20px}
.title{width:460px;font-size:60px;font-weight:700;line-height:1.04;letter-spacing:-0.03em}
.sub{width:420px;font-size:22px;font-weight:500;line-height:1.36;color:rgba(255,255,255,0.8)}
.card{box-shadow:0 30px 70px rgba(0,0,0,0.6),0 0 60px rgba(255,42,87,0.35)}
.card .ring{box-shadow:inset 0 0 0 2px rgba(255,77,109,0.55)}
.card.back{opacity:0.92}
.head{filter:drop-shadow(0 24px 36px rgba(255,42,87,0.5))}
`;
for (const loc of locales) {
  const back = fg.back.box;
  const front = fg.front.box;
  const kb = 390 / back[2];
  const kf = 430 / front[2];
  const cardHtml = (src, box, k, x, y, extra) =>
    crop(src, box, k, x, y, "card" + extra).replace("</div>", `<div class="ring"></div></div>`).replace(/class="card[^"]*" style="/, (m) => m + "border-radius:22px;");
  const parts = [
    `<img class="plate" src="${url("graphics", "source", "bg", fg.plate + ".jpg")}" alt="">`,
    `<div class="tint"></div>`,
    thread("M -40 440 C 200 360 380 480 580 400 S 860 280 1080 340", 0, 1024, 500),
    cardHtml(url("raw", fg.back.raw[loc]), back, kb, 590, 58, " back"),
    cardHtml(url("raw", fg.front.raw[loc]), front, kf, 536, 196, ""),
    `<img class="head" src="${url("raw", "cast", fg.head.cast + ".png")}" alt="" style="left:800px;top:236px;width:250px">`,
    `<div class="caption"><div class="title">${title(fg.title[loc])}</div><div class="sub">${esc(fg.sub[loc])}</div></div>`,
  ];
  const name = `${loc}__feature`;
  writeFileSync(join(out, name + ".html"), page(1024, 500, fgCss, parts.join("\n"), fitTitle.toString()));
  pages.push({ name, width: 1024, height: 500, target: `graphics/feature-graphic-${loc}.png` });
}

writeFileSync(join(out, "pages.json"), JSON.stringify(pages, null, 2));
console.log(`wrote ${pages.length} pages to ${out}`);
