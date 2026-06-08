import "server-only";
import { cookies } from "next/headers";

const BACKEND_URL = process.env.BACKEND_URL;
const SESSION_COOKIE = "session";

export type AuthUser = {
  id: number;
  username: string;
  email: string;
  availableCoins: number;
  reservedCoins: number;
};

type RawUser = {
  id: number;
  username: string;
  email?: string;
  available_coins: number;
  reserved_coins: number;
};

function toAuthUser(raw: RawUser): AuthUser {
  return {
    id: raw.id,
    username: raw.username,
    email: raw.email ?? "",
    availableCoins: raw.available_coins,
    reservedCoins: raw.reserved_coins,
  };
}

type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; error: string };

async function postJson<T = unknown>(
  path: string,
  body: unknown,
): Promise<ApiResult<T>> {
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
  });
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

// A request that returns no useful body, just success/failure.
export type SimpleResult =
  | { ok: true }
  | { ok: false; status: number; error: string };

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
  email: string,
  username: string,
  password: string,
): Promise<SimpleResult> {
  const res = await postJson("/api/auth/signup", { email, username, password });
  return res.ok ? { ok: true } : res;
}

export async function verifyEmailRequest(token: string): Promise<SimpleResult> {
  const res = await postJson("/api/auth/verify", { token });
  return res.ok ? { ok: true } : res;
}

export async function resendVerificationRequest(
  email: string,
): Promise<SimpleResult> {
  const res = await postJson("/api/auth/resend-verification", { email });
  return res.ok ? { ok: true } : res;
}

export async function forgotPasswordRequest(
  email: string,
): Promise<SimpleResult> {
  const res = await postJson("/api/auth/forgot-password", { email });
  return res.ok ? { ok: true } : res;
}

export async function resetPasswordRequest(
  token: string,
  newPassword: string,
): Promise<SimpleResult> {
  const res = await postJson("/api/auth/reset-password", {
    token,
    newPassword,
  });
  return res.ok ? { ok: true } : res;
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
