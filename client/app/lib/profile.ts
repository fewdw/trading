import "server-only";
import type { ProfileData } from "./types";

const BACKEND_URL = process.env.BACKEND_URL;

/** Public profile for a username, or null if not found / unavailable. */
export async function getProfile(username: string): Promise<ProfileData | null> {
  try {
    const res = await fetch(
      `${BACKEND_URL}/api/users/${encodeURIComponent(username)}/profile`,
      { cache: "no-store", signal: AbortSignal.timeout(4000) },
    );
    if (!res.ok) return null;
    return (await res.json()) as ProfileData;
  } catch {
    return null;
  }
}
