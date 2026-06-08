"use client";

import { useActionState, useState } from "react";
import { placeOrderAction, type OrderFormState } from "../actions/orders";
import { formatCoins } from "../lib/format";
import type { PriceLevel } from "../lib/types";

const base =
  "rounded-md px-3 py-2 text-sm font-medium border transition disabled:opacity-50";
const input =
  "rounded-md border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

/** Walk the asks (cheapest first) to estimate what a market buy of `qty` costs. */
function marketBuyEstimate(asks: PriceLevel[], qty: number) {
  let remaining = qty;
  let cost = 0;
  let filled = 0;
  for (const level of asks) {
    if (remaining <= 0) break;
    const take = Math.min(remaining, level.quantity);
    cost += take * level.price; // sub-units
    filled += take;
    remaining -= take;
  }
  return { cost, filled, shortfall: remaining };
}

export default function PlaceOrderForm({
  fighterId,
  slug,
  asks,
}: {
  fighterId: number;
  slug: string;
  asks: PriceLevel[];
}) {
  const [state, formAction, pending] = useActionState<OrderFormState, FormData>(
    placeOrderAction,
    undefined,
  );
  const [side, setSide] = useState<"BUY" | "SELL">("BUY");
  // Market is the default order type for both buys and sells — one click trades
  // at the best available price; switch to Limit to rest an order on the book.
  const [type, setType] = useState<"LIMIT" | "MARKET">("MARKET");
  const [quantity, setQuantity] = useState("");
  const [limitPrice, setLimitPrice] = useState("");

  // A live cost estimate for buys, shown under the button. Limit cost is exact
  // (shares × price); market cost is an estimate from the current asks.
  const qty = Math.floor(Number(quantity));
  const priceCoins = Number(limitPrice);
  const showEstimate = side === "BUY" && Number.isFinite(qty) && qty > 0;
  let estimate: React.ReactNode = null;
  if (showEstimate && type === "LIMIT") {
    if (Number.isFinite(priceCoins) && priceCoins > 0) {
      const cost = qty * Math.round(priceCoins * 100);
      estimate = (
        <>
          Estimated cost:{" "}
          <span className="font-medium text-zinc-700 dark:text-zinc-300">
            {formatCoins(cost)} coins
          </span>{" "}
          ({qty} × {priceCoins})
        </>
      );
    }
  } else if (showEstimate && type === "MARKET") {
    const { cost, filled, shortfall } = marketBuyEstimate(asks, qty);
    if (filled === 0) {
      estimate = "No sellers available right now — this market buy can't fill.";
    } else if (shortfall > 0) {
      estimate = (
        <>
          ≈{" "}
          <span className="font-medium text-zinc-700 dark:text-zinc-300">
            {formatCoins(cost)} coins
          </span>{" "}
          for {filled} of {qty} shares — not enough sellers for the rest.
        </>
      );
    } else {
      estimate = (
        <>
          Estimated total: ≈{" "}
          <span className="font-medium text-zinc-700 dark:text-zinc-300">
            {formatCoins(cost)} coins
          </span>
        </>
      );
    }
  }

  return (
    <form action={formAction} className="flex flex-col gap-3">
      <input type="hidden" name="fighterId" value={fighterId} />
      <input type="hidden" name="slug" value={slug} />
      <input type="hidden" name="side" value={side} />
      <input type="hidden" name="type" value={type} />

      <div className="grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={() => setSide("BUY")}
          className={`${base} ${
            side === "BUY"
              ? "border-green-600 bg-green-600 text-white"
              : "border-zinc-300 dark:border-zinc-700"
          }`}
        >
          Buy
        </button>
        <button
          type="button"
          onClick={() => setSide("SELL")}
          className={`${base} ${
            side === "SELL"
              ? "border-red-600 bg-red-600 text-white"
              : "border-zinc-300 dark:border-zinc-700"
          }`}
        >
          Sell
        </button>
      </div>

      <div className="grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={() => setType("MARKET")}
          className={`${base} ${
            type === "MARKET"
              ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-100 dark:bg-zinc-100 dark:text-zinc-900"
              : "border-zinc-300 dark:border-zinc-700"
          }`}
        >
          Market
        </button>
        <button
          type="button"
          onClick={() => setType("LIMIT")}
          className={`${base} ${
            type === "LIMIT"
              ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-100 dark:bg-zinc-100 dark:text-zinc-900"
              : "border-zinc-300 dark:border-zinc-700"
          }`}
        >
          Limit
        </button>
      </div>

      <label className="flex flex-col gap-1 text-sm">
        Quantity (shares)
        <input
          name="quantity"
          type="number"
          min={1}
          step={1}
          required
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          className={input}
        />
      </label>

      {type === "LIMIT" && (
        <label className="flex flex-col gap-1 text-sm">
          Limit price (coins)
          <input
            name="limitPrice"
            type="number"
            min="0.01"
            step="0.01"
            required
            value={limitPrice}
            onChange={(e) => setLimitPrice(e.target.value)}
            className={input}
          />
        </label>
      )}

      {state?.error && (
        <p className="text-sm text-red-600 dark:text-red-400">{state.error}</p>
      )}
      {state?.success && (
        <p className="text-sm text-green-600 dark:text-green-400">
          {state.success}
        </p>
      )}

      <button
        type="submit"
        disabled={pending}
        className={`${base} text-white ${
          side === "BUY" ? "bg-green-600" : "bg-red-600"
        } border-transparent`}
      >
        {pending
          ? "Placing…"
          : `${side === "BUY" ? "Buy" : "Sell"}${
              type === "MARKET" ? " at market" : ""
            }`}
      </button>

      {estimate && <p className="text-sm text-zinc-500">{estimate}</p>}
    </form>
  );
}
