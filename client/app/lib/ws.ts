"use client";

// Shared client-side subscription to the backend's live-updates WebSocket.
// Centralizes how the URL and auth token are resolved so the three live
// components (CoinBalance, FighterGrid, FighterLiveRefresh) stay in sync.

type LiveConfig = { url: string | null; token: string | null };

// Ask our own server for the WS endpoint + session token. Done per connect
// attempt (not cached) so login/logout is reflected without a page reload.
async function loadConfig(): Promise<LiveConfig> {
  try {
    const res = await fetch("/api/ws-token", { cache: "no-store" });
    if (!res.ok) return { url: null, token: null };
    return (await res.json()) as LiveConfig;
  } catch {
    return { url: null, token: null };
  }
}

function resolveUrl({ url, token }: LiveConfig): string {
  // Local/dev fallback: backend on the same host, port 8080 (docker-compose).
  let base = url;
  if (!base) {
    const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
    base = `${proto}//${window.location.hostname}:8080/ws`;
  }
  return token ? `${base}?token=${encodeURIComponent(token)}` : base;
}

/**
 * Opens the live-updates socket and calls `onMessage` for each parsed frame,
 * reconnecting with a fixed backoff if it drops. Returns a cleanup function
 * (suitable as a useEffect return) that stops retries and closes the socket.
 */
export function subscribeLive(onMessage: (data: unknown) => void): () => void {
  let socket: WebSocket | null = null;
  let stopped = false;
  let retry: ReturnType<typeof setTimeout> | undefined;

  const connect = async () => {
    const config = await loadConfig();
    if (stopped) return;
    const ws = new WebSocket(resolveUrl(config));
    socket = ws;

    ws.onmessage = (event) => {
      try {
        onMessage(JSON.parse(event.data));
      } catch {
        // ignore malformed frames
      }
    };
    ws.onclose = () => {
      if (!stopped) retry = setTimeout(connect, 3000);
    };
  };

  void connect();

  return () => {
    stopped = true;
    if (retry) clearTimeout(retry);
    socket?.close();
  };
}
