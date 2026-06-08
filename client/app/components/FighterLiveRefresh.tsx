"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/**
 * Subscribes to the live market socket and refreshes this page's server data
 * when this fighter's book/price changes. Auth is the httpOnly session cookie,
 * so live updates only run for logged-in users; everyone else sees data as of
 * page load (and can refresh manually).
 */
export default function FighterLiveRefresh({ fighterId }: { fighterId: number }) {
  const router = useRouter();

  useEffect(() => {
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
            fighterId?: number;
          };
          if (msg.type === "MARKET_UPDATE" && msg.fighterId === fighterId) {
            router.refresh();
          }
        } catch {
          // ignore malformed frames
        }
      };
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
  }, [fighterId, router]);

  return null;
}
