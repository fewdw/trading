"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { subscribeLive } from "../lib/ws";

/**
 * Subscribes to the live market socket and refreshes this page's server data
 * when this fighter's book/price changes (see `subscribeLive`). Authenticated
 * users get live updates; everyone else sees data as of page load (and can
 * refresh manually).
 */
export default function FighterLiveRefresh({ fighterId }: { fighterId: number }) {
  const router = useRouter();

  useEffect(
    () =>
      subscribeLive((data) => {
        const msg = data as { type?: string; fighterId?: number };
        if (msg.type === "MARKET_UPDATE" && msg.fighterId === fighterId) {
          router.refresh();
        }
      }),
    [fighterId, router],
  );

  return null;
}
