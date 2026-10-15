(() => {
  const $ = (id) => document.getElementById(id);
  const fa = (n) => Number(n || 0).toLocaleString("fa-IR");
  const TOKEN_KEY = "salawat_token";
  let token = localStorage.getItem(TOKEN_KEY);
  let sessionTotal = 0;
  let intentions = [];

  async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (token) headers["X-Token"] = token;
    const res = await fetch(path, { ...options, headers });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.detail || "خطا در ارتباط با سرور");
    return data;
  }

  async function ensureSession() {
    if (token) {
      try { await api("/api/me/stats"); return; } catch (_) { token = null; }
    }
    const data = await api("/api/session", { method: "POST" });
    token = data.token;
    localStorage.setItem(TOKEN_KEY, token);
  }

  function renderIntentions(current) {
    const select = $("intention");
    select.innerHTML = "";
    intentions.forEach((item) => {
      const opt = document.createElement("option");
      opt.value = item.slug;
      opt.textContent = item.title;
      select.appendChild(opt);
    });
    select.value = current;
    const info = intentions.find((i) => i.slug === current);
    $("intentionDesc").textContent = info ? info.description : "";
  }

  function renderList(el, items) {
    el.innerHTML = "";
    items.forEach((item) => {
      const li = document.createElement("li");
      li.innerHTML = `<span></span><strong></strong>`;
      li.firstChild.textContent = item.title;
      li.lastChild.textContent = fa(item.total);
      el.appendChild(li);
    });
  }

  async function loadMine() {
    const s = await api("/api/me/stats");
    $("myToday").textContent = fa(s.today);
    $("myTotal").textContent = fa(s.total);
    renderList($("myIntentions"), s.by_intention);
    const days = $("myDays");
    days.innerHTML = "";
    s.last_days.forEach((d) => {
      const li = document.createElement("li");
      li.innerHTML = `<span></span><strong></strong>`;
      li.firstChild.textContent = d.day;
      li.lastChild.textContent = fa(d.total);
      days.appendChild(li);
    });
    renderIntentions(s.current_intention);
  }

  async function loadGlobal() {
    const g = await api("/api/stats/global");
    $("gToday").textContent = fa(g.today);
    $("gGoal").textContent = fa(g.daily_goal);
    $("gBar").style.width = `${g.goal_progress_percent}%`;
    $("gParticipants").textContent = fa(g.participants_today);
    $("gTotal").textContent = fa(g.total);
    renderList($("gIntentions"), g.today_by_intention);
  }

  async function count(amount) {
    $("error").textContent = "";
    try {
      const intention = $("intention").value;
      const r = await api("/api/count", {
        method: "POST",
        body: JSON.stringify({ amount, intention }),
      });
      sessionTotal += r.added;
      $("sessionTotal").textContent = fa(sessionTotal);
      $("myToday").textContent = fa(r.today);
      if (navigator.vibrate) navigator.vibrate(30);
      loadMine().catch(() => {});
      loadGlobal().catch(() => {});
    } catch (err) {
      $("error").textContent = err.message;
    }
  }

  document.querySelectorAll("[data-add]").forEach((btn) =>
    btn.addEventListener("click", () => count(Number(btn.dataset.add))));

  $("addCustom").addEventListener("click", () => {
    const n = Number($("customAmount").value);
    if (!Number.isInteger(n) || n < 1 || n > 1000) {
      $("error").textContent = "تعداد باید عدد صحیح بین ۱ و ۱۰۰۰ باشد.";
      return;
    }
    $("customAmount").value = "";
    count(n);
  });

  $("intention").addEventListener("change", async (e) => {
    $("intentionDesc").textContent = intentions.find((i) => i.slug === e.target.value)?.description || "";
    try {
      await api("/api/me/intention", { method: "POST", body: JSON.stringify({ intention: e.target.value }) });
    } catch (err) {
      $("error").textContent = err.message;
    }
  });

  async function init() {
    intentions = (await api("/api/intentions")).intentions;
    await ensureSession();
    await loadMine();
    await loadGlobal();
    setInterval(() => loadGlobal().catch(() => {}), 30000);
  }

  init().catch((err) => { $("error").textContent = err.message; });
})();
