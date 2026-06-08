"use server";

import { setThemeCookie } from "../lib/auth";
import { saveThemePreference } from "../lib/preferences";

/**
 * Persist the user's dark-mode choice: the SSR cookie (so the next render has no
 * flash) and the backend (so it follows them across devices). The client also
 * mirrors it to localStorage and flips the class immediately for instant feedback.
 */
export async function setThemePreference(dark: boolean): Promise<void> {
  await setThemeCookie(dark);
  await saveThemePreference(dark);
}
