import "server-only";
import { cookies } from "next/headers";

const BACKEND_URL = process.env.BACKEND_URL;
const SESSION_COOKIE = "session";

/** Persist the dark-mode preference to the backend (no-op if not logged in). */
export async function saveThemePreference(dark: boolean): Promise<void> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) return;
  try {
    await fetch(`${BACKEND_URL}/api/preferences`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ darkMode: dark }),
      cache: "no-store",
    });
  } catch {
    // Best-effort: the cookie/localStorage cache still reflects the choice.
  }
}
