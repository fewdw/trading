import type { Metadata } from "next";
import { cache } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getProfile, getProfileHistory } from "../lib/profile";
import { getCurrentUser } from "../lib/auth";
import { slugify } from "../lib/slug";
import { formatCoins } from "../lib/format";
import OrderStatusBadge from "../components/OrderStatusBadge";
import ThemeToggle from "../components/ThemeToggle";
import ProfilePortfolio from "../components/ProfilePortfolio";
import PortfolioChart from "../components/PortfolioChart";

// Shared by generateMetadata and the page so the profile is fetched once.
const loadProfile = cache(getProfile);

export async function generateMetadata({
  params,
}: {
  params: Promise<{ username: string }>;
}): Promise<Metadata> {
  const { username } = await params;
  const profile = await loadProfile(username);
  if (!profile) return { title: "User not found", robots: { index: false } };

  const title = `${profile.username} — portfolio`;
  const description = `${profile.username}'s portfolio on Fighter Market: holdings, realized and unrealized P&L, and full order history.`;
  const canonical = `/${profile.username}`;

  return {
    title,
    description,
    alternates: { canonical },
    openGraph: {
      title: `${title} · Fighter Market`,
      description,
      type: "profile",
      url: canonical,
    },
  };
}

export default async function ProfilePage({
  params,
}: {
  params: Promise<{ username: string }>;
}) {
  const { username } = await params;
  const [profile, me] = await Promise.all([
    loadProfile(username),
    getCurrentUser(),
  ]);
  if (!profile) notFound();

  const history = await getProfileHistory(profile.username);
  const isOwnProfile =
    me?.username.toLowerCase() === profile.username.toLowerCase();

  const payoutDate = profile.nextPayoutAt
    ? new Date(profile.nextPayoutAt)
    : null;

  return (
    <main className="mx-auto w-full max-w-4xl flex-1 px-6 py-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-green-500 to-emerald-700 text-2xl font-semibold uppercase text-white shadow-sm">
            {profile.username.charAt(0)}
          </span>
          <div>
            <h1 className="text-2xl font-semibold">{profile.username}</h1>
            {payoutDate && (
              <p className="text-sm text-zinc-500">
                Next payout:{" "}
                <span className="font-medium text-zinc-700 dark:text-zinc-300">
                  +{formatCoins(profile.payoutAmount)} coins
                </span>{" "}
                on{" "}
                {payoutDate.toLocaleDateString(undefined, {
                  weekday: "short",
                  month: "short",
                  day: "numeric",
                  year: "numeric",
                })}
              </p>
            )}
          </div>
        </div>
        {isOwnProfile && <ThemeToggle />}
      </div>

      <ProfilePortfolio profile={profile} isOwnProfile={isOwnProfile} />

      <section className="mt-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-zinc-500">
          Portfolio over time
        </h2>
        <div className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
          <PortfolioChart data={history} />
        </div>
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

      <section className="mt-8">
        <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-zinc-500">
          Orders
        </h2>
        <p className="mb-2 text-xs text-zinc-400">
          Every order placed — including pending (open/partial) and cancelled
          buys and sells.
        </p>
        {profile.orders.length === 0 ? (
          <p className="text-sm text-zinc-400">No orders yet.</p>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-zinc-200 dark:border-zinc-800">
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-zinc-500">
                <tr className="border-b border-zinc-200 dark:border-zinc-800">
                  <th className="px-3 py-2">When</th>
                  <th className="px-3 py-2">Side</th>
                  <th className="px-3 py-2">Type</th>
                  <th className="px-3 py-2">Fighter</th>
                  <th className="px-3 py-2 text-right">Price</th>
                  <th className="px-3 py-2 text-right">Filled</th>
                  <th className="px-3 py-2">Status</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {profile.orders.map((o) => (
                  <tr
                    key={o.id}
                    className="border-b border-zinc-100 last:border-0 dark:border-zinc-900"
                  >
                    <td className="px-3 py-2 text-zinc-500">
                      {new Date(o.createdAt).toLocaleString()}
                    </td>
                    <td
                      className={`px-3 py-2 ${
                        o.side === "BUY"
                          ? "text-green-600 dark:text-green-400"
                          : "text-red-600 dark:text-red-400"
                      }`}
                    >
                      {o.side}
                    </td>
                    <td className="px-3 py-2 text-zinc-500">
                      {o.type.toLowerCase()}
                    </td>
                    <td className="px-3 py-2 font-sans capitalize">
                      <Link
                        href={`/fighter/${slugify(o.fighterName)}`}
                        className="hover:underline"
                      >
                        {o.fighterName}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-right">
                      {o.limitPrice !== null ? formatCoins(o.limitPrice) : "—"}
                    </td>
                    <td className="px-3 py-2 text-right">
                      {o.filledQuantity}/{o.quantity}
                    </td>
                    <td className="px-3 py-2 font-sans">
                      <OrderStatusBadge status={o.status} />
                    </td>
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
