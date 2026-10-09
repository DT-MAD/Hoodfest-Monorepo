/* Pit Stop — moderation view.
 *
 * The admin key is entered here rather than compiled in, and is kept only in
 * sessionStorage so closing the tab forgets it.
 */
(function () {
  "use strict";

  var KEY_STORAGE = "pitstop.adminKey";

  var form = document.getElementById("unlock");
  var input = document.getElementById("admin-key");
  var error = document.getElementById("unlock-error");
  var boards = document.getElementById("admin-boards");

  function adminKey() {
    try { return sessionStorage.getItem(KEY_STORAGE) || ""; } catch (e) { return ""; }
  }

  function rememberKey(key) {
    try { sessionStorage.setItem(KEY_STORAGE, key); } catch (e) { /* private mode */ }
  }

  function forgetKey() {
    try { sessionStorage.removeItem(KEY_STORAGE); } catch (e) { /* no-op */ }
  }

  function showError(message) {
    error.textContent = message;
    error.hidden = !message;
  }

  /* ----------------------------------------------------------- rendering */

  function entryRow(entry, onDelete) {
    var li = document.createElement("li");
    li.className = "row";

    var pos = document.createElement("span");
    pos.className = "row__pos";
    pos.textContent = entry.rank || "—";

    var name = document.createElement("span");
    name.className = "row__name";
    name.textContent = entry.name;

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
      onDelete(entry, remove);
    });

    li.appendChild(pos);
    li.appendChild(name);
    li.appendChild(score);
    li.appendChild(remove);
    return li;
  }

  function render(payload) {
    boards.textContent = "";

    payload.boards.forEach(function (board) {
      var section = document.createElement("section");
      section.className = "board";

      var head = document.createElement("header");
      head.className = "board__head";
      var h2 = document.createElement("h2");
      h2.textContent = board.title;
      head.appendChild(h2);

      var list = document.createElement("ol");
      list.className = "board__top";

      /* Show the top scores and the newest entries: between them they cover
         everything currently visible on the display. */
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
        shown.forEach(function (entry) {
          list.appendChild(entryRow(entry, remove));
        });
      }

      section.appendChild(head);
      section.appendChild(list);
      boards.appendChild(section);
    });
  }

  /* ------------------------------------------------------------- requests */

  function load() {
    fetch("/api/leaderboard", { cache: "no-store" })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        boards.hidden = false;
        render(data);
      })
      .catch(function () {
        showError("Could not reach the server.");
      });
  }

  function remove(entry, button) {
    fetch("/api/admin/entries/" + entry.id, {
      method: "DELETE",
      headers: { "X-Admin-Key": adminKey() }
    }).then(function (response) {
      if (response.status === 204 || response.status === 404) {
        load();
        return;
      }
      button.disabled = false;
      if (response.status === 401) {
        forgetKey();
        boards.hidden = true;
        form.hidden = false;
        showError("That key was rejected. Enter it again.");
        return;
      }
      showError("Could not remove that entry (status " + response.status + ").");
    }).catch(function () {
      button.disabled = false;
      showError("Could not reach the server.");
    });
  }

  /* Verify the key by attempting a delete of an id that cannot exist. A valid
     key gets 404 (authorized, nothing there); an invalid one gets 401. */
  function verify(key) {
    return fetch("/api/admin/entries/0", {
      method: "DELETE",
      headers: { "X-Admin-Key": key }
    }).then(function (r) { return r.status !== 401; });
  }

  function unlock(key) {
    return verify(key).then(function (ok) {
      if (!ok) {
        showError("That key was rejected.");
        return false;
      }
      rememberKey(key);
      showError("");
      form.hidden = true;
      load();
      return true;
    });
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    var key = input.value.trim();
    if (!key) return;
    unlock(key).catch(function () { showError("Could not reach the server."); });
  });

  /* Skip the prompt if this tab already unlocked. */
  if (adminKey()) {
    unlock(adminKey()).catch(function () { /* fall back to the form */ });
  }
})();
