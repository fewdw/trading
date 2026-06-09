import "server-only";
import type { LeaderboardEntry } from "./types";

const BACKEND_URL = process.env.BACKEND_URL;

/** The top holders by portfolio value (empty if unavailable). */
export async function getLeaderboard(): Promise<LeaderboardEntry[]> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/leaderboard`, {
      cache: "no-store",
      signal: AbortSignal.timeout(4000),
    });
    if (!res.ok) return [];
    return (await res.json()) as LeaderboardEntry[];
  } catch {
    return [];
  }
}
