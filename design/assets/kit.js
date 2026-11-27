(function () {
  "use strict";

  const PAGES = [
    ["index.html", "Ringkasan"],
    ["appearance.html", "Tema"],
    ["switches.html", "Saklar"],
    ["segments.html", "Tab & segmen"],
    ["dials.html", "Dial"],
    ["island.html", "Island"],
    ["widgets.html", "Widget"],
  ];

  /* Usage: <svg class="ic"><use href="#i-sun"/></svg> */
  const ICONS = {
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"/>',
    moon: '<path d="M20.2 14.6A8.4 8.4 0 0 1 9.4 3.8a8.4 8.4 0 1 0 10.8 10.8Z"/>',
    eclipse: '<circle cx="12" cy="12" r="8.5"/><path d="M12 3.5a8.5 8.5 0 0 1 0 17Z" fill="currentColor" stroke="none"/>',
    auto: '<circle cx="12" cy="12" r="8.5"/><path d="M12 3.5v17"/><path d="M12 3.5a8.5 8.5 0 0 1 0 17Z" fill="currentColor" stroke="none"/>',
    bell: '<path d="M6 16.5V11a6 6 0 1 1 12 0v5.5l1.5 2H4.5Z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    "bell-off": '<path d="M6 11a6 6 0 0 1 9.6-4.8M18 11v5.5l1.5 2H8"/><path d="M10 20.5a2 2 0 0 0 4 0M3.5 3.5l17 17"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    x: '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
    bolt: '<path d="M13 2.5 4.5 13.5H11l-1 8 8.5-11H12Z"/>',
    hand: '<path d="M8 12V5.5a1.5 1.5 0 0 1 3 0V11M11 10V4a1.5 1.5 0 0 1 3 0v6M14 10V5.5a1.5 1.5 0 0 1 3 0V14a7 7 0 0 1-7 7h-.5a6 6 0 0 1-4.6-2.2L3.5 16a1.6 1.6 0 0 1 2.4-2.1L8 15.5"/>',
    pencil: '<path d="m14.5 5.5 4 4M4 20l1-4.5L16.5 4a1.4 1.4 0 0 1 2 0l1.5 1.5a1.4 1.4 0 0 1 0 2L8.5 19Z"/>',
    wand: '<path d="m3.5 20.5 11-11M14.5 9.5l2-2M17 3v2M21 7h-2M19.8 4.2l-1.4 1.4M12 3.5l.6 1.4M20.5 12l-1.4-.6"/>',
    list: '<path d="M9 6.5h11M9 12h11M9 17.5h11"/><circle cx="4.5" cy="6.5" r="1" fill="currentColor"/><circle cx="4.5" cy="12" r="1" fill="currentColor"/><circle cx="4.5" cy="17.5" r="1" fill="currentColor"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.5"/>',
    speaker: '<path d="M4 9.5h3.5L12 5.5v13L7.5 14.5H4Z"/><path d="M15.5 9a4.2 4.2 0 0 1 0 6M18.3 6.5a8 8 0 0 1 0 11"/>',
    earpiece: '<rect x="7" y="2.5" width="10" height="19" rx="2.8"/><path d="M10.5 5.5h3"/>',
    chats: '<path d="M4 15.5V6.8A2.8 2.8 0 0 1 6.8 4h7.4A2.8 2.8 0 0 1 17 6.8v4.4a2.8 2.8 0 0 1-2.8 2.8H8Z"/><path d="M17 9h.2A2.8 2.8 0 0 1 20 11.8v8.7L16.5 18H11a2.8 2.8 0 0 1-2.4-1.4"/>',
    tray: '<path d="M3.5 13.5 6 5.5h12l2.5 8V18a2.5 2.5 0 0 1-2.5 2.5H6A2.5 2.5 0 0 1 3.5 18Z"/><path d="M3.5 13.5H8l1.5 2.5h5l1.5-2.5h4.5"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    "clock-loop": '<path d="M20 12a8 8 0 1 1-2.3-5.7"/><path d="M20 4v4h-4"/><path d="M12 8v4l2.5 1.5"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
    sparkles: '<path d="M10 3.5 11.6 8.4 16.5 10l-4.9 1.6L10 16.5l-1.6-4.9L3.5 10l4.9-1.6Z"/><path d="M18 14.5l.8 2.2 2.2.8-2.2.8L18 20.5l-.8-2.2-2.2-.8 2.2-.8Z"/>',
    globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.5 2.6 3.5 5.4 3.5 8.5s-1 5.9-3.5 8.5c-2.5-2.6-3.5-5.4-3.5-8.5S9.5 6.1 12 3.5Z"/>',
    brain: '<path d="M9 4.5a3 3 0 0 0-3 3 3 3 0 0 0-2 5 3 3 0 0 0 2 5 3 3 0 0 0 3 2.5V4.5Z"/><path d="M15 4.5a3 3 0 0 1 3 3 3 3 0 0 1 2 5 3 3 0 0 1-2 5 3 3 0 0 1-3 2.5V4.5Z"/><path d="M9 9.5h1.5M13.5 14.5H15"/>',
    chevron: '<path d="m9.5 5.5 6.5 6.5-6.5 6.5"/>',
    "chevron-left": '<path d="m14.5 5.5-6.5 6.5 6.5 6.5"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8.5 10.5V7.5a3.5 3.5 0 0 1 7 0v3"/>',
    computer: '<rect x="3.5" y="4.5" width="17" height="11.5" rx="2"/><path d="M8.5 20h7M12 16v4"/>',
    book: '<path d="M4.5 5A1.5 1.5 0 0 1 6 3.5h13.5v15H6a1.5 1.5 0 0 0-1.5 1.5Z"/><path d="M4.5 20A1.5 1.5 0 0 0 6 21.5h13.5v-3"/>',
    pulse: '<path d="M3 12h4l2.5-6 5 12L17 12h4"/>',
    gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.4M12 18.8v2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M2.8 12h2.4M18.8 12h2.4M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"/>',
    people: '<circle cx="9" cy="8.5" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 5.2a3.5 3.5 0 0 1 0 6.6M18.5 14.5A6.5 6.5 0 0 1 21.5 20"/>',
    bot: '<rect x="4" y="7.5" width="16" height="12" rx="4"/><path d="M12 7.5V4.5M9 13.5v1M15 13.5v1"/><circle cx="12" cy="3.5" r="1" fill="currentColor"/>',
    phone: '<path d="M5 4h3.5l1.5 4-2 1.5a11 11 0 0 0 6.5 6.5l1.5-2 4 1.5V19a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z"/>',
    timer: '<circle cx="12" cy="13.5" r="7.5"/><path d="M12 13.5V9.5M9.5 2.5h5"/>',
    text: '<path d="M5 6.5V5h14v1.5M12 5v14M9 19h6"/>',
    captions: '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M10 10.5a2 2 0 1 0 0 3M17 10.5a2 2 0 1 0 0 3"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/>',
    "eye-off": '<path d="M9.9 5.8A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.6 3.4M6.6 6.6C4 8.3 2.5 12 2.5 12S6 18.5 12 18.5a9 9 0 0 0 5.4-1.8"/><path d="m3.5 3.5 17 17M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
    shield: '<path d="M12 2.8 4.5 5.8v5.7c0 4.6 3.1 8.3 7.5 9.7 4.4-1.4 7.5-5.1 7.5-9.7V5.8Z"/>',
    layers: '<path d="m12 3.5 9 5-9 5-9-5Z"/><path d="m3 13 9 5 9-5"/>',
    rows: '<rect x="3.5" y="4" width="17" height="6.5" rx="2"/><rect x="3.5" y="13.5" width="17" height="6.5" rx="2"/>',
    "rows-dense": '<rect x="3.5" y="3.5" width="17" height="4" rx="1.3"/><rect x="3.5" y="10" width="17" height="4" rx="1.3"/><rect x="3.5" y="16.5" width="17" height="4" rx="1.3"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>',
  };

  function injectSprite() {
    if (document.getElementById("seira-sprite")) return;
    const symbols = Object.entries(ICONS)
      .map(
        ([name, body]) =>
          `<symbol id="i-${name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${body}</symbol>`
      )
      .join("");
    const holder = document.createElement("div");
    holder.innerHTML = `<svg id="seira-sprite" xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">${symbols}</svg>`;
    document.body.prepend(holder.firstChild);
  }

  function icon(name, cls) {
    return `<svg class="ic ${cls || ""}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
  }

  const rmQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  const darkQuery = window.matchMedia("(prefers-color-scheme: dark)");

  /* Haptic stand-in: a short vibration where the browser allows it, plus a micro scale pulse. */
  function tick(el, strength) {
    try {
      if (navigator.vibrate && navigator.userActivation?.hasBeenActive) navigator.vibrate(strength === "heavy" ? 18 : 8);
    } catch (_) {}
    if (!el || rmQuery.matches || !el.animate) return;
    el.animate(
      [{ transform: "scale(1)" }, { transform: `scale(${strength === "heavy" ? 0.94 : 0.97})` }, { transform: "scale(1)" }],
      { duration: 180, easing: "cubic-bezier(.2,.8,.2,1)" }
    );
  }

  /* Change the Seira theme of one scope element.
     mode: "light" | "dark" | "auto". Auto follows the viewer's system setting.
     opts.x / opts.y: viewport point the reveal grows from. opts.reveal: "circle" (default) | "fade" | false. */
  let vtCount = 0;
  function setUI(el, mode, opts) {
    opts = opts || {};
    const resolved = mode === "auto" ? (darkQuery.matches ? "dark" : "light") : mode;
    el.dataset.uiChoice = mode;
    if ((el._uiPending || el.dataset.ui) === resolved) return Promise.resolve();
    el._uiPending = resolved;
    const apply = () => {
      el.dataset.ui = el._uiPending || resolved;
    };
    const settle = () => {
      if (el._uiPending === el.dataset.ui) el._uiPending = undefined;
    };
    if (!document.startViewTransition || rmQuery.matches || opts.reveal === false) {
      apply();
      settle();
      return Promise.resolve();
    }
    const r = el.getBoundingClientRect();
    const x = (opts.x != null ? opts.x : r.left + r.width / 2) - r.left;
    const y = (opts.y != null ? opts.y : r.top + r.height / 2) - r.top;
    const radius = Math.hypot(Math.max(x, r.width - x), Math.max(y, r.height - y));
    const name = "seira-vt-" + ++vtCount;
    el.style.viewTransitionName = name;
    let t;
    try {
      t = document.startViewTransition(apply);
    } catch (_) {
      el.style.viewTransitionName = "";
      apply();
      settle();
      return Promise.resolve();
    }
    t.ready
      .then(() => {
        const frames =
          opts.reveal === "fade"
            ? { opacity: [0, 1] }
            : { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] };
        document.documentElement.animate(frames, {
          duration: opts.duration || 560,
          easing: "cubic-bezier(.65,0,.25,1)",
          pseudoElement: `::view-transition-new(${name})`,
        });
      })
      .catch(() => {});
    return t.finished.finally(() => {
      if (el.style.viewTransitionName === name) el.style.viewTransitionName = "";
      settle();
    });
  }

  /* Critically damped-ish spring driven by rAF. Returns a stop function.
     opts: { from, to, velocity, stiffness, damping, mass, onUpdate(value), onDone() } */
  function spring(opts) {
    const k = opts.stiffness || 420;
    const c = opts.damping || 32;
    const m = opts.mass || 1;
    let x = opts.from;
    let v = opts.velocity || 0;
    const to = opts.to;
    let last = performance.now();
    let raf = 0;
    if (rmQuery.matches) {
      opts.onUpdate && opts.onUpdate(to);
      opts.onDone && opts.onDone();
      return () => {};
    }
    function step(now) {
      const dt = Math.min(0.032, (now - last) / 1000);
      last = now;
      const a = (-k * (x - to) - c * v) / m;
      v += a * dt;
      x += v * dt;
      opts.onUpdate && opts.onUpdate(x, v);
      if (Math.abs(v) < 0.01 && Math.abs(x - to) < 0.001) {
        opts.onUpdate && opts.onUpdate(to, 0);
        opts.onDone && opts.onDone();
        return;
      }
      raf = requestAnimationFrame(step);
    }
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }

  /* Pointer drag helper with velocity. handlers: { start(e), move({dx, dy, x, y, e}), end({dx, dy, vx, vy, e}) } */
  function drag(el, handlers) {
    let sx = 0, sy = 0, lx = 0, ly = 0, lt = 0, vx = 0, vy = 0, active = false;
    el.addEventListener("pointerdown", (e) => {
      if (e.button !== 0) return;
      active = true;
      sx = lx = e.clientX;
      sy = ly = e.clientY;
      lt = performance.now();
      vx = vy = 0;
      try { el.setPointerCapture(e.pointerId); } catch (_) {}
      handlers.start && handlers.start(e);
    });
    el.addEventListener("pointermove", (e) => {
      if (!active) return;
      const now = performance.now();
      const dt = Math.max(1, now - lt);
      vx = (e.clientX - lx) / dt;
      vy = (e.clientY - ly) / dt;
      lx = e.clientX;
      ly = e.clientY;
      lt = now;
      handlers.move && handlers.move({ dx: e.clientX - sx, dy: e.clientY - sy, x: e.clientX, y: e.clientY, e });
    });
    const finish = (e) => {
      if (!active) return;
      active = false;
      handlers.end && handlers.end({ dx: e.clientX - sx, dy: e.clientY - sy, vx, vy, e });
    };
    el.addEventListener("pointerup", finish);
    el.addEventListener("pointercancel", finish);
  }

  /* Gallery nav. Put <header data-g-nav="appearance.html"></header> at the top of a page. */
  function renderNav() {
    document.querySelectorAll("[data-g-nav]").forEach((host) => {
      const current = host.getAttribute("data-g-nav");
      host.className = "g-nav";
      host.innerHTML = `
        <a class="g-brand" href="index.html"><i class="g-brand-mark" aria-hidden="true"></i><span>Seira Design</span></a>
        <nav class="g-links" aria-label="Papan">${PAGES.map(
          ([href, label]) => `<a href="${href}"${href === current ? ' aria-current="page"' : ""}>${label}</a>`
        ).join("")}</nav>
        <div class="g-ui-switch" role="group" aria-label="Tema mockup">
          <button type="button" data-g-ui="light" aria-pressed="false">Light</button>
          <button type="button" data-g-ui="dark" aria-pressed="true">Dark</button>
        </div>`;
      host.querySelectorAll("[data-g-ui]").forEach((b) =>
        b.addEventListener("click", () => setAllStages(b.getAttribute("data-g-ui")))
      );
    });
  }

  /* Stages marked data-ui-global follow the nav switch. */
  function setAllStages(mode) {
    document.querySelectorAll("[data-g-ui]").forEach((b) => b.setAttribute("aria-pressed", String(b.getAttribute("data-g-ui") === mode)));
    document.querySelectorAll("[data-ui-global]").forEach((el) => {
      el.dataset.ui = mode;
    });
    try { localStorage.setItem("seira-design-ui", mode); } catch (_) {}
    document.dispatchEvent(new CustomEvent("seira:ui", { detail: mode }));
  }

  function restoreStages() {
    let mode = null;
    try { mode = localStorage.getItem("seira-design-ui"); } catch (_) {}
    if (mode === "light" || mode === "dark") setAllStages(mode);
  }

  window.Seira = {
    icon,
    icons: ICONS,
    tick,
    setUI,
    spring,
    drag,
    setAllStages,
    get reduced() {
      return rmQuery.matches;
    },
    get systemDark() {
      return darkQuery.matches;
    },
  };

  function boot() {
    injectSprite();
    renderNav();
    restoreStages();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
