"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { Fighter } from "../lib/types";
import { slugify } from "../lib/slug";
import { formatCoins } from "../lib/format";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-400",
  UNLISTED: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400",
};

type Live = { price: number; dir: "up" | "down" | null };

function seedFrom(fighters: Fighter[]): Record<number, Live> {
  return Object.fromEntries(
    fighters.map((f) => [f.id, { price: f.lastPrice, dir: null }]),
  );
}

/** Fighter grid with live prices over WebSocket: ▲ green up, ▼ red down. */
export default function FighterGrid({ fighters }: { fighters: Fighter[] }) {
  const [live, setLive] = useState<Record<number, Live>>(() =>
    seedFrom(fighters),
  );

  // Reseed if the server sends a fresh list (render-time adjust, not an effect).
  const [seed, setSeed] = useState(fighters);
  if (seed !== fighters) {
    setSeed(fighters);
    setLive(seedFrom(fighters));
  }

  useEffect(() => {
    const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
    const url = `${proto}//${window.location.hostname}:8080/ws`;
    let socket: WebSocket | null = null;
    let stopped = false;
    let retry: ReturnType<typeof setTimeout> | undefined;

    const connect = () => {
      socket = new WebSocket(url);
      socket.onmessage = (e) => {
        try {
          const msg = JSON.parse(e.data) as {
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
              if (!cur || cur.price === next) return prev;
              return {
                ...prev,
                [id]: { price: next, dir: next > cur.price ? "up" : "down" },
              };
            });
          }
        } catch {
          // ignore malformed frames
        }
      };
      socket.onclose = () => {
        if (!stopped) retry = setTimeout(connect, 3000);
      };
    };

    connect();
    return () => {
      stopped = true;
      if (retry) clearTimeout(retry);
      socket?.close();
    };
  }, []);

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
      {fighters.map((f) => {
        const l = live[f.id] ?? { price: f.lastPrice, dir: null };
        const badge =
          STATUS_STYLES[f.status] ??
          "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400";
        const priceTone =
          l.dir === "up"
            ? "text-green-600 dark:text-green-400"
            : l.dir === "down"
              ? "text-red-600 dark:text-red-400"
              : "";
        return (
          <Link
            key={f.id}
            href={`/fighter/${slugify(f.name)}`}
            className="group flex flex-col overflow-hidden rounded-xl border border-zinc-200 bg-white transition hover:-translate-y-0.5 hover:shadow-lg dark:border-zinc-800 dark:bg-zinc-950"
          >
            <div className="aspect-square overflow-hidden bg-zinc-100 dark:bg-zinc-900">
              {f.photo ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={f.photo}
                  alt={f.name}
                  className="h-full w-full object-cover transition group-hover:scale-105"
                />
              ) : (
                <div className="flex h-full w-full items-center justify-center text-4xl">
                  🥊
                </div>
              )}
            </div>
            <div className="flex flex-col gap-2 p-3">
              <span className="truncate font-medium capitalize">{f.name}</span>
              <div className="flex items-center justify-between">
                <span
                  className={`rounded-full px-2 py-0.5 text-xs font-medium ${badge}`}
                >
                  {f.status}
                </span>
                <span
                  className={`flex items-center gap-0.5 font-mono text-sm transition-colors ${priceTone}`}
                >
                  {l.dir === "up" && <span aria-hidden>▲</span>}
                  {l.dir === "down" && <span aria-hidden>▼</span>}
                  {formatCoins(l.price)}
                </span>
              </div>
            </div>
          </Link>
        );
      })}
    </div>
  );
}
