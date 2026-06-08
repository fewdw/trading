import "server-only";
import { cookies } from "next/headers";
import type { Order } from "./types";

const BACKEND_URL = process.env.BACKEND_URL;
const SESSION_COOKIE = "session";

async function sessionToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value ?? null;
}

/** The logged-in user's orders (empty if not logged in). */
export async function getMyOrders(): Promise<Order[]> {
  const token = await sessionToken();
  if (!token) return [];
  try {
    const res = await fetch(`${BACKEND_URL}/api/orders`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!res.ok) return [];
    return (await res.json()) as Order[];
  } catch {
    return [];
  }
}
