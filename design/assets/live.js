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
    const holder = document.createElement("div");
    holder.innerHTML = `<svg id="live-sprite" xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">${symbols}</svg>`;
    document.body.prepend(holder.firstChild);
  }

  const ic = (name) => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;

  const BOTS = {
    atlas: { name: "Atlas", hair: "short-neat", color: "#377FE6" },
    mira: { name: "Mira", hair: "twin-tails", color: "#D84F8B" },
    juno: { name: "Juno", hair: "spiky", color: "#01A492" },
    seira: { name: "Seira", hair: "seira", color: "#E11D48" },
    nox: { name: "Nox", hair: "messy", color: "#8057C8" },
    kei: { name: "Kei", hair: "bob", color: "#E78531" },
  };

  const ACC = { working: "#4f8dff", needs: "#f5a524", failed: "#ff5c6c", done: "#3ccf8e", soon: "#a78bfa" };

  function head(bot, mood, size, cls, ringColor) {
    const b = BOTS[bot];
    const src = new URL(`assets/heads/${b.hair}.webp`, document.baseURI).href;
    const rc = ringColor ? `;--rc:${ringColor}` : "";
    return `<span class="hd ${cls || ""}" data-mood="${mood || "idle"}" style="--s:${size}px;--c:${b.color};--img:url(${src})${rc}"><img src="${src}" alt=""><span class="ey"><b></b><b></b></span></span>`;
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
