import "server-only";
import type { Fighter, Orderbook, Trade } from "./types";
import { slugify } from "./slug";

const BACKEND_URL = process.env.BACKEND_URL;

// A short timeout so a slow/unavailable backend never hangs a render (notably
// during `next build`, when the backend container isn't running yet).
const FETCH_TIMEOUT_MS = 4000;

export async function getFighters(): Promise<Fighter[]> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/fighters`, {
      cache: "no-store",
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    if (!res.ok) return [];
    return (await res.json()) as Fighter[];
  } catch {
    return [];
  }
}

export async function getFighterById(id: number): Promise<Fighter | null> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/fighters/${id}`, {
      cache: "no-store",
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    if (!res.ok) return null;
    return (await res.json()) as Fighter;
  } catch {
    return null;
  }
}

/** Resolve a slug to a fighter, then load fresh detail by id. */
export async function getFighterBySlug(slug: string): Promise<Fighter | null> {
  const match = (await getFighters()).find((f) => slugify(f.name) === slug);
  if (!match) return null;
  return (await getFighterById(match.id)) ?? match;
}

export async function getOrderbook(id: number): Promise<Orderbook> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/fighters/${id}/orderbook`, {
      cache: "no-store",
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    if (!res.ok) return { bids: [], asks: [] };
    return (await res.json()) as Orderbook;
  } catch {
    return { bids: [], asks: [] };
  }
}

export async function getTrades(id: number): Promise<Trade[]> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/fighters/${id}/trades`, {
      cache: "no-store",
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    if (!res.ok) return [];
    return (await res.json()) as Trade[];
  } catch {
    return [];
  }
}
