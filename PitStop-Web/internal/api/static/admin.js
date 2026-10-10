/* Pit Stop — booth operator dashboard.
 *
 * The admin key is entered here rather than compiled in, and kept only in
 * sessionStorage so closing the tab forgets it.
 */
(function () {
  "use strict";

  var KEY_STORAGE = "pitstop.adminKey";

  var form = document.getElementById("unlock");
  var input = document.getElementById("admin-key");
  var unlockError = document.getElementById("unlock-error");
  var dashboard = document.getElementById("dashboard");
  var statsEl = document.getElementById("stats");
  var boardsEl = document.getElementById("admin-boards");
  var statusEl = document.getElementById("action-status");
  var seedButton = document.getElementById("seed");
  var resetButton = document.getElementById("reset");
  var confirmDialog = document.getElementById("confirm-reset");
  var confirmInput = document.getElementById("confirm-input");
  var confirmGo = document.getElementById("confirm-go");
  var confirmCount = document.getElementById("confirm-count");

  var lastTotal = 0;

  /* ------------------------------------------------------------------ key */

  function adminKey() {
    try { return sessionStorage.getItem(KEY_STORAGE) || ""; } catch (e) { return ""; }
  }

  function rememberKey(key) {
    try { sessionStorage.setItem(KEY_STORAGE, key); } catch (e) { /* private mode */ }
  }

  function forgetKey() {
    try { sessionStorage.removeItem(KEY_STORAGE); } catch (e) { /* no-op */ }
  }

  function lock(message) {
    forgetKey();
    dashboard.hidden = true;
    form.hidden = false;
    unlockError.textContent = message || "";
    unlockError.hidden = !message;
  }

  /* -------------------------------------------------------------- requests */

  function request(path, options) {
    var opts = options || {};
    opts.headers = Object.assign({}, opts.headers, { "X-Admin-Key": adminKey() });
    opts.cache = "no-store";

    return fetch(path, opts).then(function (response) {
      if (response.status === 401) {
        lock("That key was rejected. Enter it again.");
        return Promise.reject(new Error("unauthorized"));
      }
      return response;
    });
  }

  function setStatus(message, tone) {
    statusEl.textContent = message || "";
    statusEl.setAttribute("data-tone", tone || "");
  }

  function busy(isBusy) {
    seedButton.disabled = isBusy;
    resetButton.disabled = isBusy;
  }

  /* ------------------------------------------------------------- rendering */

  function renderStats(stats) {
    statsEl.textContent = "";

    var tiles = [
      { label: "Total entries", value: stats.total },
      { label: "Starter scores", value: stats.seeded },
      { label: "Real runs", value: stats.total - stats.seeded }
    ];

    tiles.forEach(function (tile) {
      var box = document.createElement("div");
      box.className = "stat";

      var value = document.createElement("span");
      value.className = "stat__value";
      value.textContent = tile.value;

      var label = document.createElement("span");
      label.className = "stat__label";
      label.textContent = tile.label;

      box.appendChild(value);
      box.appendChild(label);
      statsEl.appendChild(box);
    });
  }

  function entryRow(entry) {
    var li = document.createElement("li");
    li.className = "row";

    var pos = document.createElement("span");
    pos.className = "row__pos";
    pos.textContent = entry.rank || "—";

    var name = document.createElement("span");
    name.className = "row__name";
    name.textContent = entry.name;
    if (entry.detail && entry.detail.seeded) {
      var tag = document.createElement("span");
      tag.className = "tag";
      tag.textContent = "starter";
      name.appendChild(tag);
    }

    var score = document.createElement("span");
    score.className = "row__score";
    score.textContent = entry.display;

    var remove = document.createElement("button");
    remove.type = "button";
    remove.className = "btn btn--danger";
    remove.textContent = "Remove";
    remove.setAttribute("aria-label", "Remove " + entry.name + ", " + entry.display);
    remove.addEventListener("click", function () {
      if (!window.confirm("Remove " + entry.name + " (" + entry.display + ") from the board?")) return;
      remove.disabled = true;
      removeEntry(entry, remove);
    });

    li.appendChild(pos);
    li.appendChild(name);
    li.appendChild(score);
    li.appendChild(remove);
    return li;
  }

  function renderBoards(payload) {
    boardsEl.textContent = "";

    payload.boards.forEach(function (board) {
      var section = document.createElement("section");
      section.className = "board";

      var head = document.createElement("header");
      head.className = "board__head";
      var h3 = document.createElement("h3");
      h3.textContent = board.title;
      head.appendChild(h3);

      var list = document.createElement("ol");
      list.className = "board__top";

      /* The top scores plus the newest entries: between them they cover
         everything currently on the display. */
      var shown = board.top.slice();
      board.recent.forEach(function (e) {
        if (!shown.some(function (x) { return x.id === e.id; })) shown.push(e);
      });

      if (!shown.length) {
        var empty = document.createElement("li");
        empty.className = "board__empty";
        empty.textContent = "No entries";
        list.appendChild(empty);
      } else {
        shown.forEach(function (entry) { list.appendChild(entryRow(entry)); });
      }

      section.appendChild(head);
      section.appendChild(list);
      boardsEl.appendChild(section);
    });
  }

  /* --------------------------------------------------------------- actions */

  function load() {
    return request("/api/admin/stats")
      .then(function (r) { return r.json(); })
      .then(function (data) {
        lastTotal = data.stats.total;
        dashboard.hidden = false;
        form.hidden = true;
        renderStats(data.stats);
        renderBoards(data.boards);
      })
      .catch(function (error) {
        if (error && error.message === "unauthorized") return;
        setStatus("Could not reach the server.", "bad");
      });
  }

  function removeEntry(entry, button) {
    request("/api/admin/entries/" + entry.id, { method: "DELETE" })
      .then(function (response) {
        if (response.status === 204 || response.status === 404) {
          setStatus("Removed " + entry.name + ".", "good");
          return load();
        }
        button.disabled = false;
        setStatus("Could not remove that entry (status " + response.status + ").", "bad");
      })
      .catch(function (error) {
        if (error && error.message === "unauthorized") return;
        button.disabled = false;
        setStatus("Could not reach the server.", "bad");
      });
  }

  seedButton.addEventListener("click", function () {
    busy(true);
    setStatus("Loading starter scores…");

    request("/api/admin/seed", { method: "POST" })
      .then(function (r) { return r.json(); })
      .then(function (result) {
        busy(false);
        var message = "Added " + result.inserted + " starter scores";
        message += result.replaced ? ", replacing " + result.replaced + " from last time." : ".";
        setStatus(message, "good");
        return load();
      })
      .catch(function (error) {
        busy(false);
        if (error && error.message === "unauthorized") return;
        setStatus("Could not load the starter scores.", "bad");
      });
  });

  /* The reset dialog requires the word to be typed: the dashboard is often
     left open on a laptop, and a mis-click must not wipe the day's scores. */
  resetButton.addEventListener("click", function () {
    confirmInput.value = "";
    confirmGo.disabled = true;
    confirmCount.textContent = lastTotal;
    confirmDialog.showModal();
  });

  confirmInput.addEventListener("input", function () {
    confirmGo.disabled = confirmInput.value.trim().toUpperCase() !== "RESET";
  });

  confirmDialog.addEventListener("close", function () {
    if (confirmDialog.returnValue !== "confirm") return;

    busy(true);
    setStatus("Deleting every score…");

    request("/api/admin/entries?confirm=RESET", { method: "DELETE" })
      .then(function (r) { return r.json(); })
      .then(function (result) {
        busy(false);
        setStatus("Deleted " + result.deleted + " entries. The boards are empty.", "good");
        return load();
      })
      .catch(function (error) {
        busy(false);
        if (error && error.message === "unauthorized") return;
        setStatus("Could not reset the boards.", "bad");
      });
  });

  /* ---------------------------------------------------------------- unlock */

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    var key = input.value.trim();
    if (!key) return;

    rememberKey(key);
    unlockError.hidden = true;
    load();
  });

  /* Skip the prompt if this tab already unlocked. */
  if (adminKey()) load();
})();
