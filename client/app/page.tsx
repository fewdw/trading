import type { Metadata } from "next";
import Link from "next/link";
import { getCurrentUser } from "./lib/auth";
import { getFighters } from "./lib/fighters";
import FighterGrid from "./components/FighterGrid";
import JsonLd from "./components/JsonLd";
import { SITE_NAME, SITE_URL, SITE_DESCRIPTION } from "./lib/site";

export const metadata: Metadata = {
  alternates: { canonical: "/" },
};

const websiteJsonLd = {
  "@context": "https://schema.org",
  "@type": "WebSite",
  name: SITE_NAME,
  alternateName: "Fighter Market — UFC fantasy stock market",
  url: SITE_URL,
  description: SITE_DESCRIPTION,
};

export default async function Home() {
  const [user, fighters] = await Promise.all([getCurrentUser(), getFighters()]);

  // Most expensive first. Array.prototype.sort is stable, so fighters at the
  // same price keep their original order. This runs at request time (the page is
  // force-dynamic), so the order is fixed per page load — live price ticks update
  // the numbers in place but never reorder until the next refresh.
  const sortedFighters = [...fighters].sort(
    (a, b) => b.lastPrice - a.lastPrice,
  );

  return (
    <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
      <JsonLd data={websiteJsonLd} />
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            {user ? `Welcome back, ${user.username}.` : "Fighter Market"}
          </h1>
          <p className="text-sm text-zinc-600 dark:text-zinc-400">
            Trade shares in UFC fighters.
            {!user && (
              <>
                {" "}
                <Link href="/login" className="underline">
                  Log in
                </Link>{" "}
                to trade.
              </>
            )}
          </p>
        </div>
      </div>

      {sortedFighters.length === 0 ? (
        <p className="text-sm text-zinc-500">
          No fighters available right now.
        </p>
      ) : (
        <FighterGrid fighters={sortedFighters} />
      )}
    </main>
  );
}
