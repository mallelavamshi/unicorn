// Unicorn website: live hunches for the visitor's area, the rewrite demo, and the early-access form.

const API = "https://unicorn-api.m-vamshikrishna22.workers.dev";

// Live hunches -------------------------------------------------------------------------------------------------------
// The API guesses the area from the connection (nothing is sent but the time zone) and doesn't store it.

async function loadHunches() {
  const list = document.querySelector("[data-hunches]");
  if (!list) return;
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const res = await fetch(`${API}/v1/hunches?tz=${encodeURIComponent(tz)}`);
    if (!res.ok) return;
    const data = await res.json();
    const hunches = (data.hunches || []).slice(0, 4);
    if (hunches.length === 0) return;
    const town = data.place?.hood || data.place?.city || data.place?.region;
    if (town) document.querySelector("[data-sky-title]").textContent = `Probably on ${town}'s mind`;
    list.replaceChildren(...hunches.map((h) => {
      const li = document.createElement("li");
      const emoji = Object.assign(document.createElement("span"), { className: "emoji", textContent: h.emoji });
      const text = Object.assign(document.createElement("span"), { className: "text", textContent: h.text });
      const because = Object.assign(document.createElement("span"), { className: "because", textContent: h.why });
      li.append(emoji, text, because);
      return li;
    }));
    const credits = document.querySelector("[data-credits]");
    if (credits && data.credits?.length) credits.textContent = data.credits.join(". ");
  } catch {
    // The examples already on the page stay
  }
}

// Rewrite demo -------------------------------------------------------------------------------------------------------

const REWRITES = {
  polite: "Hi! I'm so sorry, but I can't make it tomorrow night. Something came up at work. Could we do Saturday instead?",
  funny: "Bad news: work just body-checked my plans for tomorrow night. Rain check for Saturday? Snacks are on me as an apology.",
  pro: "Apologies, something came up at work and I won't be able to make it tomorrow evening. Would Saturday work for you instead?",
  short: "Sorry, can't make tomorrow night. Work thing. Saturday?",
};

function setupRewrite() {
  const out = document.querySelector("[data-rewrite-out]");
  const buttons = document.querySelectorAll("[data-tone]");
  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      buttons.forEach((b) => b.setAttribute("aria-pressed", String(b === button)));
      out.textContent = REWRITES[button.dataset.tone];
    });
  });
}

// Early access -------------------------------------------------------------------------------------------------------

function setupSignup() {
  document.querySelectorAll("[data-signup]").forEach((form) => {
    const note = form.querySelector(".form-note");
    const button = form.querySelector("button");
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const email = form.email.value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        note.className = "form-note error";
        note.textContent = "Enter an email address like you@email.com.";
        form.email.focus();
        return;
      }
      button.disabled = true;
      try {
        const res = await fetch(`${API}/v1/waitlist`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, platform: form.platform.value }),
        });
        const body = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(body.error || "That didn't go through. Try again in a minute.");
        note.className = "form-note ok";
        note.textContent = form.platform.value === "iphone"
          ? "You're on the list. We'll email you when Unicorn is ready for iPhone."
          : "You're on the list. Watch your inbox for your invite.";
        form.email.value = "";
      } catch (error) {
        note.className = "form-note error";
        note.textContent = error.message || "That didn't go through. Try again in a minute.";
      } finally {
        button.disabled = false;
      }
    });
  });
}

loadHunches();
setupRewrite();
setupSignup();
