import "server-only";
import { cookies } from "next/headers";
import type { Position } from "./types";

const BACKEND_URL = process.env.BACKEND_URL;
const SESSION_COOKIE = "session";

/** The logged-in user's portfolio positions (empty if not logged in). */
export async function getMyPortfolio(): Promise<Position[]> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) return [];
  try {
    const res = await fetch(`${BACKEND_URL}/api/portfolio`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!res.ok) return [];
    return (await res.json()) as Position[];
  } catch {
    return [];
  }
}
