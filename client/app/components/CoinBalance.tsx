"use client";

import { useEffect, useState } from "react";

type Props = {
  availableCoins: number;
  reservedCoins: number;
};

/** Balances arrive in cents (sub-units); show them as coins with 2 decimals. */
function formatCoins(cents: number): string {
  return (cents / 100).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

/**
 * The coin badge in the navbar. Renders the server-provided balance, then keeps
 * it live over a WebSocket. The socket authenticates with the httpOnly `session`
 * cookie, which the browser sends automatically on the handshake — so the token
 * is never exposed to client JS.
 */
export default function CoinBalance({ availableCoins, reservedCoins }: Props) {
  const [available, setAvailable] = useState(availableCoins);
  const [reserved, setReserved] = useState(reservedCoins);

  // Adjust state during render when the server sends fresh props (e.g. after a
  // navigation) — React's recommended pattern instead of a sync effect.
  const [serverValues, setServerValues] = useState({
    availableCoins,
    reservedCoins,
  });
  if (
    serverValues.availableCoins !== availableCoins ||
    serverValues.reservedCoins !== reservedCoins
  ) {
    setServerValues({ availableCoins, reservedCoins });
    setAvailable(availableCoins);
    setReserved(reservedCoins);
  }

  useEffect(() => {
    // Backend listens on the same host, port 8080 (see docker-compose / dev).
    const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
    const url = `${proto}//${window.location.hostname}:8080/ws`;

    let socket: WebSocket | null = null;
    let stopped = false;
    let retry: ReturnType<typeof setTimeout> | undefined;

    const connect = () => {
      socket = new WebSocket(url);

      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data) as {
            type?: string;
            availableCoins?: number;
            reservedCoins?: number;
          };
          if (msg.type === "BALANCE_UPDATE") {
            if (typeof msg.availableCoins === "number") {
              setAvailable(msg.availableCoins);
            }
            if (typeof msg.reservedCoins === "number") {
              setReserved(msg.reservedCoins);
            }
          }
        } catch {
          // ignore malformed frames
        }
      };

      // Auto-reconnect with a small backoff if the socket drops.
      socket.onclose = () => {
        if (!stopped) retry = setTimeout(connect, 3000);
      };
    };

    connect();

    return () => {
      stopped = true;
      if (retry) clearTimeout(retry);
      socket?.close();
    };
  }, []);

  return (
    <span
      className="flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2.5 py-1 font-medium text-amber-700 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-400"
      title={
        reserved > 0
          ? `${formatCoins(available)} coins available, ${formatCoins(reserved)} reserved`
          : "Coins"
      }
    >
      <span aria-hidden>🪙</span>
      {formatCoins(available)}
      {reserved > 0 && (
        <span className="font-normal text-amber-600/80 dark:text-amber-400/70">
          ({formatCoins(reserved)} reserved)
        </span>
      )}
    </span>
  );
}
