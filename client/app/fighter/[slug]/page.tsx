import Link from "next/link";
import { notFound } from "next/navigation";
import { getCurrentUser } from "../../lib/auth";
import {
  getFighterBySlug,
  getOrderbook,
  getTrades,
} from "../../lib/fighters";
import { getMyOrders } from "../../lib/orders";
import { getMyPortfolio } from "../../lib/portfolio";
import { formatCoins } from "../../lib/format";
import OrderBook from "../../components/OrderBook";
import PriceChart from "../../components/PriceChart";
import PlaceOrderForm from "../../components/PlaceOrderForm";
import FighterLiveRefresh from "../../components/FighterLiveRefresh";
import { cancelOrderAction } from "../../actions/orders";

export default async function FighterPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const fighter = await getFighterBySlug(slug);
  if (!fighter) notFound();

  const [book, trades, user] = await Promise.all([
    getOrderbook(fighter.id),
    getTrades(fighter.id),
    getCurrentUser(),
  ]);

  const openOrders = user
    ? (await getMyOrders()).filter(
        (o) =>
          o.fighterId === fighter.id &&
          (o.status === "OPEN" || o.status === "PARTIAL"),
      )
    : [];

  const position = user
    ? ((await getMyPortfolio()).find((p) => p.fighterId === fighter.id) ?? null)
    : null;

  const bestBid = book.bids[0]?.price ?? null;
  const bestAsk = book.asks[0]?.price ?? null;

  return (
    <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-8">
      <FighterLiveRefresh fighterId={fighter.id} />

      <Link href="/" className="text-sm text-zinc-500 underline">
        ← All fighters
      </Link>

      <div className="mt-4 flex items-center gap-4">
        {fighter.photo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={fighter.photo}
            alt={fighter.name}
            className="h-20 w-20 rounded-xl object-cover"
          />
        ) : (
          <div className="flex h-20 w-20 items-center justify-center rounded-xl bg-zinc-100 text-3xl dark:bg-zinc-900">
            🥊
          </div>
        )}
        <div>
          <h1 className="text-2xl font-semibold capitalize">{fighter.name}</h1>
          <p className="text-sm text-zinc-500">{fighter.status}</p>
          <p className="mt-1 font-mono text-lg">
            {formatCoins(fighter.lastPrice)}
          </p>
        </div>
      </div>

      <div className="mt-8 grid gap-8 md:grid-cols-3">
        <div className="flex flex-col gap-8 md:col-span-2">
          <section>
            <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-zinc-500">
              Price history
            </h2>
            <PriceChart trades={trades} />
          </section>

          <section>
            <div className="mb-2 flex items-center justify-between">
              <h2 className="text-sm font-medium uppercase tracking-wide text-zinc-500">
                Order book
              </h2>
              <span className="font-mono text-xs text-zinc-500">
                {bestBid !== null && `bid ${formatCoins(bestBid)}`}
                {bestBid !== null && bestAsk !== null && " · "}
                {bestAsk !== null && `ask ${formatCoins(bestAsk)}`}
              </span>
            </div>
            <OrderBook book={book} />
          </section>

          <section>
            <h2 className="mb-2 text-sm font-medium uppercase tracking-wide text-zinc-500">
              Recent trades
            </h2>
            {trades.length === 0 ? (
              <p className="text-sm text-zinc-400">No trades yet.</p>
            ) : (
              <ul className="flex flex-col gap-0.5 font-mono text-sm">
                {trades.slice(0, 12).map((t, i) => (
                  <li key={i} className="flex justify-between gap-4">
                    <span>{formatCoins(t.price)}</span>
                    <span className="text-zinc-500">{t.quantity}</span>
                    <span className="text-zinc-400">
                      {new Date(t.executedAt).toLocaleTimeString()}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <div className="flex flex-col gap-6">
          <section className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
            <h2 className="mb-3 text-sm font-medium uppercase tracking-wide text-zinc-500">
              Place order
            </h2>
            {user ? (
              fighter.status === "ACTIVE" ? (
                <PlaceOrderForm fighterId={fighter.id} slug={slug} />
              ) : (
                <p className="text-sm text-zinc-500">
                  This fighter isn&apos;t tradable yet (status {fighter.status}).
                </p>
              )
            ) : (
              <p className="text-sm text-zinc-500">
                <Link href="/login" className="underline">
                  Log in
                </Link>{" "}
                to trade.
              </p>
            )}
          </section>

          {user && (
            <section className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
              <h2 className="mb-3 text-sm font-medium uppercase tracking-wide text-zinc-500">
                Your position
              </h2>
              {position && position.quantity > 0 ? (
                <dl className="flex flex-col gap-1 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-zinc-500">Shares</dt>
                    <dd className="font-mono">
                      {position.quantity}
                      {position.reservedQuantity > 0 && (
                        <span className="text-zinc-400">
                          {" "}
                          ({position.reservedQuantity} reserved)
                        </span>
                      )}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-zinc-500">Avg price</dt>
                    <dd className="font-mono">
                      {formatCoins(position.averagePrice)}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-zinc-500">Market value</dt>
                    <dd className="font-mono">
                      {formatCoins(position.marketValue)}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-zinc-500">Unrealized P&amp;L</dt>
                    <dd
                      className={`font-mono ${
                        position.unrealizedPnl > 0
                          ? "text-green-600 dark:text-green-400"
                          : position.unrealizedPnl < 0
                            ? "text-red-600 dark:text-red-400"
                            : ""
                      }`}
                    >
                      {position.unrealizedPnl > 0 ? "+" : ""}
                      {formatCoins(position.unrealizedPnl)}
                    </dd>
                  </div>
                </dl>
              ) : (
                <p className="text-sm text-zinc-400">
                  You don&apos;t own any shares of this fighter yet.
                </p>
              )}
            </section>
          )}

          {user && (
            <section className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
              <h2 className="mb-3 text-sm font-medium uppercase tracking-wide text-zinc-500">
                Your open orders
              </h2>
              {openOrders.length === 0 ? (
                <p className="text-sm text-zinc-400">No open orders.</p>
              ) : (
                <ul className="flex flex-col gap-2">
                  {openOrders.map((o) => (
                    <li
                      key={o.id}
                      className="flex items-center justify-between gap-2 text-sm"
                    >
                      <span>
                        <span
                          className={
                            o.side === "BUY"
                              ? "text-green-600 dark:text-green-400"
                              : "text-red-600 dark:text-red-400"
                          }
                        >
                          {o.side}
                        </span>{" "}
                        {o.filledQuantity}/{o.quantity}
                        {o.limitPrice !== null && (
                          <> @ {formatCoins(o.limitPrice)}</>
                        )}
                      </span>
                      <form action={cancelOrderAction}>
                        <input type="hidden" name="orderId" value={o.id} />
                        <input type="hidden" name="slug" value={slug} />
                        <button
                          type="submit"
                          className="rounded-md border border-zinc-300 px-2 py-1 text-xs hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
                        >
                          Cancel
                        </button>
                      </form>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
        </div>
      </div>
    </main>
  );
}
