import { cookies } from "next/headers";

/**
 * Hands the browser what it needs to open the live-updates WebSocket straight to
 * the backend:
 *
 *  - `url`   — the public ws(s):// endpoint, from the BACKEND_WS_URL runtime env
 *              var (set in production). When unset (local dev / docker-compose),
 *              the client falls back to ws://<host>:8080/ws.
 *  - `token` — the value of the httpOnly `session` cookie. The browser can't read
 *              that cookie itself, so we read it here server-side; the client
 *              appends it as the backend's `?token=` handshake param. This is the
 *              only way to authenticate the socket once the frontend and backend
 *              live on different origins (where the cookie no longer rides along).
 *
 * Reading the cookie makes this handler dynamic, so it always runs at request
 * time. Same-origin only — there is no CORS, so only our own pages can call it.
 */
export async function GET() {
  const token = (await cookies()).get("session")?.value ?? null;
  const url = process.env.BACKEND_WS_URL ?? null;
  return Response.json({ url, token });
}
