/* Pit Stop — booth display.
 *
 * Holds an EventSource open to /api/events and re-renders the boards on every
 * push. If the stream cannot be kept up, it falls back to polling, because a
 * display that is silently stale is worse than one that is a few seconds behind.
 */
(function () {
  "use strict";

  var POLL_INTERVAL_MS = 5000;
  var FAILURES_BEFORE_POLLING = 3;

  var failures = 0;
  var pollTimer = null;
  var source = null;

  /* Ids already on screen, per game, so a newly landed score can flash. */
  var seen = Object.create(null);
  var firstRender = true;

  var statusDot = document.getElementById("status-dot");
  var statusText = document.getElementById("status-text");

  function setStatus(state, text) {
    statusDot.setAttribute("data-state", state);
    statusText.textContent = text;
  }

  /* ----------------------------------------------------------- rendering */

  function row(entry, isLeader, isNew) {
    var li = document.createElement("li");
    li.className = "row" + (isLeader ? " row--leader" : "") + (isNew ? " row--new" : "");

    var pos = document.createElement("span");
    pos.className = "row__pos";
    pos.textContent = entry.rank;

    var name = document.createElement("span");
    name.className = "row__name";
    name.textContent = entry.name;

    var score = document.createElement("span");
    score.className = "row__score";
    score.textContent = entry.display;

    li.appendChild(pos);
    li.appendChild(name);
    li.appendChild(score);

    /* One readable sentence for a screen reader, instead of three fragments. */
    li.setAttribute("aria-label",
      "Position " + entry.rank + (isLeader ? ", leader" : "") +
      ": " + entry.name + ", " + entry.display);

    return li;
  }

  function emptyRow(message) {
    var li = document.createElement("li");
    li.className = "board__empty";
    li.textContent = message;
    return li;
  }

  function renderBoard(board) {
    var section = document.querySelector('.board[data-game="' + board.game + '"]');
    if (!section) return;

    var known = seen[board.game] || (seen[board.game] = Object.create(null));

    var top = section.querySelector('[data-role="top"]');
    top.textContent = "";

    if (!board.top.length) {
      top.appendChild(emptyRow("No scores yet — be the first."));
    } else {
      board.top.forEach(function (entry) {
        var isNew = !firstRender && !known[entry.id];
        top.appendChild(row(entry, entry.rank === 1, isNew));
      });
    }

    var recent = section.querySelector('[data-role="recent"]');
    recent.textContent = "";

    if (!board.recent.length) {
      var li = document.createElement("li");
      li.className = "recent-row recent-row--empty";
      li.textContent = "Nothing played yet";
      recent.appendChild(li);
    } else {
      board.recent.forEach(function (entry) {
        var item = document.createElement("li");
        item.className = "recent-row";

        var name = document.createElement("span");
        name.textContent = entry.name;
        var score = document.createElement("span");
        score.textContent = entry.display;

        item.appendChild(name);
        item.appendChild(score);
        recent.appendChild(item);
      });
    }

    section.querySelector('[data-role="total"]').textContent =
      board.total === 1 ? "1 run recorded" : board.total + " runs recorded";

    /* Remember every id now on screen so the next render can spot arrivals. */
    seen[board.game] = Object.create(null);
    board.top.concat(board.recent).forEach(function (e) {
      seen[board.game][e.id] = true;
    });
  }

  function render(payload) {
    if (!payload || !payload.boards) return;
    payload.boards.forEach(renderBoard);
    firstRender = false;
  }

  /* ------------------------------------------------------------ transport */

  function startPolling() {
    if (pollTimer) return;
    setStatus("polling", "Reconnecting — refreshing every " + POLL_INTERVAL_MS / 1000 + "s");

    var tick = function () {
      fetch("/api/leaderboard", { cache: "no-store" })
        .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
        .then(function (data) {
          render(data);
          /* The API is reachable, so try the stream again. */
          stopPolling();
          connect();
        })
        .catch(function () {
          setStatus("offline", "Cannot reach the server");
        });
    };

    tick();
    pollTimer = setInterval(tick, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  function connect() {
    if (source) source.close();

    setStatus("connecting", "Connecting…");
    source = new EventSource("/api/events");

    source.addEventListener("open", function () {
      failures = 0;
      stopPolling();
      setStatus("live", "Live");
    });

    source.addEventListener("leaderboard", function (event) {
      try {
        render(JSON.parse(event.data));
        setStatus("live", "Live");
      } catch (err) {
        /* A malformed payload should not take the display down. */
        setStatus("offline", "Received an unreadable update");
      }
    });

    source.addEventListener("error", function () {
      failures += 1;
      if (failures >= FAILURES_BEFORE_POLLING) {
        source.close();
        startPolling();
      } else {
        setStatus("connecting", "Reconnecting…");
      }
    });
  }

  connect();
})();
