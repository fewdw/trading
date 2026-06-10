import type { Metadata } from "next";
import Link from "next/link";
import { getLeaderboard } from "../lib/leaderboard";
import { getCurrentUser } from "../lib/auth";
import { formatCoins } from "../lib/format";

export const metadata: Metadata = {
  title: "Leaderboard",
  description:
    "The top holders on Fighter Market, ranked by total portfolio value.",
  alternates: { canonical: "/leaderboard" },
};

const MEDALS: Record<number, string> = { 1: "🥇", 2: "🥈", 3: "🥉" };

export default async function LeaderboardPage() {
  const [entries, me] = await Promise.all([
    getLeaderboard(),
    getCurrentUser(),
  ]);

  return (
    <main className="mx-auto w-full max-w-2xl flex-1 px-6 py-8">
      <h1 className="text-2xl font-semibold tracking-tight">Leaderboard</h1>
      <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">
        Top 10 holders by total portfolio value.
      </p>

      {entries.length === 0 ? (
        <p className="mt-8 text-sm text-zinc-500">
          No holders yet — be the first to buy some shares.
        </p>
      ) : (
        <ol className="mt-6 flex flex-col gap-2">
          {entries.map((e) => {
            const isMe =
              me?.username.toLowerCase() === e.username.toLowerCase();
            return (
              <li key={e.username}>
                <Link
                  href={`/${e.username}`}
                  className={`flex items-center gap-4 rounded-xl border px-4 py-3 transition hover:-translate-y-0.5 hover:shadow-sm ${
                    isMe
                      ? "border-green-400 bg-green-50 dark:border-green-500/40 dark:bg-green-500/10"
                      : "border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950"
                  }`}
                >
                  <span className="w-8 shrink-0 text-center text-lg font-semibold tabular-nums">
                    {MEDALS[e.rank] ?? e.rank}
                  </span>
                  <span className="flex-1 truncate font-medium">
                    {e.isBot && (
                      <span
                        className="mr-1"
                        role="img"
                        aria-label="AI trading agent"
                        title="AI trading agent"
                      >
                        🤖
                      </span>
                    )}
                    {e.username}
                    {isMe && (
                      <span className="ml-2 text-xs text-green-600 dark:text-green-400">
                        you
                      </span>
                    )}
                  </span>
                  <span className="shrink-0 font-mono text-sm">
                    {formatCoins(e.holdingsValue)}
                    <span className="ml-1 text-xs text-zinc-400">coins</span>
                  </span>
                </Link>
              </li>
            );
          })}
        </ol>
      )}
    </main>
  );
}
