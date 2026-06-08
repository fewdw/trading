"use client";

import { useActionState, useState } from "react";
import { placeOrderAction, type OrderFormState } from "../actions/orders";

const base =
  "rounded-md px-3 py-2 text-sm font-medium border transition disabled:opacity-50";
const input =
  "rounded-md border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

export default function PlaceOrderForm({
  fighterId,
  slug,
}: {
  fighterId: number;
  slug: string;
}) {
  const [state, formAction, pending] = useActionState<OrderFormState, FormData>(
    placeOrderAction,
    undefined,
  );
  const [side, setSide] = useState<"BUY" | "SELL">("BUY");
  const [type, setType] = useState<"LIMIT" | "MARKET">("LIMIT");

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
          onClick={() => setType("LIMIT")}
          className={`${base} ${
            type === "LIMIT"
              ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-100 dark:bg-zinc-100 dark:text-zinc-900"
              : "border-zinc-300 dark:border-zinc-700"
          }`}
        >
          Limit
        </button>
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
      </div>

      <label className="flex flex-col gap-1 text-sm">
        Quantity (shares)
        <input
          name="quantity"
          type="number"
          min={1}
          step={1}
          required
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
    </form>
  );
}
