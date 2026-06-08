import "server-only";
import { cookies } from "next/headers";

const BACKEND_URL = process.env.BACKEND_URL;
const SESSION_COOKIE = "session";
export const THEME_COOKIE = "theme";

export type AuthUser = {
  id: number;
  username: string;
  availableCoins: number;
  reservedCoins: number;
  darkMode: boolean;
};

type RawUser = {
  id: number;
  username: string;
  available_coins: number;
  reserved_coins: number;
  dark_mode?: boolean;
};

function toAuthUser(raw: RawUser): AuthUser {
  return {
    id: raw.id,
    username: raw.username,
    availableCoins: raw.available_coins,
    reservedCoins: raw.reserved_coins,
    darkMode: raw.dark_mode ?? false,
  };
}

/** The `theme` cookie is the SSR-readable, no-flash cache of the user's choice. */
export async function setThemeCookie(dark: boolean) {
  const store = await cookies();
  store.set(THEME_COOKIE, dark ? "dark" : "light", {
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 365,
  });
}

type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; error: string };

// Cap auth requests so a slow/unreachable backend can't hang the form forever.
const AUTH_TIMEOUT_MS = 20000;

async function postJson<T = unknown>(
  path: string,
  body: unknown,
): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(`${BACKEND_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(AUTH_TIMEOUT_MS),
    });
  } catch {
    return { ok: false, status: 0, error: "Something went wrong. Please try again." };
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    return {
      ok: false,
      status: res.status,
      error: (data as { error?: string }).error ?? "Something went wrong.",
    };
  }
  return { ok: true, data: data as T };
}

export type AuthResult =
  | { ok: true; token: string; user: AuthUser }
  | { ok: false; status: number; error: string };

export async function loginRequest(
  username: string,
  password: string,
): Promise<AuthResult> {
  const res = await postJson<{ token: string; user: RawUser }>(
    "/api/auth/login",
    { username, password },
  );
  if (!res.ok) return res;
  return { ok: true, token: res.data.token, user: toAuthUser(res.data.user) };
}

export async function signupRequest(
  username: string,
  password: string,
): Promise<AuthResult> {
  const res = await postJson<{ token: string; user: RawUser }>(
    "/api/auth/signup",
    { username, password },
  );
  if (!res.ok) return res;
  return { ok: true, token: res.data.token, user: toAuthUser(res.data.user) };
}

export async function setSessionCookie(token: string) {
  const store = await cookies();
  store.set(SESSION_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 7,
  });
}

export async function clearSessionCookie() {
  const store = await cookies();
  store.delete(SESSION_COOKIE);
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) return null;

  const res = await fetch(`${BACKEND_URL}/api/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) return null;
  return toAuthUser((await res.json()) as RawUser);
}

export async function logoutRequest() {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (token) {
    await fetch(`${BACKEND_URL}/api/auth/logout`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    }).catch(() => {});
  }
}
