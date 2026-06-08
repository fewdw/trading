import type { Trade } from "../lib/types";
import { formatCoins } from "../lib/format";

/** A dependency-free SVG line chart of trade price over time. */
export default function PriceChart({ trades }: { trades: Trade[] }) {
  // The API returns newest-first; chart oldest -> newest.
  const points = [...trades].reverse();

  if (points.length < 2) {
    return (
      <div className="flex h-40 items-center justify-center rounded-lg border border-dashed border-zinc-300 text-sm text-zinc-500 dark:border-zinc-700">
        Not enough trade history to chart yet.
      </div>
    );
  }

  const prices = points.map((t) => t.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const span = max - min || 1;

  const W = 600;
  const H = 160;
  const pad = 8;
  const coords = points.map((t, i) => {
    const x = pad + (i / (points.length - 1)) * (W - 2 * pad);
    const y = pad + (1 - (t.price - min) / span) * (H - 2 * pad);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });

  const up = points[points.length - 1].price >= points[0].price;
  const stroke = up ? "#16a34a" : "#dc2626";

  return (
    <div>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full"
        preserveAspectRatio="none"
        role="img"
        aria-label="Price over time"
      >
        <polyline
          fill="none"
          stroke={stroke}
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
          points={coords.join(" ")}
        />
      </svg>
      <div className="mt-1 flex justify-between text-xs text-zinc-500">
        <span>low {formatCoins(min)}</span>
        <span>high {formatCoins(max)}</span>
      </div>
    </div>
  );
}
