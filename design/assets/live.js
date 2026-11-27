(function () {
  "use strict";

  const S = window.Seira;

  const EXTRA = {
    stop: '<rect x="6" y="6" width="12" height="12" rx="3" fill="currentColor" stroke="none"/>',
    retry: '<path d="M20 12a8 8 0 1 1-2.3-5.7"/><path d="M20 4v4h-4"/>',
    hangup: '<path d="M3.5 14.5c5-5 12-5 17 0l-1.8 2.6-3.7-1.4v-2.6a9 9 0 0 0-6 0v2.6l-3.7 1.4Z" fill="currentColor"/>',
    "mic-off": '<path d="M15 9.5V6a3 3 0 0 0-5.7-1.3M9 9v2a3 3 0 0 0 4.6 2.5M5.5 11a6.5 6.5 0 0 0 10.6 5M18.5 11a6.4 6.4 0 0 1-.5 2.5M12 17.5V21M3.5 3.5l17 17"/>',
    camera: '<path d="M4 8.5A2.5 2.5 0 0 1 6.5 6h1.7l1.3-2h5l1.3 2h1.7A2.5 2.5 0 0 1 20 8.5v8a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 16.5Z"/><circle cx="12" cy="12.5" r="3.5"/>',
    send: '<path d="M12 19V5M5.5 11.5 12 5l6.5 6.5"/>',
    skip: '<path d="M5 5.5 14 12l-9 6.5ZM18.5 5.5v13"/>',
    play: '<path d="M7 4.5 19 12 7 19.5Z" fill="currentColor"/>',
    alert: '<path d="M12 3.5 21.5 20h-19Z"/><path d="M12 10v4.5M12 17.2v.3"/>',
    bubble: '<path d="M4 6.8A2.8 2.8 0 0 1 6.8 4h10.4A2.8 2.8 0 0 1 20 6.8v7.4a2.8 2.8 0 0 1-2.8 2.8H10l-4.5 3.5V17A2.8 2.8 0 0 1 4 14.2Z"/><path d="M10 8.6a2.1 2.1 0 1 1 3 1.9c-.7.4-1 .8-1 1.5M12 14v.3"/>',
    terminal: '<rect x="3" y="4.5" width="18" height="15" rx="3"/><path d="m7 10 3 2.5L7 15M12.5 15H17"/>',
    files: '<path d="M8 3.5h7l4 4V17a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5.5a2 2 0 0 1 2-2Z"/><path d="M14.5 3.5V8H19M4 8v11a2 2 0 0 0 2 2h8"/>',
    keyboard: '<rect x="2.5" y="6" width="19" height="12" rx="3"/><path d="M6.5 10h.1M10 10h.1M13.5 10h.1M17 10h.1M7.5 14h9"/>',
    wifi: '<path d="M3 9.5a13 13 0 0 1 18 0M6.2 13a8.5 8.5 0 0 1 11.6 0M9.4 16.4a4 4 0 0 1 5.2 0"/><circle cx="12" cy="19.5" r="0.6" fill="currentColor"/>',
    torch: '<path d="M8 3.5h8v3l-2 3.5V20a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V10L8 6.5Z"/><path d="M12 12.5v2"/>',
    bt: '<path d="m7 7.5 10 9-5 4.5V3l5 4.5-10 9"/>',
    seira:
      '<path fill="currentColor" stroke="none" fill-rule="evenodd" d="M1 23V12a11 11 0 0 1 22 0v11Zm4-15.2h14V12a7 7 0 0 1-14 0Z"/><rect x="7.9" y="10" width="2.8" height="4.6" rx="1.4" fill="currentColor" stroke="none"/><rect x="13.3" y="10" width="2.8" height="4.6" rx="1.4" fill="currentColor" stroke="none"/>',
  };

  function injectExtra() {
    if (document.getElementById("live-sprite")) return;
    const symbols = Object.entries(EXTRA)
      .map(
        ([name, body]) =>
          `<symbol id="i-${name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${body}</symbol>`
      )
      .join("");
    const clips = Object.entries(FACES)
      .map(([style, f]) => `<clipPath id="hc-${style}"><path d="${f.clip}"/></clipPath>`)
      .join("");
    const holder = document.createElement("div");
    holder.innerHTML = `<svg id="live-sprite" xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">${symbols}<defs>${clips}<filter id="hd-glow" x="-60%" y="-60%" width="220%" height="220%"><feDropShadow dx="0" dy="0" stdDeviation="3.5" flood-color="#e11d48" flood-opacity="0.9"/></filter></defs></svg>`;
    document.body.prepend(holder.firstChild);
  }

  const ic = (name) => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;

  const BOTS = {
    atlas: { name: "Atlas", hair: "short-neat", color: "#377FE6" },
    mira: { name: "Mira", hair: "twin-tails", color: "#D84F8B" },
    juno: { name: "Juno", hair: "spiky", color: "#01A492" },
    seira: { name: "Seira", hair: "seira", color: "#D94B52" },
    nox: { name: "Nox", hair: "messy", color: "#8057C8" },
    kei: { name: "Kei", hair: "bob", color: "#E78531" },
  };

  const ACC = { working: "#4f8dff", needs: "#f5a524", failed: "#ff5c6c", done: "#3ccf8e", soon: "#a78bfa" };

  const FACES = {"short-neat":{"cx":120,"cy":136.1,"dx":37.1,"w":20.1,"h":38,"round":32.1,"my":157.6,"clip":"M97.5 96.27 C95.74 99.63 97.99 102.3 93.82 105 C93.25 105.37 92.65 105.74 92 105.95 C89.81 106.65 70.42 106.93 66.5 107.1 C65.49 107.15 57.81 107.28 57.5 107.5 C56.81 107.97 55.07 112.43 54.26 113.5 C52.14 116.33 51.82 119.53 50.13 122.5 C49.83 123.03 49.12 123.34 49 123.93 C48.94 124.22 49.72 127.43 49.5 128.5 C49.39 129.03 49.01 129.47 48.9 130 C47.7 135.3 49.03 150.01 50.07 155.5 C52.2 166.69 60.91 176.54 71.5 180.42 C73.91 181.3 76.61 181.32 79 182.23 C84.59 184.34 101.05 185.71 107.5 185.94 C110.93 186.06 119.66 186.97 122.5 186.47 C123.03 186.38 123.48 186 124 185.89 C125.37 185.61 127.1 185.8 128.5 185.75 C132.35 185.64 143.51 185.9 146.5 185.21 C147.03 185.09 147.47 184.69 148 184.56 C149.88 184.11 152.07 184.51 154 184.28 C161.86 183.33 171.3 181.1 178 176.76 C183.89 172.94 188.54 167.01 191.16 160.5 C196.15 148.11 193.87 139.65 194.52 127.5 C194.57 126.47 195.3 125.53 195.32 124.5 C195.35 122.96 192.59 118.56 191.83 117 C191.05 115.4 187.57 107.33 186.5 106.69 C185.72 106.22 180.24 106.44 179 106.39 C172.5 106.13 166 105.87 159.5 105.61 C157.76 105.54 153.45 105.9 152 105.31 C149.9 104.46 148.43 102.16 148.18 100 C148.18 100 148.14 97 148.08 97 C147.57 97 147.88 98 147.78 98.5 C147.68 99 147.57 99.5 147.48 100 C147.19 101.71 147.36 103.17 146.01 104.5 C143.36 107.1 134.69 106 131 106 C124 106 117 106 110 106 C107.12 106 103.85 106.44 101 106 C97.8 105.51 97.89 102.53 97.86 100 C97.85 98.93 97.88 98.06 97.71 97 C97.67 96.75 97.62 96.05 97.5 96.27 Z"},"twin-tails":{"cx":120,"cy":111.4,"dx":37,"w":20.1,"h":37.9,"round":32.1,"my":132.9,"clip":"M95.5 72.23 C94.67 74.33 95.59 78.51 93.5 79.8 C92.05 80.7 86.39 80.19 84.5 80.22 C81.13 80.27 75.61 79.72 72.5 80.38 C71.97 80.5 71.53 80.89 71 81 C68.25 81.59 64.82 81.13 62 81.19 C60.6 81.22 58.87 81 57.5 81.28 C56.97 81.39 56.53 81.81 56 81.86 C55.46 81.91 53.58 81.15 53 81.61 C52.28 82.19 49.78 92.46 49.51 94 C48.98 96.96 48.19 99.99 47.99 103 C47.38 112.39 47.32 127.16 50.35 136 C52.5 142.28 55.87 146.72 60.82 151 C62.32 152.3 63.8 153.86 65.5 154.9 C74.32 160.33 85.46 161.11 95.5 161.95 C96.9 162.06 98.64 161.96 100 162.32 C100.53 162.47 100.96 162.88 101.49 163 C104.18 163.62 113.16 163.12 116.5 163.15 C122.49 163.21 128.52 163.47 134.5 163.08 C142.01 162.58 149.67 162.43 157 160.9 C163.44 159.55 170.9 158.38 176.5 154.68 C178.37 153.44 180.02 151.5 181.68 150 C184.32 147.6 186.59 145.22 188.24 142 C192.92 132.86 193.6 122.01 193.6 112 C193.6 110.62 193.87 108.85 193.6 107.5 C192.89 103.97 192.57 102.17 192.16 98.5 C191.88 95.94 189.86 83.48 188.5 82.2 C186.94 80.73 184.47 81.22 182.5 81.16 C180.62 81.11 178.33 81.4 176.5 81 C175.97 80.89 175.53 80.5 175 80.39 C172.71 79.91 169.84 80.33 167.5 80.31 C165 80.28 150.71 80.59 149.5 80.1 C147.38 79.24 148.34 76 148 76 C147.99 76 147.61 79 147.61 79 C147.25 79.5 146.63 79.79 146.05 80 C144.05 80.71 131.09 80.1 128 80.1 C120 80.1 112 80.1 104 80.1 C103.08 80.1 96.87 80.37 96.5 80.1 C95.21 79.15 95.66 75.38 95.59 74 C95.56 73.5 95.54 73 95.51 72.5 C95.51 72.41 95.53 72.15 95.5 72.23 Z"},"spiky":{"cx":120,"cy":157.1,"dx":37.1,"w":20.1,"h":38,"round":32.1,"my":178.6,"clip":"M72.5 118.96 C69.66 119.28 57.61 120.01 56.04 121.5 C54.65 122.82 51.4 134.01 50.72 136.5 C48.42 144.96 47.55 153.28 47.02 162 C46.94 163.31 46.49 165.21 46.74 166.5 C47.03 167.99 47.8 168.18 48.44 169.5 C50.01 172.72 49.95 176.7 51.5 179.95 C52.25 181.52 53.41 183.02 54.33 184.5 C55.55 186.46 56.56 188.47 58.31 190 C60.6 192 63.15 194.43 65.75 196 C79.31 204.14 102.94 204.4 118.5 204.4 C131.5 204.4 144.65 204.22 157.5 201.94 C164.27 200.74 174.93 197.25 179.92 192.5 C182.49 190.06 185.39 184.73 188.2 183 C188.91 182.57 190.75 182.28 191.5 182.23 C192 182.2 192.64 182.63 193 182.29 C193.59 181.73 193.44 175.61 193.46 174.5 C193.6 167.56 193.46 160.38 192.42 153.5 C192.1 151.45 191.43 149.58 191.17 147.5 C190.14 139.24 188.94 135.8 186.41 128 C185.76 126 185.29 122.09 183.5 120.77 C181.28 119.14 177.11 119.5 174.5 119.27 C167.48 118.65 160.55 118.6 153.5 118.6 C136 118.6 118.5 118.6 101 118.6 C91.59 118.6 81.85 117.88 72.5 118.96 Z"},"seira":{"cx":120,"cy":106,"dx":37,"w":20,"h":38,"round":32,"my":128,"clip":"M47.5 66 C46.49 67.05 47 74.75 47 76.5 C47 86 47 95.5 47 105 C47 112.61 46.78 120.1 48.86 127.5 C50.19 132.27 52.24 136.83 54.92 141 C72.07 167.7 97.24 164 124.5 164 C146.44 164 163.64 165.49 180 147.63 C196.1 130.05 193 108.86 193 87 C193 82 193 77 193 72 C193 71.02 193.23 66.57 192.5 66 C192.27 65.82 187.16 66 186.5 66 C181 66 175.5 66 170 66 C148 66 126 66 104 66 C90 66 76 66 62 66 C58.5 66 55 66 51.5 66 C51.2 66 47.53 65.97 47.5 66 Z"},"messy":{"cx":120,"cy":145.5,"dx":37.1,"w":20.1,"h":38,"round":32.1,"my":167,"clip":"M148.5 103.94 C148.61 106.36 150.35 107.78 150.95 110 C151.65 112.57 150.68 112.97 152.42 117.5 C152.59 117.96 154.14 121.74 154.02 122 C153.31 123.47 146.41 121.21 145 120.86 C140.36 119.72 135.73 118.08 131.5 115.8 C130.08 115.03 128.54 113.55 127 113.1 C125.61 112.69 123.92 113.65 122.5 113.7 C121.6 113.73 118.37 112.65 118 112.89 C116.34 113.96 119.03 116.51 117.35 117.5 C115.43 118.63 107.22 114.2 105 113.74 C101.49 113.01 90.01 114.09 85.5 113.99 C83.95 113.95 82.52 113.32 81 113.27 C79.04 113.19 66.6 119.93 64.5 121.41 C63.85 121.87 59.72 124.87 59 124.35 C57.65 123.39 60.24 113.66 60.63 112 C60.87 111 62.13 109.66 61.34 109 C60.65 108.42 57.45 112.87 57 113.5 C53.83 117.91 51.78 123.21 50.9 128.5 C50.55 130.6 49.97 132.49 49.46 134.5 C49.15 135.73 48.5 146.43 48.6 148 C48.68 149.27 50.15 159.64 50.1 160 C50.02 160.56 49.42 160.95 49.3 161.5 C48.41 165.46 54.96 176.38 57.33 179.5 C63.79 187.99 76.23 192.58 86.5 193.97 C90.97 194.57 95.51 194.75 100 195.26 C105.82 195.91 97.02 194.86 103 196.01 C106.51 196.69 114.06 196.13 118 196.17 C131.14 196.31 144.17 195.9 157 193.15 C165.39 191.36 174.19 188.76 180.81 183 C188.06 176.7 191.6 166.67 193.04 157.5 C193.61 153.85 195.29 150.73 195.4 147 C195.47 144.64 195.05 135.14 194.1 133.5 C193.51 132.48 192.43 131.83 191.6 131 C184.64 124.04 175.06 121.23 166.5 116.89 C162.46 114.84 157.91 112.48 154.5 109.51 C152.98 108.19 151.3 106.94 149.89 105.5 C149.4 105 148.47 103.24 148.5 103.94 Z"},"bob":{"cx":120,"cy":124.2,"dx":37.1,"w":20.1,"h":38,"round":32.1,"my":145.7,"clip":"M95 80.99 C94.93 83.21 94.94 89.92 93.5 91.48 C91.95 93.15 85.36 92.5 83 92.54 C79.54 92.59 75.94 92.35 72.5 92.68 C69.65 92.96 72.35 92.92 69.5 93.35 C67.56 93.64 65.46 93.51 63.5 93.59 C61.51 93.68 59.49 93.69 57.5 93.84 C56.48 93.92 55.52 94.38 54.5 94.44 C53.99 94.48 53.47 94.01 53 94.21 C51.65 94.78 49.86 105.55 49.45 107.5 C48.63 111.37 46.43 129.79 47.02 133 C47.12 133.54 47.49 133.99 47.67 134.5 C47.84 134.99 47.99 135.49 48.08 136 C48.44 137.97 48.28 140.03 48.63 142 C49.43 146.6 50.89 151.43 53.22 155.5 C61.45 169.91 77.19 173.86 92.5 175.04 C96.32 175.34 100.79 175.05 104.5 175.97 C105.03 176.1 105.47 176.49 106 176.6 C107.34 176.88 134.61 176.88 136 176.6 C136.52 176.5 136.95 176.13 137.47 176 C141.4 175.04 149.58 175.02 154 174.66 C166.15 173.69 177.79 169.49 185.5 159.61 C189.63 154.32 193.17 142.23 193.88 135.5 C194.21 132.3 193.75 119.36 193.32 116 C193.12 114.43 192.4 113.09 192.22 111.5 C192.01 109.57 192.24 107.39 191.79 105.5 C191.54 104.45 189.41 95.15 189.32 95 C189.01 94.5 188.54 94.08 188 93.85 C186.79 93.32 180.71 93.51 179 93.43 C177.41 93.36 176.05 92.9 174.5 92.68 C169.68 92.02 164.37 92.57 159.5 92.51 C158.18 92.49 151.22 92.76 150.5 92.4 C148.52 91.41 148.92 88.34 148.79 86.5 C148.79 86.5 148.62 83.5 148.59 83.5 C148.26 83.5 148.27 86.45 148.27 86.5 C148.23 88.25 148.31 91.24 146.5 92.33 C145.53 92.92 139 92.45 137.5 92.46 C128 92.51 118.5 92.57 109 92.63 C106.24 92.65 99.3 93.35 97 92.53 C94.29 91.56 95.13 87.82 95.09 85.5 C95.07 84.5 95.05 83.5 95.03 82.5 C95.02 82 95.02 80.48 95 80.99 Z"}};
  const INK = "#e11d48";
  const POSES = {
    working: { gy: 0.5 },
    alert: { wide: 1 },
    curious: { gx: 0.6, gy: -0.6 },
    happy: { smile: 1.5 },
    sad: { gy: 0.6, smile: -0.8 },
    sleep: { shut: 0.92, smile: 0.4 },
  };
  const n1 = (v) => Math.round(v * 10) / 10;

  function face(style, mood) {
    const e = FACES[style];
    const p = Object.assign({ gx: 0, gy: 0, wide: 0, shut: 0, smile: 1 }, POSES[mood]);
    const ax = Math.abs(p.gx);
    const spread = e.dx * (1 - 0.1 * ax) * (1 + 0.14 * p.wide);
    const cx = e.cx + p.gx * e.dx * 1.05;
    const cy = e.cy + p.gy * 16 - p.wide * e.h * 0.42;
    let w = e.w * (1 + 0.05 * ax);
    let h = e.h * (1 - 0.3 * ax);
    w += (e.round - w) * p.wide;
    h += (e.round * 1.08 - h) * p.wide;
    h *= 1 - 0.9 * p.shut;
    const tilt = -p.gx * 18 * (1 - p.wide);
    const eyes = [-1, 1]
      .map((side) => {
        const k = 1 - 0.1 * Math.max(0, side * -p.gx);
        const ew = w * k;
        const eh = Math.max(h * k, 0.5);
        const x = cx + side * spread;
        return `<rect x="${n1(x - ew / 2)}" y="${n1(cy - eh / 2)}" width="${n1(ew)}" height="${n1(eh)}" rx="${n1(Math.min(ew, eh) / 2)}" transform="rotate(${n1(tilt)} ${n1(x)} ${n1(cy)})"/>`;
      })
      .join("");
    const mw = e.dx * 0.46;
    const half = mw * (1 - 0.25 * ax);
    const depth = mw * 0.5 * p.smile;
    const my = e.my + p.gy * 10;
    const y0 = n1(my - depth * 0.35);
    const smile =
      p.wide < 1
        ? `<path d="M${n1(cx - half)} ${y0}Q${n1(cx)} ${n1(my + depth)} ${n1(cx + half)} ${y0}" fill="none" stroke="${INK}" stroke-width="${n1(e.w * 0.36)}" stroke-linecap="round" opacity="${n1(1 - p.wide)}" transform="rotate(${n1(tilt * 0.6)} ${n1(cx)} ${n1(my)})"/>`
        : "";
    const open =
      p.wide > 0
        ? `<ellipse cx="${n1(cx)}" cy="${n1(my + mw * 0.1)}" rx="${n1(mw * 0.5 * p.wide)}" ry="${n1(mw * 0.62 * p.wide)}" fill="${INK}" opacity="${p.wide}"/>`
        : "";
    const body = `translate(120 150) rotate(${n1(-p.gx * 9)}) scale(${1 - 0.06 * p.wide} ${1 + 0.09 * p.wide}) translate(-120 -150)`;
    return { eyes, mouth: smile + open, body };
  }

  function head(bot, mood, size, cls, ringColor) {
    const b = BOTS[bot];
    const src = new URL(`assets/heads/${b.hair}.webp`, document.baseURI).href;
    const rc = ringColor ? `;--rc:${ringColor}` : "";
    const f = face(b.hair, mood || "idle");
    return `<span class="hd ${cls || ""}" style="--s:${size}px;--c:${b.color}${rc}"><svg viewBox="-14 -14 268 268" aria-hidden="true"><g transform="${f.body}"><image href="${src}" width="240" height="240"/><g clip-path="url(#hc-${b.hair})"><g fill="${INK}" filter="url(#hd-glow)">${f.eyes}</g></g><g filter="url(#hd-glow)">${f.mouth}</g></g></svg></span>`;
  }

  function heads(list, size) {
    return `<span class="hds">${list.map(([b, m]) => head(b, m, size)).join("")}</span>`;
  }

  function ring(size, frac, color, inner, sw) {
    sw = sw || 2.5;
    const r = (size - sw) / 2;
    const c = 2 * Math.PI * r;
    const h = size / 2;
    return `<span class="ringwrap" style="width:${size}px;height:${size}px"><svg viewBox="0 0 ${size} ${size}" aria-hidden="true"><circle cx="${h}" cy="${h}" r="${r}" fill="none" stroke="currentColor" stroke-opacity=".2" stroke-width="${sw}"/><circle cx="${h}" cy="${h}" r="${r}" fill="none" stroke="${color || "currentColor"}" stroke-width="${sw}" stroke-linecap="round" stroke-dasharray="${(c * frac).toFixed(1)} ${c.toFixed(1)}"/></svg>${inner || ""}</span>`;
  }

  const fmt = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
  const up = (s) => `<time data-t="${s}">${fmt(s)}</time>`;
  const down = (s) => `<time data-t="${s}" data-down>${fmt(s)}</time>`;
  const wave = '<span class="wave" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></span>';
  const steps = (done, total) =>
    Array.from({ length: total }, (_, i) => `<i class="${i < done ? "d" : i === done ? "a" : ""}"></i>`).join("");
  const pill = (label, go, cls, icon) =>
    `<button type="button" class="pill ${cls || ""}" data-go="${go}"${cls && cls.includes("ico") ? ` aria-label="${label}"` : ""}>${icon ? ic(icon) : ""}${cls && cls.includes("ico") ? "" : label}</button>`;
  const act = (label, go, cls, icon) => `<button type="button" class="${cls || ""}" data-go="${go}">${icon ? ic(icon) : ""}${label}</button>`;

  const SCENES = [
    {
      id: "working", label: "Bekerja", bot: "atlas", mood: "working", acc: ACC.working, cw: 224,
      ct: () => `${ring(16, 0.5)}${up(222)}`,
      min: () => ring(31, 0.5, ACC.working, head("atlas", "working", 23)),
      trail: () => `${ic("terminal")}${up(222)}`,
      body: () =>
        `<div class="ie-line">Running the test suite</div><div class="steps">${steps(2, 5)}</div><div class="ie-sub"><span><b>Step 3 of 5</b></span><span>2 files changed</span></div><div class="btns">${pill("Stop", "done", "", "stop")}${pill("Open chat", "working")}</div>`,
      chip: () => `${ic("seira")}${up(222)}`,
      an: () => ({
        sub: "Atlas", time: up(222), title: "Atlas is working", text: "Running the test suite\nStep 3 of 5",
        prog: `<div class="an-p">${steps(2, 5)}${head("atlas", "working", 22)}</div>`,
        actions: act("Stop", "done", "", "stop") + act("Open chat", "working"),
      }),
      idea: "Island menunjukkan langkah, alat, dan lama kerja. Kamu tahu bot sampai di mana tanpa membuka app.",
      isi: "Kepala bot, cincin langkah, jam berjalan, kalimat bot, 5 segmen langkah, jumlah berkas.",
      aksi: "Stop, Open chat.",
      data: [["ok", "nama, kalimat, jam mulai"], ["new", "nomor langkah, jumlah langkah, alat, jumlah berkas"]],
      ios: "<code>Text(timerInterval:)</code> untuk jam. Segmen: <code>HStack</code> berisi <code>Capsule</code>. Stop: <code>Button(intent:)</code> dengan <code>LiveActivityIntent</code>.",
      and: "<code>ProgressStyle</code> dengan <code>setProgressSegments</code> dan <code>setProgressTrackerIcon</code> (kepala bot). Chip: <code>setUsesChronometer(true)</code>.",
    },
    {
      id: "approval", label: "Minta izin", bot: "atlas", mood: "alert", acc: ACC.needs, cw: 234,
      ct: () => `${ic("hand")}Allow?`,
      min: () => ring(31, 1, ACC.needs, head("atlas", "alert", 23)),
      trail: () => `${ic("terminal")}Command`,
      body: () =>
        `<div class="ie-sub"><b>Run a command in the shop folder</b></div><div class="ie-cmd">npm run deploy -- --env=staging</div><div class="btns">${pill("Deny", "working")}${pill("Allow once", "working", "pri", "lock")}${pill("Open chat", "approval")}</div>`,
      chip: () => `${ic("hand")}Allow?`,
      an: () => ({
        sub: "Atlas", time: "now", title: "Atlas needs your OK",
        text: "Run a command in the shop folder\n<code>npm run deploy -- --env=staging</code>",
        large: head("atlas", "alert", 40),
        actions: act("Deny", "working") + act("Allow once", "working", "pri", "lock") + act("Open chat", "approval"),
      }),
      idea: "Island membuka sendiri saat bot butuh izin. Teks perintah tampil utuh, lalu kamu menjawab di tempat.",
      isi: "Kepala bot dengan mata waspada, jenis izin, judul, teks perintah utuh.",
      aksi: "Deny, Allow once (butuh HP tidak terkunci), Open chat.",
      data: [["ok", "judul, teks perintah, jenis izin"]],
      ios: "<code>AlertConfiguration</code> saat update supaya island membuka. <code>ViewThatFits</code> menyembunyikan Allow once kalau teks tidak muat. <code>authenticationPolicy = .requiresAuthentication</code>.",
      and: "<code>BigTextStyle</code>, 3 aksi. Allow once: <code>setAuthenticationRequired(true)</code>. Chip: <code>setShortCriticalText(\"Allow?\")</code>.",
    },
    {
      id: "question", label: "Bertanya", bot: "mira", mood: "curious", acc: ACC.needs, cw: 224,
      ct: () => `${ic("bubble")}Asks`,
      min: () => ring(31, 1, ACC.needs, head("mira", "curious", 23)),
      trail: () => `${ic("bubble")}Question`,
      body: () =>
        `<div class="ie-line">Which logo goes on the invoice?</div><div class="btns">${pill("The new one", "working")}${pill("Keep the old", "working")}${pill("Answer by voice", "call", "ico", "mic")}</div>`,
      chip: () => `${ic("bubble")}Asks`,
      an: () => ({
        sub: "Mira", time: "now", title: "Mira has a question", text: "Which logo goes on the invoice?",
        large: head("mira", "curious", 40),
        actions: act("The new one", "working") + act("Keep the old", "working") + act("Reply", "question", "", "keyboard"),
      }),
      idea: "Pilihan jawaban bot menjadi tombol. Satu ketukan menjawab, dan bot langsung lanjut.",
      isi: "Kepala bot dengan mata penasaran, pertanyaan, sampai 2 pilihan jawaban.",
      aksi: "Pilih jawaban, jawab dengan suara, atau buka chat.",
      data: [["ok", "pertanyaan, pilihan jawaban"]],
      ios: "Setiap pilihan: <code>Button(intent:)</code>. Mic: <code>Link</code> ke chat dengan dikte aktif.",
      and: "Aksi 1 dan 2 berisi pilihan. Aksi 3: <code>RemoteInput</code> untuk mengetik di notifikasi.",
    },
    {
      id: "done", label: "Selesai", bot: "atlas", mood: "happy", acc: ACC.done, cw: 216,
      ct: () => `${ic("check")}4m`,
      min: () => ring(31, 1, ACC.done, head("atlas", "happy", 23)),
      trail: () => `${ic("check")}4m 12s`,
      body: () =>
        `<div class="ie-line">Deployed to staging. All 42 tests passed.</div><div class="ie-sub"><span>3 files changed</span></div><div class="btns">${pill("Read reply", "working", "pri")}${pill("Next task", "working", "", "plus")}</div>`,
      chip: () => `${ic("check")}Done`,
      an: () => ({
        sub: "Atlas", time: "now", title: "Atlas finished", text: "Deployed to staging. All 42 tests passed.",
        large: head("atlas", "happy", 40),
        actions: act("Read reply", "working", "pri") + act("Next task", "working", "", "plus"),
      }),
      idea: "Hasil kerja tampil sebagai satu kalimat dan lama kerja. Tugas berikutnya tinggal satu ketukan.",
      isi: "Kepala bot tersenyum, lama kerja, ringkasan satu kalimat, jumlah berkas.",
      aksi: "Read reply, Next task.",
      data: [["ok", "state selesai, kalimat terakhir"], ["new", "lama kerja akhir, jumlah berkas"]],
      ios: "<code>activity.end(_:dismissalPolicy: .after(date))</code> supaya hilang sendiri. <code>contentTransition(.numericText())</code> pada angka.",
      and: "Notifikasi biasa (tidak ongoing) dengan <code>setTimeoutAfter</code>. Chip hilang saat notifikasi selesai.",
    },
    {
      id: "failed", label: "Gagal", bot: "juno", mood: "sad", acc: ACC.failed, cw: 232,
      ct: () => `${ic("alert")}Failed`,
      min: () => ring(31, 1, ACC.failed, head("juno", "sad", 23)),
      trail: () => `${ic("alert")}Step 4`,
      body: () =>
        `<div class="ie-line">The build stopped. The disk on your computer is full.</div><div class="btns">${pill("Try again", "working", "pri", "retry")}${pill("Open chat", "failed")}</div>`,
      chip: () => `${ic("alert")}Failed`,
      an: () => ({
        sub: "Juno", time: "now", title: "Juno could not finish", text: "The build stopped. The disk on your computer is full.",
        large: head("juno", "sad", 40),
        actions: act("Try again", "working", "pri", "retry") + act("Open chat", "failed"),
      }),
      idea: "Island menyebut apa yang gagal dengan bahasa biasa, lalu memberi satu jalan keluar.",
      isi: "Kepala bot sedih, langkah yang gagal, sebab dalam satu kalimat.",
      aksi: "Try again, Open chat.",
      data: [["ok", "state gagal"], ["new", "sebab singkat, langkah yang gagal, aksi ulang"]],
      ios: "<code>keylineTint</code> merah. Try again: <code>Button(intent:)</code>.",
      and: "<code>BigTextStyle</code>, 2 aksi. Chip: <code>setShortCriticalText(\"Failed\")</code>.",
    },
    {
      id: "call", label: "Live call", bot: "seira", mood: "listen", acc: ACC.done, cw: 234,
      ct: () => `${wave}${up(67)}`,
      min: () => ring(31, 1, ACC.done, head("seira", "listen", 23)),
      trail: () => `${wave}${up(67)}`,
      body: () =>
        `<div class="ie-line"><em>Seira</em> Moved your 3 pm meeting to Friday. Anything else?</div><div class="btns">${pill("Mute", "call", "", "mic-off")}${pill("Speaker", "call", "", "speaker")}${pill("End", "working", "bad", "hangup")}</div>`,
      chip: () => `${ic("phone")}${up(67)}`,
      chipCls: "call",
      an: () => ({
        sub: "Live call", time: up(67), call: true, title: "Seira", text: "Moved your 3 pm meeting to Friday. Anything else?",
        large: head("seira", "listen", 44),
        actions: act("Hang up", "working", "bad", "hangup") + act("Mute", "call", "", "mic-off") + act("Speaker", "call", "", "speaker"),
      }),
      idea: "Panggilan dengan Chief of Staff tetap terlihat saat kamu pindah app. Kalimat terakhir bot tampil sebagai caption.",
      isi: "Kepala bot mendengar, gelombang suara, lama panggilan, caption kalimat terakhir.",
      aksi: "Mute, Speaker, End.",
      data: [["ok", "lama panggilan, state mute dan speaker"], ["new", "caption di luar app"]],
      ios: "Live Activity kedua khusus panggilan. Gelombang berubah saat update, bukan dari audio langsung. Tombol: <code>LiveActivityIntent</code>.",
      and: "<code>CallStyle.forOngoingCall</code> dari <code>LiveCallService</code>. Chip hijau dengan jam berjalan.",
    },
    {
      id: "crew", label: "Banyak bot", bot: "mira", mood: "curious", acc: ACC.needs, cw: 234,
      cl: () => heads([["mira", "curious"], ["atlas", "working"], ["juno", "working"]], 22),
      ct: () => `<span class="dots3"><i style="background:${ACC.needs}"></i><i style="background:${ACC.working}"></i><i style="background:${ACC.working}"></i></span>`,
      min: () => ring(31, 1, ACC.needs, head("mira", "curious", 23)),
      lead: () => heads([["mira", "curious"], ["atlas", "working"], ["juno", "working"]], 26),
      trail: () => `<span class="dots3"><i style="background:${ACC.needs}"></i><i style="background:${ACC.working}"></i><i style="background:${ACC.working}"></i></span>`,
      body: () =>
        `<div class="crew"><a href="#" data-go="question">${head("mira", "curious", 24)}<span class="n">Mira</span><span class="t">Which logo goes on the invoice?</span><span class="r" style="color:${ACC.needs}">Answer</span></a><a href="#" data-go="working">${head("atlas", "working", 24)}<span class="n">Atlas</span><span class="t">Running the test suite</span><span class="r" style="color:${ACC.working}">${up(222)}</span></a><a href="#" data-go="working">${head("juno", "working", 24)}<span class="n">Juno</span><span class="t">Writing the weekly report</span><span class="r" style="color:${ACC.working}">${up(58)}</span></a></div>`,
      chip: () => `${ic("bubble")}Asks`,
      an: () => ({
        sub: "Mira", time: "now", title: "Mira has a question",
        text: "Which logo goes on the invoice?\nAtlas: Running the test suite\nJuno: Writing the weekly report",
        large: heads([["mira", "curious"], ["atlas", "working"], ["juno", "working"]], 30),
        actions: act("Answer Mira", "question", "pri") + act("Open Seira", "crew"),
      }),
      idea: "Saat beberapa bot aktif, island menumpuk kepalanya. Bot yang paling butuh kamu selalu di depan.",
      isi: "Sampai 3 kepala, satu titik warna per bot, satu baris per bot dengan kalimat dan jam.",
      aksi: "Ketuk satu baris untuk membuka chat bot itu.",
      data: [["ok", "daftar bot, state, kalimat, jam mulai"]],
      ios: "Satu activity, isi berupa array 3 bot (di bawah batas 4 KB). Setiap baris: <code>Link(destination:)</code>.",
      and: "<code>BigTextStyle</code> berisi bot utama, lalu satu baris per bot lain. Tanpa layout kustom, jadi tetap bisa dipromosikan.",
    },
    {
      id: "routine", label: "Routine", bot: "nox", mood: "idle", acc: ACC.soon, cw: 224,
      ct: () => `${ic("clock")}${down(45)}`,
      min: () => ring(31, 0.75, ACC.soon, head("nox", "idle", 23)),
      trail: () => `${ic("clock")}${down(45)}`,
      body: () =>
        `<div class="ie-line">Morning brief starts soon</div><div class="ie-sub"><span>Every weekday at 09:00</span></div><div class="btns">${pill("Skip this run", "working", "", "skip")}${pill("Run now", "working", "pri", "play")}</div>`,
      chip: () => `${ic("clock")}${down(45)}`,
      an: () => ({
        sub: "Nox", time: down(45), title: "Morning brief starts soon", text: "Every weekday at 09:00",
        prog: '<div class="an-p time" style="--left:25%"><i></i></div>',
        actions: act("Skip this run", "working", "", "skip") + act("Run now", "working", "pri", "play"),
      }),
      idea: "Satu menit sebelum routine jalan, island menghitung mundur. Kamu bisa lewati atau jalankan sekarang.",
      isi: "Kepala bot, hitung mundur, nama routine, jadwal.",
      aksi: "Skip this run, Run now.",
      data: [["chk", "jadwal routine ada di app; perlu cek push sebelum mulai"], ["new", "aksi lewati satu kali"]],
      ios: "<code>Text(timerInterval:countsDown:)</code>. Mulai lewat push-to-start (iOS 17.2). Warna ungu adalah usulan warna baru untuk \"terjadwal\".",
      and: "<code>ProgressStyle</code> dengan <code>setChronometerCountDown(true)</code>. Chip menampilkan hitung mundur.",
    },
  ];

  const byId = (id) => SCENES.find((s) => s.id === id) || SCENES[0];

  /* ---- renderers ---- */
  function compact(s) {
    return `<div class="icp" style="--cw:${s.cw}px"><span class="cl">${s.cl ? s.cl() : head(s.bot, s.mood, 25)}</span><span class="ct">${s.ct()}</span></div>`;
  }
  function expanded(s) {
    const lead = s.lead ? s.lead() : `${head(s.bot, s.mood, 30)}<span class="ie-name">${BOTS[s.bot].name}</span>`;
    return `<div class="ie"><div class="ie-top"><div class="ie-lead">${lead}</div><div class="ie-trail">${s.trail()}</div></div><div class="ie-body">${s.body()}</div></div>`;
  }
  function chip(s) {
    return `<button type="button" class="chip ${s.chipCls || ""}" style="--acc-s:${s.acc}" aria-label="Open the notification">${s.chip()}</button>`;
  }
  function android(s) {
    const a = s.an();
    const body = a.call
      ? `<div class="an-call">${a.large}<div class="an-tx"><div class="an-t">${a.title}</div><div class="an-x">${a.text}</div></div></div>`
      : `<div class="an-b"><div class="an-tx"><div class="an-t">${a.title}</div><div class="an-x">${a.text}</div></div>${a.large || ""}</div>`;
    return `<div class="an" style="--acc-s:${s.acc}"><div class="an-h"><i class="an-ico">${ic("seira")}</i><span>Seira</span><span>&middot;</span><span>${a.sub}</span><span>&middot;</span><span>${a.time}</span><span class="car">${ic("chevron")}</span></div>${body}${a.prog || ""}<div class="an-a">${a.actions}</div></div>`;
  }
  const dataTags = (s) =>
    s.data
      .map(([k, t]) => `<span class="data-${k}">${k === "ok" ? "Sudah ada" : k === "new" ? "Perlu data baru" : "Perlu cek"}</span> ${t}`)
      .join("<br>");

  /* ---- timers ---- */
  function startClock() {
    if (startClock.on) return;
    startClock.on = true;
    setInterval(() => {
      document.querySelectorAll("time[data-t]").forEach((el) => {
        const start = Number(el.dataset.t);
        let cur = Number(el.dataset.c != null ? el.dataset.c : start);
        if (el.hasAttribute("data-down")) cur = cur <= 0 ? start : cur - 1;
        else cur += 1;
        el.dataset.c = cur;
        el.textContent = fmt(cur);
      });
    }, 1000);
  }

  /* ---- interactive stage ---- */
  const IOS_FORMS = [["compact", "Compact"], ["expanded", "Expanded"], ["minimal", "Minimal"], ["lock", "Lock screen"]];
  const AND_FORMS = [["chip", "Chip"], ["shade", "Notifikasi"], ["lock", "Lock screen"]];
  const sbRight = '<span class="sb-r"><i class="sb-sig"></i><i class="sb-wifi"></i><i class="sb-bat"></i></span>';
  const tiles = (n) => Array.from({ length: n }, () => "<i></i>").join("");

  function mountStage(root) {
    injectExtra();
    root.innerHTML = `
      <div class="scenes" role="group" aria-label="Adegan">
        ${SCENES.map((s) => `<button type="button" class="scene-b" data-scene="${s.id}" aria-pressed="false" style="--sc:${s.acc}"><i></i>${s.label}</button>`).join("")}
        <button type="button" class="scene-b play" data-play style="--sc:var(--g-acc)">${ic("play")}Putar cerita</button>
      </div>
      <div class="stage2">
        <div class="stage-col">
          <div class="plat"><b>iPhone</b> Dynamic Island dan Live Activity</div>
          <div class="seg" role="group" aria-label="Bentuk di iPhone">${IOS_FORMS.map(([k, l]) => `<button type="button" data-ios="${k}" aria-pressed="false">${l}</button>`).join("")}</div>
          <div class="ph ios" data-ui="dark" data-ui-global data-form="compact">
            <div class="ph-screen">
              <div class="view v-home on"><div class="ph-home"><div class="apps" style="top:92px">${tiles(16)}</div><div class="dock">${tiles(3)}<i class="me">${ic("seira")}</i></div></div></div>
              <div class="view v-lock"><div class="lock-clock"><small>Saturday, 28 November</small><b>9:41</b></div><div class="lock-la"><div class="la"></div></div><div class="lock-btns"><i></i><i></i></div></div>
              <div class="sb"><span class="sb-time">9:41</span>${sbRight}</div>
              <div class="isl" data-form="compact"><span class="lens"></span><div class="l l-c"></div><div class="l l-e"></div><div class="l l-m"><div class="im-other">${ic("timer")}${ring(14, 0.62, "currentColor", "", 2.2)}</div></div></div>
              <div class="isl-min"></div>
              <div class="homebar"></div>
            </div>
          </div>
        </div>
        <div class="stage-col">
          <div class="plat"><b>Android 16</b> Live Update</div>
          <div class="seg" role="group" aria-label="Bentuk di Android">${AND_FORMS.map(([k, l]) => `<button type="button" data-and="${k}" aria-pressed="false">${l}</button>`).join("")}</div>
          <div class="ph and" data-ui="dark" data-ui-global data-form="chip">
            <div class="ph-screen">
              <div class="view v-home on"><div class="ph-home"><div class="chip-zoom"></div><div class="apps" style="top:330px">${tiles(12)}</div><div class="gsearch"></div></div></div>
              <div class="view v-shade"><div class="shade"><div class="shade-top"><b>9:41</b><span>Sat, 28 Nov</span></div><div class="qs"><span>${ic("wifi")}Internet</span><span>${ic("bt")}Bluetooth</span><span class="on">${ic("mic")}Talk to Seira</span><span>${ic("torch")}Flashlight</span></div><div class="shade-n"></div><div class="an quiet"><div class="an-h"><i class="an-ico" style="background:#8a8d94">${ic("calendar")}</i><span>Calendar</span><span>&middot;</span><span>12 min</span></div><div class="an-t">Design review at 10:00</div></div></div></div>
              <div class="view v-lock"><div class="lock-clock"><b>9:41</b><small>Sat, 28 Nov</small></div><div class="lock-la"></div></div>
              <div class="sb"><span class="sb-time"><span>9:41</span><span class="chip-slot"></span></span>${sbRight}</div>
              <i class="cam"></i>
              <div class="homebar"></div>
            </div>
          </div>
        </div>
      </div>
      <p class="now-note" aria-live="polite"></p>`;

    const ios = root.querySelector(".ph.ios");
    const and = root.querySelector(".ph.and");
    const isl = ios.querySelector(".isl");
    const note = root.querySelector(".now-note");
    let scene = SCENES[0];
    let iosForm = "compact";
    let andForm = "chip";
    let story = [];

    function paintForms() {
      ios.dataset.form = iosForm;
      isl.dataset.form = iosForm === "lock" ? "idle" : iosForm;
      ios.querySelector(".v-home").classList.toggle("on", iosForm !== "lock");
      ios.querySelector(".v-lock").classList.toggle("on", iosForm === "lock");
      and.dataset.form = andForm;
      and.querySelector(".v-home").classList.toggle("on", andForm === "chip");
      and.querySelector(".v-shade").classList.toggle("on", andForm === "shade");
      and.querySelector(".v-lock").classList.toggle("on", andForm === "lock");
      root.querySelectorAll("[data-ios]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.ios === iosForm)));
      root.querySelectorAll("[data-and]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.and === andForm)));
    }

    function paintScene() {
      const s = scene;
      isl.style.setProperty("--acc-s", s.acc);
      isl.style.setProperty("--cw", s.cw + "px");
      isl.querySelector(".l-c").innerHTML = compact(s);
      const le = isl.querySelector(".l-e");
      le.innerHTML = expanded(s);
      const box = le.firstElementChild;
      const eh = Math.round((box.offsetHeight / box.offsetWidth) * 368);
      isl.style.setProperty("--eh", eh + "px");
      if (eh > 160) console.warn("Expanded island is taller than 160 pt:", s.id, eh);
      ios.querySelector(".isl-min").innerHTML = s.min();
      const la = ios.querySelector(".la");
      la.style.setProperty("--acc-s", s.acc);
      la.innerHTML = expanded(s);
      and.querySelector(".chip-slot").innerHTML = chip(s);
      and.querySelector(".chip-zoom").innerHTML = chip(s);
      and.querySelector(".shade-n").innerHTML = android(s);
      and.querySelector(".v-lock .lock-la").innerHTML = android(s);
      root.querySelectorAll("[data-scene]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.scene === s.id)));
      note.innerHTML = `<b>${s.label}.</b> ${s.idea}`;
    }

    function setScene(id, opts) {
      scene = byId(id);
      if (opts && opts.ios) iosForm = opts.ios;
      if (opts && opts.and) andForm = opts.and;
      paintScene();
      paintForms();
    }

    function stopStory() {
      story.forEach(clearTimeout);
      story = [];
      root.querySelector("[data-play]").setAttribute("aria-pressed", "false");
    }

    function playStory() {
      stopStory();
      root.querySelector("[data-play]").setAttribute("aria-pressed", "true");
      const tapPri = () => {
        const b = isl.querySelector(".l-e .pill.pri");
        const a = and.querySelector(".shade-n .an-a .pri");
        [a, b].forEach((el) => el && el.classList.add("tap"));
      };
      const beats = [
        [0, () => setScene("working", { ios: "compact", and: "chip" })],
        [2600, () => setScene("approval", { ios: "expanded", and: "shade" })],
        [5600, tapPri],
        [6100, () => setScene("working", { ios: "compact", and: "chip" })],
        [8800, () => setScene("done", { ios: "expanded", and: "shade" })],
        [12200, () => setScene("done", { ios: "compact", and: "chip" })],
        [13400, stopStory],
      ];
      story = beats.map(([t, fn]) => setTimeout(fn, t));
    }

    root.addEventListener("click", (e) => {
      const t = e.target.closest("[data-scene],[data-ios],[data-and],[data-play],[data-go],.chip,.isl");
      if (!t || !root.contains(t)) return;
      if (t.matches("[data-play]")) return playStory();
      stopStory();
      if (t.matches("[data-scene]")) return setScene(t.dataset.scene);
      if (t.matches("[data-ios]")) { iosForm = t.dataset.ios; return paintForms(); }
      if (t.matches("[data-and]")) { andForm = t.dataset.and; return paintForms(); }
      if (t.matches("[data-go]")) {
        e.preventDefault();
        S && S.tick(t);
        const inIos = ios.contains(t);
        const same = t.dataset.go === scene.id;
        return setTimeout(() => setScene(t.dataset.go, same ? null : inIos ? { ios: iosForm === "lock" ? "lock" : "compact" } : { and: andForm === "lock" ? "lock" : "chip" }), 170);
      }
      if (t.matches(".chip")) { andForm = "shade"; return paintForms(); }
      if (t.matches(".isl")) {
        if (iosForm === "lock") return;
        iosForm = iosForm === "expanded" ? "compact" : "expanded";
        return paintForms();
      }
    });

    setScene("working");
    startClock();
    return { setScene, playStory };
  }

  /* ---- static catalog ---- */
  function mountCatalog(root) {
    injectExtra();
    root.innerHTML = SCENES.map(
      (s, i) => `
      <article class="concept is-wide" id="i${i + 1}">
        <div class="concept-stage wallp">
          <div class="pieces">
            <div class="piece"><div class="piece-row"><div class="pc pc-c" style="--cw:${s.cw}px;--acc-s:${s.acc}"><span class="lens"></span>${compact(s)}</div><div class="pc pc-m">${s.min()}</div></div><span>iOS compact dan minimal</span>
              <div class="piece-row" style="margin-top:14px">${chip(s)}</div><span>Android chip</span></div>
            <div class="piece"><div class="pc pc-e" style="--acc-s:${s.acc}"><span class="lens"></span>${expanded(s)}</div><span>iOS expanded</span></div>
            <div class="piece">${android(s)}<span>Android notifikasi</span></div>
          </div>
        </div>
        <div class="concept-meta">
          <div class="concept-top"><span class="concept-no">I${i + 1}</span><h3>${s.label}</h3></div>
          <p class="concept-idea">${s.idea}</p>
          <dl class="concept-spec">
            <div><dt>Isi</dt><dd>${s.isi}</dd></div>
            <div><dt>Aksi</dt><dd>${s.aksi}</dd></div>
            <div><dt>Data</dt><dd>${dataTags(s)}</dd></div>
            <div><dt>iOS</dt><dd>${s.ios}</dd></div>
            <div><dt>Android</dt><dd>${s.and}</dd></div>
          </dl>
        </div>
      </article>`
    ).join("");
    startClock();
  }

  window.Live = { BOTS, ACC, SCENES, head, heads, ring, ic, up, down, fmt, steps, compact, expanded, chip, android, mountStage, mountCatalog, injectExtra, startClock };
})();
