/* محفظتي — wallet-service frontend (no build step) */
(function () {
  "use strict";

  var TOKEN_KEY = "wt_access";
  var REFRESH_KEY = "wt_refresh";
  var USER_KEY = "wt_user";

  var app = document.getElementById("app");

  /* ---------- helpers ---------- */

  function el(html) {
    var t = document.createElement("template");
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function toast(msg, isErr) {
    var t = el('<div class="toast' + (isErr ? " err" : "") + '">' + esc(msg) + "</div>");
    document.getElementById("toasts").appendChild(t);
    setTimeout(function () { t.remove(); }, 4200);
  }

  function fmtMinor(minor, currency) {
    var v = (minor / 100).toLocaleString("en-EG", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return v + " " + (currency || "EGP");
  }

  function fmtDate(iso) {
    try {
      return new Date(iso).toLocaleString("ar-EG", { dateStyle: "medium", timeStyle: "short" });
    } catch (e) { return iso; }
  }

  /* ---------- API ---------- */

  function api(path, opts, allowRefresh) {
    opts = opts || {};
    var headers = { "Content-Type": "application/json" };
    var token = localStorage.getItem(TOKEN_KEY);
    if (token) headers.Authorization = "Bearer " + token;
    return fetch(path, {
      method: opts.method || "GET",
      headers: headers,
      body: opts.body ? JSON.stringify(opts.body) : undefined
    }).then(function (res) {
      if (res.status === 401 && allowRefresh !== false && localStorage.getItem(REFRESH_KEY)) {
        return refreshSession().then(function (ok) {
          return ok ? api(path, opts, false) : Promise.reject({ code: "UNAUTHENTICATED" });
        });
      }
      return res.json().then(function (body) {
        if (!res.ok) {
          var detail = body && body.violations && body.violations.length
            ? body.violations.map(function (v) { return v.field + ": " + v.message; }).join("، ")
            : (body && body.message) || "حدث خطأ غير متوقع";
          throw { code: body && body.code, message: detail, status: res.status };
        }
        return body.data;
      });
    });
  }

  function refreshSession() {
    return fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: localStorage.getItem(REFRESH_KEY) })
    }).then(function (res) {
      if (!res.ok) { clearSession(); return false; }
      return res.json().then(function (body) {
        saveSession(body.data);
        return true;
      });
    }).catch(function () { return false; });
  }

  function saveSession(data) {
    localStorage.setItem(TOKEN_KEY, data.accessToken);
    if (data.refreshToken) localStorage.setItem(REFRESH_KEY, data.refreshToken);
    if (data.user) localStorage.setItem(USER_KEY, JSON.stringify(data.user));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  }

  function currentUser() {
    try { return JSON.parse(localStorage.getItem(USER_KEY) || "null"); } catch (e) { return null; }
  }

  /* ---------- auth view ---------- */

  var authMode = "login";

  function renderAuth() {
    document.getElementById("user-chip").hidden = true;
    document.getElementById("logout-btn").hidden = true;
    app.innerHTML = "";
    var shell = el(
      '<div class="auth-shell">' +
        '<div class="auth-hero">' +
          "<h1>فلوسك، <span class=\"grad-text\">تحت إيدك</span></h1>" +
          '<p class="auth-sub">محفظة إلكترونية بسيطة: تشحن رصيدك بالبطاقة وتشوف كل حركة حصلت في حسابك.</p>' +
        "</div>" +
        '<section class="card auth-card">' +
          '<div class="auth-tabs">' +
            '<button id="tab-login" type="button">دخول</button>' +
            '<button id="tab-register" type="button">حساب جديد</button>' +
          "</div>" +
          '<form id="auth-form" novalidate>' +
            '<div class="field" id="f-name" hidden><label for="in-name">الاسم بالكامل</label>' +
            '<input id="in-name" type="text" autocomplete="name" placeholder="يوسف نادر"></div>' +
            '<div class="field"><label for="in-email">البريد الإلكتروني</label>' +
            '<input id="in-email" type="email" autocomplete="email" placeholder="you@example.com" dir="ltr"></div>' +
            '<div class="field"><label for="in-pass">كلمة المرور</label>' +
            '<input id="in-pass" type="password" autocomplete="current-password" placeholder="٨ أحرف على الأقل" dir="ltr"></div>' +
            '<div class="err" id="auth-err"></div>' +
            '<button class="btn" id="auth-submit" type="submit" style="width:100%">دخول</button>' +
          "</form>" +
        "</section>" +
        '<ul class="auth-features">' +
          "<li>شحن بالبطاقة عبر Paymob</li>" +
          "<li>كل حركة متسجلة</li>" +
          "<li>الرصيد بيتحدث بعد الدفع لوحده</li>" +
          "<li>تفضل مسجل دخولك</li>" +
        "</ul>" +
      "</div>"
    );
    app.appendChild(shell);

    var tabLogin = shell.querySelector("#tab-login");
    var tabRegister = shell.querySelector("#tab-register");

    function syncTabs() {
      tabLogin.classList.toggle("active", authMode === "login");
      tabRegister.classList.toggle("active", authMode === "register");
      shell.querySelector("#f-name").hidden = authMode === "login";
      shell.querySelector("#auth-submit").textContent = authMode === "login" ? "دخول" : "إنشاء الحساب";
      shell.querySelector("#in-pass").setAttribute("autocomplete", authMode === "login" ? "current-password" : "new-password");
      shell.querySelector("#auth-err").textContent = "";
    }
    tabLogin.onclick = function () { authMode = "login"; syncTabs(); };
    tabRegister.onclick = function () { authMode = "register"; syncTabs(); };
    syncTabs();

    shell.querySelector("#auth-form").onsubmit = function (ev) {
      ev.preventDefault();
      var email = shell.querySelector("#in-email").value.trim();
      var password = shell.querySelector("#in-pass").value;
      var fullName = shell.querySelector("#in-name").value.trim();
      var errBox = shell.querySelector("#auth-err");
      var btn = shell.querySelector("#auth-submit");
      errBox.textContent = "";
      btn.disabled = true;
      btn.innerHTML = '<span class="spin"></span> لحظات…';

      var call = authMode === "login"
        ? api("/api/v1/auth/login", { method: "POST", body: { email: email, password: password } })
        : api("/api/v1/auth/register", { method: "POST", body: { email: email, fullName: fullName, password: password } });

      call.then(function (data) {
        saveSession(data);
        toast(authMode === "login" ? "أهلًا بعودتك" : "تم إنشاء حسابك");
        renderApp();
      }).catch(function (e) {
        errBox.textContent = e.code === "UNAUTHENTICATED" || e.status === 401
          ? "البريد أو كلمة المرور غير صحيحة"
          : (e.message || "تعذر إتمام العملية");
      }).finally(function () {
        btn.disabled = false;
        btn.textContent = authMode === "login" ? "دخول" : "إنشاء الحساب";
      });
    };
  }

  /* ---------- app view ---------- */

  var pollTimer = null;

  function renderApp() {
    var user = currentUser();
    var chip = document.getElementById("user-chip");
    chip.hidden = false;
    chip.textContent = (user && (user.fullName || user.email)) || "";
    document.getElementById("logout-btn").hidden = false;

    app.innerHTML = "";
    var view = el(
      "<div>" +
        '<div class="masthead">' +
          "<div>" +
            '<div class="eyebrow">لوحة المحفظة</div>' +
            "<h1>أهلًا" + (user && user.fullName ? " يا " + esc(String(user.fullName).split(" ")[0]) : "") + "</h1>" +
          "</div>" +
        "</div>" +
        '<section class="balance-card">' +
          '<div class="balance-top">' +
            '<span class="balance-label">الرصيد المتاح</span>' +
            '<span class="balance-badge">EGP</span>' +
          "</div>" +
          '<div class="balance-value" id="balance"><span class="spin"></span></div>' +
          '<div class="balance-actions">' +
            '<button class="btn btn-glass btn-sm" id="refresh-btn" type="button">تحديث الرصيد</button>' +
          "</div>" +
        "</section>" +
        '<div class="grid">' +
          '<section class="card">' +
            '<div class="section-title"><h2>شحن الرصيد</h2></div>' +
            '<div class="chips" id="chips">' +
              '<button class="chip" data-v="50" type="button">٥٠ ج</button>' +
              '<button class="chip active" data-v="100" type="button">١٠٠ ج</button>' +
              '<button class="chip" data-v="200" type="button">٢٠٠ ج</button>' +
              '<button class="chip" data-v="500" type="button">٥٠٠ ج</button>' +
            "</div>" +
            '<div class="field"><label for="amount">المبلغ بالجنيه</label>' +
            '<input id="amount" type="number" min="1" step="0.5" value="100" dir="ltr"></div>' +
            '<div class="err" id="topup-err"></div>' +
            '<button class="btn" id="topup-btn" type="button" style="width:100%">شحن عبر Paymob</button>' +
          "</section>" +
          '<section class="card">' +
            '<div class="section-title"><h2>سجل الحركات</h2>' +
            '<button class="btn btn-quiet btn-sm" id="tx-refresh" type="button">تحديث</button></div>' +
            '<ul class="tx-list" id="tx-list"><div class="empty">جارِ التحميل…</div></ul>' +
            '<button class="btn btn-quiet btn-sm" id="tx-more" type="button" hidden style="width:100%;margin-block-start:0.7rem">عرض المزيد</button>' +
          "</section>" +
        "</div>" +
      "</div>"
    );
    app.appendChild(view);

    var amountIn = view.querySelector("#amount");
    view.querySelectorAll(".chip").forEach(function (chipBtn) {
      chipBtn.onclick = function () {
        view.querySelectorAll(".chip").forEach(function (c) { c.classList.remove("active"); });
        chipBtn.classList.add("active");
        amountIn.value = chipBtn.dataset.v;
      };
    });

    view.querySelector("#refresh-btn").onclick = loadBalance;
    view.querySelector("#tx-refresh").onclick = function () { loadTransactions(); };
    view.querySelector("#tx-more").onclick = function () {
      loadTransactions(view.querySelector("#tx-more").dataset.before);
    };
    view.querySelector("#topup-btn").onclick = function () { startTopUp(view); };

    loadBalance();
    loadTransactions();
  }

  function loadBalance() {
    var box = document.getElementById("balance");
    if (!box) return;
    return api("/api/v1/wallet").then(function (w) {
      box.textContent = w.balanceFormatted || fmtMinor(w.balanceMinor, w.currency);
    }).catch(function (e) {
      if (e.code === "UNAUTHENTICATED") { clearSession(); renderAuth(); return; }
      box.textContent = "—";
    });
  }

  function loadTransactions(beforeId) {
    var list = document.getElementById("tx-list");
    var moreBtn = document.getElementById("tx-more");
    if (!list) return;
    if (!beforeId) list.innerHTML = '<div class="empty">جارِ التحميل…</div>';
    moreBtn.hidden = true;
    var url = "/api/v1/wallet/transactions?limit=20" + (beforeId ? "&beforeId=" + encodeURIComponent(beforeId) : "");
    return api(url).then(function (d) {
      var entries = (d && d.entries) || [];
      if (!beforeId && !entries.length) {
        list.innerHTML = '<div class="empty">لا حركات بعد — اشحن رصيدك الأول</div>';
        return;
      }
      if (!beforeId) list.innerHTML = "";
      var loading = list.querySelector(".empty");
      if (loading) loading.remove();
      entries.forEach(function (tx) {
        var credit = tx.direction === "CREDIT";
        var arrow = credit
          ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5"/><path d="m5 12 7-7 7 7"/></svg>'
          : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14"/><path d="m19 12-7 7-7-7"/></svg>';
        list.appendChild(el(
          '<li class="tx">' +
            '<span class="tx-icon ' + (credit ? "credit" : "debit") + '">' + arrow + "</span>" +
            '<div class="tx-body">' +
              '<div class="tx-desc">' + esc(tx.description || (credit ? "إيداع" : "خصم")) + "</div>" +
              '<div class="tx-date">' + esc(fmtDate(tx.createdAt)) + "</div>" +
            "</div>" +
            '<div class="tx-amount ' + (credit ? "credit" : "debit") + '">' +
              (credit ? "+" : "−") + esc(fmtMinor(tx.amountMinor, "EGP")) +
            "</div>" +
          "</li>"
        ));
      });
      if (d && d.nextBeforeId) {
        moreBtn.dataset.before = d.nextBeforeId;
        moreBtn.hidden = false;
      }
    }).catch(function () {
      if (!beforeId) list.innerHTML = '<div class="empty">تعذر تحميل الحركات</div>';
    });
  }

  /* ---------- top-up flow ---------- */

  function startTopUp(view) {
    var errBox = view.querySelector("#topup-err");
    var btn = view.querySelector("#topup-btn");
    var egp = parseFloat(view.querySelector("#amount").value);
    errBox.textContent = "";
    if (!egp || egp <= 0) { errBox.textContent = "أدخل مبلغًا صحيحًا"; return; }
    var minor = Math.round(egp * 100);
    btn.disabled = true;
    btn.innerHTML = '<span class="spin"></span> جارِ تجهيز الدفع…';
    api("/api/v1/topups", { method: "POST", body: { amountMinor: minor } })
      .then(function (d) {
        openPayModal(d.paymentUrl, minor);
      })
      .catch(function (e) {
        errBox.textContent = e.message || "تعذر بدء الشحن";
      })
      .finally(function () {
        btn.disabled = false;
        btn.textContent = "شحن عبر Paymob";
      });
  }

  function openPayModal(paymentUrl, minor) {
    var modal = document.getElementById("pay-modal");
    var frame = document.getElementById("pay-frame");
    var status = document.getElementById("pay-status");
    var before = null;

    status.textContent = "بانتظار الدفع…";
    status.classList.remove("ok");
    frame.src = paymentUrl;
    modal.hidden = false;

    api("/api/v1/wallet").then(function (w) { before = w.balanceMinor; }).catch(function () {});

    function stopPoll() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }

    function check() {
      return loadBalance().then(function () {
        return api("/api/v1/wallet").then(function (w) {
          if (before != null && w.balanceMinor >= before + minor) {
            stopPoll();
            status.textContent = "تم الشحن بنجاح ✓";
            status.classList.add("ok");
            toast("اتشحن رصيدك بـ " + fmtMinor(minor, w.currency));
            loadTransactions();
            setTimeout(closeModal, 1800);
          }
        });
      });
    }

    function closeModal() {
      stopPoll();
      modal.hidden = true;
      frame.src = "about:blank";
    }

    document.getElementById("pay-check").onclick = check;
    document.getElementById("pay-close").onclick = closeModal;
    pollTimer = setInterval(check, 5000);
  }

  /* ---------- boot ---------- */

  document.getElementById("theme-toggle").onclick = function () {
    var now = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = now;
    localStorage.setItem("wt_theme", now);
  };

  document.getElementById("logout-btn").onclick = function () {
    var rt = localStorage.getItem(REFRESH_KEY);
    if (rt) {
      fetch("/api/v1/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": "Bearer " + localStorage.getItem(TOKEN_KEY) },
        body: JSON.stringify({ refreshToken: rt })
      }).catch(function () {});
    }
    clearSession();
    renderAuth();
  };

  if (localStorage.getItem(TOKEN_KEY)) {
    renderApp();
  } else {
    renderAuth();
  }
})();
