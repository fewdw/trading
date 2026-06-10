"use client";

import { useEffect, useRef, useState } from "react";
import { subscribeLive } from "../lib/ws";
import { formatCoins } from "../lib/format";

type SpendMsg = {
  type?: string;
  username?: string;
  totalCoins?: number;
  delta?: number;
  fighter?: string;
};

type Flash = { id: number; delta: number; fighter: string };

function signed(subunits: number): string {
  return `${subunits > 0 ? "+" : "−"}${formatCoins(Math.abs(subunits))}`;
}

/**
 * A user's total coin balance — available plus coins reserved against open buy
 * orders — shown as one amount, and kept live over the WebSocket. Every public
 * SPEND_UPDATE frame for this profile's user updates the total in place and
 * flashes the spend (or income) in a small live feed, so visitors watch the
 * balance move as the user trades. The feed never reorders the page; it only
 * prepends transient chips that fade out on their own.
 */
export default function ProfileCoins({
  username,
  totalCoins,
}: {
  username: string;
  totalCoins: number;
}) {
  const [total, setTotal] = useState(totalCoins);
  const [flashes, setFlashes] = useState<Flash[]>([]);
  const [tone, setTone] = useState<"spend" | "income" | null>(null);
  const nextId = useRef(0);

  // Reseed from the server when fresh props arrive (navigation / refresh).
  const [seed, setSeed] = useState(totalCoins);
  if (seed !== totalCoins) {
    setSeed(totalCoins);
    setTotal(totalCoins);
  }

  useEffect(() => {
    const name = username.toLowerCase();
    return subscribeLive((data) => {
      const msg = data as SpendMsg;
      if (msg.type !== "SPEND_UPDATE") return;
      if (typeof msg.username !== "string") return;
      if (msg.username.toLowerCase() !== name) return;

      if (typeof msg.totalCoins === "number") setTotal(msg.totalCoins);

      const delta = typeof msg.delta === "number" ? msg.delta : 0;
      if (delta === 0) return;

      const flash: Flash = {
        id: nextId.current++,
        delta,
        fighter: msg.fighter ?? "",
      };
      setFlashes((prev) => [flash, ...prev].slice(0, 5));
      setTone(delta < 0 ? "spend" : "income");
      setTimeout(
        () => setFlashes((prev) => prev.filter((f) => f.id !== flash.id)),
        6000,
      );
    });
  }, [username]);

  // Let the highlight ring fade shortly after each update.
  useEffect(() => {
    if (!tone) return;
    const t = setTimeout(() => setTone(null), 800);
    return () => clearTimeout(t);
  }, [tone, total]);

  const ring =
    tone === "spend"
      ? "ring-2 ring-red-400/70"
      : tone === "income"
        ? "ring-2 ring-green-400/70"
        : "ring-0 ring-transparent";

  return (
    <section className="mt-6">
      <div className="flex flex-wrap items-center gap-3">
        <span
          className={`inline-flex items-center gap-2 rounded-full border border-amber-300 bg-amber-50 px-4 py-2 text-lg font-semibold text-amber-700 transition duration-300 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-400 ${ring}`}
          title="Total coins: available + reserved"
        >
          <span aria-hidden>🪙</span>
          <span className="font-mono">{formatCoins(total)}</span>
          <span className="text-sm font-normal text-amber-600/80 dark:text-amber-400/70">
            coins
          </span>
        </span>

        <div className="flex flex-wrap items-center gap-2" aria-live="polite">
          {flashes.map((f) => (
            <span
              key={f.id}
              className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 font-mono text-xs ${
                f.delta < 0
                  ? "bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400"
                  : "bg-green-50 text-green-600 dark:bg-green-500/10 dark:text-green-400"
              }`}
            >
              <span aria-hidden>{f.delta < 0 ? "▼" : "▲"}</span>
              {signed(f.delta)}
              {f.fighter && (
                <span className="font-sans capitalize opacity-80">
                  · {f.fighter}
                </span>
              )}
            </span>
          ))}
        </div>
      </div>
      <p className="mt-1 text-xs text-zinc-400">
        Total coins (available + reserved) — updates live as they trade.
      </p>
    </section>
  );
}
