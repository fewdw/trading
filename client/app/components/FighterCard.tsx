import Link from "next/link";
import type { Fighter } from "../lib/types";
import { slugify } from "../lib/slug";
import { formatCoins } from "../lib/format";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE:
    "bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-400",
  UNLISTED: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400",
};

export default function FighterCard({ fighter }: { fighter: Fighter }) {
  const badge =
    STATUS_STYLES[fighter.status] ??
    "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400";

  return (
    <Link
      href={`/fighter/${slugify(fighter.name)}`}
      className="group flex flex-col overflow-hidden rounded-xl border border-zinc-200 bg-white transition hover:-translate-y-0.5 hover:shadow-lg dark:border-zinc-800 dark:bg-zinc-950"
    >
      <div className="aspect-square overflow-hidden bg-zinc-100 dark:bg-zinc-900">
        {fighter.photo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={fighter.photo}
            alt={fighter.name}
            className="h-full w-full object-cover transition group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-4xl">
            🥊
          </div>
        )}
      </div>
      <div className="flex flex-col gap-2 p-3">
        <span className="truncate font-medium capitalize">{fighter.name}</span>
        <div className="flex items-center justify-between">
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${badge}`}>
            {fighter.status}
          </span>
          <span className="font-mono text-sm">
            🪙 {formatCoins(fighter.lastPrice)}
          </span>
        </div>
      </div>
    </Link>
  );
}
