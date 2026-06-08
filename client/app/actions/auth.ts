"use server";

import { redirect } from "next/navigation";
import { validateSignup } from "../lib/validation";
import {
  clearSessionCookie,
  loginRequest,
  logoutRequest,
  setSessionCookie,
  setThemeCookie,
  signupRequest,
} from "../lib/auth";

export type FormState =
  | {
      error?: string;
      success?: string;
      // Echoed back on failure so the form can re-fill (never the password).
      values?: { username?: string };
    }
  | undefined;

export async function signupAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const confirm = String(formData.get("confirmPassword") ?? "");

  const validationError = validateSignup({
    username,
    password,
    confirmPassword: confirm,
  });
  if (validationError) {
    return { error: validationError, values: { username } };
  }

  const result = await signupRequest(username, password);
  if (!result.ok) return { error: result.error, values: { username } };
  // Signup logs you straight in.
  await setSessionCookie(result.token);
  await setThemeCookie(result.user.darkMode);
  redirect("/");
}

export async function loginAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  if (!username || !password) {
    return {
      error: "Username and password are required.",
      values: { username },
    };
  }
  const result = await loginRequest(username, password);
  if (!result.ok) return { error: result.error, values: { username } };
  await setSessionCookie(result.token);
  // Carry their saved theme into the SSR cookie so it applies right away.
  await setThemeCookie(result.user.darkMode);
  redirect("/");
}

export async function logoutAction() {
  await logoutRequest();
  await clearSessionCookie();
  redirect("/login");
}
