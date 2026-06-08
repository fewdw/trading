"use client";

import { useSyncExternalStore } from "react";
import { setThemePreference } from "../actions/preferences";

const YEAR = 60 * 60 * 24 * 365;
const THEME_EVENT = "themechange";

// The applied theme lives outside React, on <html> (set by the inline layout
// script before paint). Read it with useSyncExternalStore so there's no effect,
// no setState-in-effect, and no hydration mismatch.
function subscribe(callback: () => void) {
  window.addEventListener(THEME_EVENT, callback);
  window.addEventListener("storage", callback);
  return () => {
    window.removeEventListener(THEME_EVENT, callback);
    window.removeEventListener("storage", callback);
  };
}
const getSnapshot = () =>
  document.documentElement.classList.contains("dark");
const getServerSnapshot = () => false;

/**
 * Dark-mode switch. Flips the class on <html> immediately, then persists to
 * localStorage, the SSR cookie, and the backend so the choice sticks locally
 * and across devices.
 */
export default function ThemeToggle() {
  const dark = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  function toggle() {
    const next = !dark;
    document.documentElement.classList.toggle("dark", next);
    try {
      localStorage.setItem("theme", next ? "dark" : "light");
    } catch {
      // ignore storage failures (private mode, etc.)
    }
    document.cookie = `theme=${next ? "dark" : "light"}; path=/; max-age=${YEAR}; samesite=lax`;
    window.dispatchEvent(new Event(THEME_EVENT));
    void setThemePreference(next);
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-pressed={dark}
      className="inline-flex items-center gap-2 rounded-md border border-zinc-300 px-3 py-1.5 text-sm hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
    >
      <span aria-hidden suppressHydrationWarning>
        {dark ? "🌙" : "☀️"}
      </span>
      <span suppressHydrationWarning>{dark ? "Dark mode" : "Light mode"}</span>
    </button>
  );
}
