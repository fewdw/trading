import type { Order } from "../lib/types";

const STATUS_STYLES: Record<Order["status"], string> = {
  OPEN: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-400",
  PARTIAL:
    "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400",
  FILLED:
    "bg-green-100 text-green-700 dark:bg-green-500/15 dark:text-green-400",
  CANCELLED: "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400",
};

/** A coloured pill for an order's lifecycle status (open/partial/filled/cancelled). */
export default function OrderStatusBadge({
  status,
}: {
  status: Order["status"];
}) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[status]}`}
    >
      {status.toLowerCase()}
    </span>
  );
}
