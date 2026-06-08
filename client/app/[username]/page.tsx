import Link from "next/link";
import { notFound } from "next/navigation";
import { getProfile } from "../lib/profile";
import { slugify } from "../lib/slug";
import { formatCoins } from "../lib/format";

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

function Stat({
  label,
  value,
  className,
}: {
  label: string;
  value: string;
  className?: string;
}) {
  return (
    <div className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
      <div className="text-xs uppercase tracking-wide text-zinc-500">
        {label}
      </div>
      <div className={`mt-1 font-mono text-lg ${className ?? ""}`}>{value}</div>
    </div>
  );
}

export default async function ProfilePage({
  params,
}: {
  params: Promise<{ username: string }>;
}) {
  const { username } = await params;
  const profile = await getProfile(username);
  if (!profile) notFound();

  const totalPnl = profile.realizedPnl + profile.unrealizedPnl;

  return (
    <main className="mx-auto w-full max-w-4xl flex-1 px-6 py-8">
      <h1 className="text-2xl font-semibold">{profile.username}</h1>

      <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Stat label="Holdings value" value={formatCoins(profile.holdingsValue)} />
        <Stat
          label="Unrealized P&L"
          value={signed(profile.unrealizedPnl)}
          className={tone(profile.unrealizedPnl)}
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
        />
      </div>

      <section className="mt-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-zinc-500">
          Holdings
        </h2>
        {profile.holdings.length === 0 ? (
          <p className="text-sm text-zinc-400">No holdings.</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-zinc-200 dark:border-zinc-800">
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-zinc-500">
                <tr className="border-b border-zinc-200 dark:border-zinc-800">
                  <th className="px-3 py-2">Fighter</th>
                  <th className="px-3 py-2 text-right">Shares</th>
                  <th className="px-3 py-2 text-right">Avg</th>
                  <th className="px-3 py-2 text-right">Value</th>
                  <th className="px-3 py-2 text-right">Unrealized</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {profile.holdings.map((h) => (
                  <tr
                    key={h.fighterId}
                    className="border-b border-zinc-100 last:border-0 dark:border-zinc-900"
                  >
                    <td className="px-3 py-2 font-sans capitalize">
                      <Link
                        href={`/fighter/${slugify(h.fighterName)}`}
                        className="hover:underline"
                      >
                        {h.fighterName}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-right">{h.quantity}</td>
                    <td className="px-3 py-2 text-right">
                      {formatCoins(h.averagePrice)}
                    </td>
                    <td className="px-3 py-2 text-right">
                      {formatCoins(h.marketValue)}
                    </td>
                    <td className={`px-3 py-2 text-right ${tone(h.unrealizedPnl)}`}>
                      {signed(h.unrealizedPnl)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="mt-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-zinc-500">
          Trade history
        </h2>
        {profile.trades.length === 0 ? (
          <p className="text-sm text-zinc-400">No trades yet.</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-zinc-200 dark:border-zinc-800">
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-zinc-500">
                <tr className="border-b border-zinc-200 dark:border-zinc-800">
                  <th className="px-3 py-2">When</th>
                  <th className="px-3 py-2">Side</th>
                  <th className="px-3 py-2">Fighter</th>
                  <th className="px-3 py-2 text-right">Price</th>
                  <th className="px-3 py-2 text-right">Qty</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {profile.trades.map((t, i) => (
                  <tr
                    key={i}
                    className="border-b border-zinc-100 last:border-0 dark:border-zinc-900"
                  >
                    <td className="px-3 py-2 text-zinc-500">
                      {new Date(t.executedAt).toLocaleString()}
                    </td>
                    <td
                      className={`px-3 py-2 ${
                        t.side === "BUY"
                          ? "text-green-600 dark:text-green-400"
                          : "text-red-600 dark:text-red-400"
                      }`}
                    >
                      {t.side}
                    </td>
                    <td className="px-3 py-2 font-sans capitalize">
                      <Link
                        href={`/fighter/${slugify(t.fighterName)}`}
                        className="hover:underline"
                      >
                        {t.fighterName}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-right">
                      {formatCoins(t.price)}
                    </td>
                    <td className="px-3 py-2 text-right">{t.quantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
