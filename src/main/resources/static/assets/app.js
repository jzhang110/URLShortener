// Thin same-origin client for the URL shortener API. The backend is authoritative for every rule;
// this script only sends requests and renders responses. Every API value is untrusted and is
// rendered with textContent only. State lives in memory; nothing is persisted or logged.
"use strict";

(function () {
  const EMPTY = "—";
  const byId = (id) => document.getElementById(id);

  const form = byId("shorten-form");
  const urlInput = byId("url");
  const message = byId("message");
  const result = byId("result");
  const openLink = byId("open");

  let current = null; // { shortCode, shortUrl } of the last successful shorten

  function show(text, isError) {
    message.textContent = text;
    message.classList.toggle("error", Boolean(isError));
  }

  function setText(id, value) {
    byId(id).textContent = value === null || value === undefined || value === "" ? EMPTY : String(value);
  }

  async function request(method, path, body) {
    const init = { method: method, headers: { Accept: "application/json" } };
    if (body !== undefined) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
    let response;
    try {
      response = await fetch(path, init);
    } catch (e) {
      return { ok: false, status: 0, data: null };
    }
    const type = response.headers.get("Content-Type") || "";
    let data = null;
    if (type.includes("json")) {
      try {
        data = await response.json();
      } catch (e) {
        data = null;
      }
    }
    return { ok: response.ok, status: response.status, data: data };
  }

  // Only the client-safe Problem Details fields are shown, as plain text.
  function showProblem(res) {
    if (res.status === 0) {
      show("Network error: the server could not be reached.", true);
      return;
    }
    const problem = res.data && typeof res.data === "object" ? res.data : null;
    const lines = [];
    if (problem && typeof problem.code === "string") {
      lines.push(problem.code + (typeof problem.detail === "string" ? ": " + problem.detail : ""));
    } else {
      lines.push("Request failed (HTTP " + res.status + ").");
    }
    if (problem && typeof problem.correlationId === "string") {
      lines.push("Correlation ID: " + problem.correlationId);
    }
    show(lines.join("\n"), true);
  }

  function codePath(suffix) {
    return "/api/v1/urls/" + encodeURIComponent(current.shortCode) + suffix;
  }

  async function shorten(event) {
    event.preventDefault();
    const res = await request("POST", "/api/v1/urls", { url: urlInput.value });
    if (!res.ok || !res.data) {
      showProblem(res);
      return;
    }
    const data = res.data;
    current = { shortCode: String(data.shortCode), shortUrl: String(data.shortUrl) };
    setText("short-code", data.shortCode);
    setText("short-url", data.shortUrl);
    setText("destination-url", data.destinationUrl);
    setText("created-at", data.createdAt);
    // Same-origin relative path built from the backend's short code, never from the destination.
    openLink.href = "/" + encodeURIComponent(current.shortCode);
    ["total-clicks", "last-clicked-at", "status", "deactivated-at"].forEach((id) => setText(id, null));
    result.hidden = false;
    show(res.status === 201
        ? "Created a new short URL."
        : "Existing short URL returned (an equivalent URL was already shortened).");
    await refreshAnalytics();
  }

  async function refreshAnalytics() {
    if (!current) {
      return;
    }
    const res = await request("GET", codePath("/analytics"));
    if (!res.ok || !res.data) {
      showProblem(res);
      return;
    }
    setText("total-clicks", res.data.totalClicks);
    setText("last-clicked-at", res.data.lastClickedAt);
  }

  async function deactivate() {
    if (!current) {
      return;
    }
    const res = await request("POST", codePath("/deactivate"));
    if (!res.ok || !res.data) {
      showProblem(res);
      return;
    }
    setText("status", res.data.status);
    setText("deactivated-at", res.data.deactivatedAt);
    show("Deactivate request completed.");
  }

  async function copy() {
    if (!current) {
      return;
    }
    try {
      await navigator.clipboard.writeText(current.shortUrl);
      show("Short URL copied.");
    } catch (e) {
      show("Copy failed: the clipboard is unavailable in this browser context.", true);
    }
  }

  form.addEventListener("submit", shorten);
  byId("refresh").addEventListener("click", refreshAnalytics);
  byId("deactivate").addEventListener("click", deactivate);
  byId("copy").addEventListener("click", copy);
})();
