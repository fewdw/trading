"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { Position, ProfileData } from "../lib/types";
import { slugify } from "../lib/slug";
import { formatCoins } from "../lib/format";
import { subscribeLive } from "../lib/ws";

type Live = { price: number; dir: "up" | "down" | null };

function tone(n: number): string {
  return n > 0
    ? "text-green-600 dark:text-green-400"
    : n < 0
      ? "text-red-600 dark:text-red-400"
      : "";
}

function signed(n: number): string {
  return `${n > 0 ? "+" : ""}${formatCoins(n)}`;
}

function seedFrom(holdings: Position[]): Record<number, Live> {
  return Object.fromEntries(
    holdings.map((h) => [h.fighterId, { price: h.lastPrice, dir: null }]),
  );
}

function Stat({
  label,
  value,
  className,
  accent,
}: {
  label: string;
  value: string;
  className?: string;
  accent?: string;
}) {
  return (
    <div
      className={`rounded-xl border bg-white p-4 dark:bg-zinc-950 ${
        accent ?? "border-zinc-200 dark:border-zinc-800"
      }`}
    >
      <div className="text-xs uppercase tracking-wide text-zinc-500">
        {label}
      </div>
      <div className={`mt-1 font-mono text-lg ${className ?? ""}`}>{value}</div>
    </div>
  );
}

function Avatar({ photo, name }: { photo: string | null; name: string }) {
  if (photo) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={photo}
        alt={name}
        className="h-8 w-8 shrink-0 rounded-full object-cover"
      />
    );
  }
  return (
    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-100 text-sm dark:bg-zinc-900">
      🥊
    </span>
  );
}

/**
 * The stat cards + holdings table, kept live over the market WebSocket: holding
 * prices, market values, unrealized P&L, holdings value and total P&L all update
 * in place as fighters trade. When the viewer's *own* balance changes (a fill on
 * their account) the page is refreshed so realized P&L and trade history catch
 * up too. Order never changes between server renders.
 */
export default function ProfilePortfolio({
  profile,
  isOwnProfile,
}: {
  profile: ProfileData;
  isOwnProfile: boolean;
}) {
  const router = useRouter();
  const [live, setLive] = useState<Record<number, Live>>(() =>
    seedFrom(profile.holdings),
  );

  // Reseed when the server sends fresh data (e.g. after a refresh below).
  const [seed, setSeed] = useState(profile.holdings);
  if (seed !== profile.holdings) {
    setSeed(profile.holdings);
    setLive(seedFrom(profile.holdings));
  }

  useEffect(
    () =>
      subscribeLive((data) => {
        const msg = data as {
          type?: string;
          fighterId?: number;
          lastPrice?: number;
        };
        if (
          msg.type === "MARKET_UPDATE" &&
          typeof msg.fighterId === "number" &&
          typeof msg.lastPrice === "number"
        ) {
          const id = msg.fighterId;
          const next = msg.lastPrice;
          setLive((prev) => {
            const cur = prev[id];
            if (!cur || cur.price === next) return prev; // not held / unchanged
            return {
              ...prev,
              [id]: { price: next, dir: next > cur.price ? "up" : "down" },
            };
          });
          return;
        }
        // A fill on the viewer's own account changes holdings + realized P&L,
        // which can't be derived from price alone — re-pull the server data.
        if (msg.type === "BALANCE_UPDATE" && isOwnProfile) {
          router.refresh();
        }
      }),
    [isOwnProfile, router],
  );

  const holdings = profile.holdings.map((h) => {
    const l = live[h.fighterId];
    const lastPrice = l?.price ?? h.lastPrice;
    return {
      ...h,
      lastPrice,
      dir: l?.dir ?? null,
      marketValue: h.quantity * lastPrice,
      unrealizedPnl: (lastPrice - h.averagePrice) * h.quantity,
    };
  });

  const holdingsValue = holdings.reduce((s, h) => s + h.marketValue, 0);
  const unrealizedPnl = holdings.reduce((s, h) => s + h.unrealizedPnl, 0);
  const totalPnl = profile.realizedPnl + unrealizedPnl;

  return (
    <>
      <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Stat label="Holdings value" value={formatCoins(holdingsValue)} />
        <Stat
          label="Unrealized P&L"
          value={signed(unrealizedPnl)}
          className={tone(unrealizedPnl)}
        />
        <Stat
          label="Realized P&L"
          value={signed(profile.realizedPnl)}
          className={tone(profile.realizedPnl)}
        />
        <Stat
          label="Total P&L"
          value={signed(totalPnl)}
          className={tone(totalPnl)}
          accent={
            totalPnl > 0
              ? "border-green-300 dark:border-green-500/40"
              : totalPnl < 0
                ? "border-red-300 dark:border-red-500/40"
                : "border-zinc-200 dark:border-zinc-800"
          }
        />
      </div>

      <section className="mt-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-zinc-500">
          Holdings
        </h2>
        {holdings.length === 0 ? (
          <p className="text-sm text-zinc-400">No holdings.</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-zinc-200 dark:border-zinc-800">
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-zinc-500">
                <tr className="border-b border-zinc-200 dark:border-zinc-800">
                  <th className="px-3 py-2">Fighter</th>
                  <th className="px-3 py-2 text-right">Shares</th>
                  <th className="px-3 py-2 text-right">Avg</th>
                  <th className="px-3 py-2 text-right">Price</th>
                  <th className="px-3 py-2 text-right">Value</th>
                  <th className="px-3 py-2 text-right">Unrealized</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {holdings.map((h) => (
                  <tr
                    key={h.fighterId}
                    className="border-b border-zinc-100 last:border-0 dark:border-zinc-900"
                  >
                    <td className="px-3 py-2 font-sans">
                      <Link
                        href={`/fighter/${slugify(h.fighterName)}`}
                        className="flex items-center gap-2 hover:underline"
                      >
                        <Avatar photo={h.photo} name={h.fighterName} />
                        <span className="capitalize">{h.fighterName}</span>
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-right">{h.quantity}</td>
                    <td className="px-3 py-2 text-right">
                      {formatCoins(h.averagePrice)}
                    </td>
                    <td
                      className={`px-3 py-2 text-right ${
                        h.dir === "up"
                          ? "text-green-600 dark:text-green-400"
                          : h.dir === "down"
                            ? "text-red-600 dark:text-red-400"
                            : ""
                      }`}
                    >
                      {h.dir === "up" && <span aria-hidden>▲ </span>}
                      {h.dir === "down" && <span aria-hidden>▼ </span>}
                      {formatCoins(h.lastPrice)}
                    </td>
                    <td className="px-3 py-2 text-right">
                      {formatCoins(h.marketValue)}
                    </td>
                    <td
                      className={`px-3 py-2 text-right ${tone(h.unrealizedPnl)}`}
                    >
                      {signed(h.unrealizedPnl)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
