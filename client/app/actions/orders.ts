"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";

const BACKEND_URL = process.env.BACKEND_URL;
const SESSION_COOKIE = "session";

export type OrderFormState = { error?: string; success?: string } | undefined;

async function sessionToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value ?? null;
}

export async function placeOrderAction(
  _prev: OrderFormState,
  formData: FormData,
): Promise<OrderFormState> {
  const token = await sessionToken();
  if (!token) return { error: "You must be logged in to trade." };

  const fighterId = String(formData.get("fighterId") ?? "");
  const slug = String(formData.get("slug") ?? "");
  const side = String(formData.get("side") ?? "");
  const type = String(formData.get("type") ?? "");
  const quantity = Number(formData.get("quantity") ?? 0);
  const limitPriceRaw = String(formData.get("limitPrice") ?? "").trim();

  if (!fighterId) return { error: "Missing fighter." };
  if (side !== "BUY" && side !== "SELL") return { error: "Choose buy or sell." };
  if (type !== "LIMIT" && type !== "MARKET") {
    return { error: "Choose an order type." };
  }
  if (!Number.isFinite(quantity) || quantity <= 0) {
    return { error: "Quantity must be a positive whole number." };
  }

  const body: Record<string, unknown> = {
    fighterId,
    side,
    type,
    quantity: Math.floor(quantity),
  };

  if (type === "LIMIT") {
    // Price is entered in coins; the backend stores sub-units (1 coin = 100).
    const coins = Number(limitPriceRaw);
    if (!Number.isFinite(coins) || coins <= 0) {
      return { error: "Enter a valid limit price." };
    }
    body.limitPrice = Math.round(coins * 100);
  }

  try {
    const res = await fetch(`${BACKEND_URL}/api/orders`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(body),
      cache: "no-store",
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      // Spring's ResponseStatusException puts the real reason in `message`
      // ("insufficient shares", …) and only "Bad Request" in `error`.
      const d = data as { message?: string; error?: string };
      return { error: d.message ?? d.error ?? "Order rejected." };
    }
  } catch {
    return { error: "Could not reach the server." };
  }

  if (slug) revalidatePath(`/fighter/${slug}`);
  return { success: "Order placed." };
}

export async function cancelOrderAction(formData: FormData): Promise<void> {
  const token = await sessionToken();
  if (!token) return;

  const orderId = String(formData.get("orderId") ?? "");
  const slug = String(formData.get("slug") ?? "");
  if (!orderId) return;

  try {
    await fetch(`${BACKEND_URL}/api/orders/${orderId}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
  } catch {
    // ignore — the page will reflect the true state on refresh
  }

  if (slug) revalidatePath(`/fighter/${slug}`);
}
