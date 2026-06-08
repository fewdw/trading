import type { Orderbook } from "../lib/types";
import { formatCoins } from "../lib/format";

/** Aggregated depth: asks (sell side) on top, bids (buy side) below. */
export default function OrderBook({ book }: { book: Orderbook }) {
  return (
    <div className="grid grid-cols-2 gap-4 text-sm">
      <Side title="Bids" rows={book.bids} tone="text-green-600 dark:text-green-400" />
      <Side title="Asks" rows={book.asks} tone="text-red-600 dark:text-red-400" />
    </div>
  );
}

function Side({
  title,
  rows,
  tone,
}: {
  title: string;
  rows: { price: number; quantity: number }[];
  tone: string;
}) {
  return (
    <div>
      <div className="mb-1 flex justify-between text-xs font-medium uppercase tracking-wide text-zinc-500">
        <span>{title}</span>
        <span>Qty</span>
      </div>
      {rows.length === 0 ? (
        <p className="py-2 text-xs text-zinc-400">None</p>
      ) : (
        <ul className="flex flex-col gap-0.5 font-mono">
          {rows.map((r) => (
            <li key={r.price} className="flex justify-between">
              <span className={tone}>{formatCoins(r.price)}</span>
              <span className="text-zinc-600 dark:text-zinc-400">
                {r.quantity}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
