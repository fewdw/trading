import Link from "next/link";
import type { Metadata } from "next";
import { getCurrentUser } from "../lib/auth";

export const metadata: Metadata = {
  title: "About — Fighter Market",
  description: "A fantasy stock market for UFC fighters.",
};

function Step({ n, title, children }: {
  n: number;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <li className="flex gap-4">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-900 font-mono text-sm text-white dark:bg-zinc-100 dark:text-zinc-900">
        {n}
      </span>
      <div>
        <h3 className="font-medium">{title}</h3>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">{children}</p>
      </div>
    </li>
  );
}

export default async function AboutPage() {
  const user = await getCurrentUser();

  return (
    <main className="mx-auto w-full max-w-2xl flex-1 px-6 py-10">
      <h1 className="text-3xl font-semibold tracking-tight">
        About Fighter Market
      </h1>
      <p className="mt-3 text-zinc-600 dark:text-zinc-400">
        Fighter Market is a fantasy stock market for UFC fighters. Instead of
        companies, the &ldquo;stocks&rdquo; are fighters — and their prices move
        based on what other players are willing to pay. It&apos;s play money, so
        there&apos;s nothing real at stake: just bragging rights for the biggest
        portfolio.
      </p>

      <section className="mt-8">
        <h2 className="mb-4 text-sm font-medium uppercase tracking-wide text-zinc-500">
          How it works
        </h2>
        <ol className="flex flex-col gap-5">
          <Step n={1} title="Start with coins">
            Every new account gets 1,000 coins to trade with — and a 500-coin
            paycheck every two weeks, so you can keep trading even if a few bets
            don&apos;t pan out. No deposits, no real money.
          </Step>
          <Step n={2} title="Buy and sell shares of fighters">
            Place a <strong>market</strong> order to trade instantly at the best
            available price, or a <strong>limit</strong> order to set your own
            price and wait for someone to match it.
          </Step>
          <Step n={3} title="Prices are set by a live order book">
            There&apos;s no house setting prices. Every buy is matched against
            someone else&apos;s sell using price/time priority — the same way a
            real exchange works. A fighter&apos;s price is simply the last price
            two players traded at.
          </Step>
          <Step n={4} title="Track your profit and loss">
            Your profile shows your holdings, realized and unrealized P&amp;L,
            and your full order history — including pending and cancelled orders.
          </Step>
        </ol>
      </section>

      <section className="mt-10">
        <h2 className="mb-3 text-sm font-medium uppercase tracking-wide text-zinc-500">
          Good to know
        </h2>
        <ul className="flex list-disc flex-col gap-2 pl-5 text-sm text-zinc-600 dark:text-zinc-400">
          <li>Coins and prices use sub-units internally (1 coin = 100), so you can trade in fractions of a coin.</li>
          <li>You can&apos;t trade against your own resting orders.</li>
          <li>Prices and balances update live as other players trade.</li>
        </ul>
      </section>

      <div className="mt-10 flex items-center gap-4">
        {!user && (
          <Link
            href="/signup"
            className="rounded-md bg-zinc-900 px-4 py-2 text-sm text-white hover:bg-zinc-700 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
          >
            Create an account
          </Link>
        )}
        <Link
          href="/"
          className={
            user
              ? "rounded-md bg-zinc-900 px-4 py-2 text-sm text-white hover:bg-zinc-700 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
              : "text-sm text-zinc-500 underline"
          }
        >
          Browse fighters
        </Link>
      </div>
    </main>
  );
}
