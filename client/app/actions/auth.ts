"use server";

import { redirect } from "next/navigation";
import { validateSignup } from "../lib/validation";
import {
  clearSessionCookie,
  forgotPasswordRequest,
  loginRequest,
  logoutRequest,
  resendVerificationRequest,
  resetPasswordRequest,
  setSessionCookie,
  signupRequest,
  verifyEmailRequest,
} from "../lib/auth";

export type FormState =
  | {
      error?: string;
      success?: string;
      // Echoed back on failure so the form can re-fill (never the password).
      values?: { username?: string; email?: string };
    }
  | undefined;

export async function signupAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const email = String(formData.get("email") ?? "").trim();
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const confirm = String(formData.get("confirmPassword") ?? "");

  const validationError = validateSignup({
    email,
    username,
    password,
    confirmPassword: confirm,
  });
  if (validationError) {
    return { error: validationError, values: { email, username } };
  }

  const result = await signupRequest(email, username, password);
  if (!result.ok) return { error: result.error, values: { email, username } };
  return {
    success:
      "Account created! Check your email for a confirmation link to activate your account.",
  };
}

export async function loginAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  if (!username || !password) {
    return {
      error: "Username or email and password are required.",
      values: { username },
    };
  }
  const result = await loginRequest(username, password);
  if (!result.ok) return { error: result.error, values: { username } };
  await setSessionCookie(result.token);
  redirect("/");
}

export async function logoutAction() {
  await logoutRequest();
  await clearSessionCookie();
  redirect("/login");
}

export async function verifyEmailAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const token = String(formData.get("token") ?? "");
  if (!token) return { error: "Missing verification token." };
  const result = await verifyEmailRequest(token);
  if (!result.ok) return { error: result.error };
  redirect("/login?verified=1");
}

export async function resendVerificationAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const email = String(formData.get("email") ?? "").trim();
  if (!email) return { error: "Email is required." };
  const result = await resendVerificationRequest(email);
  if (!result.ok) return { error: result.error };
  return {
    success:
      "If an account exists and is unverified, we've sent a new confirmation link.",
  };
}

export async function forgotPasswordAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const email = String(formData.get("email") ?? "").trim();
  if (!email) return { error: "Email is required." };
  const result = await forgotPasswordRequest(email);
  if (!result.ok) return { error: result.error };
  return {
    success:
      "If an account exists for that email, we've sent a password reset link.",
  };
}

export async function resetPasswordAction(
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  const token = String(formData.get("token") ?? "");
  const password = String(formData.get("password") ?? "");
  const confirm = String(formData.get("confirmPassword") ?? "");

  if (!token) return { error: "Missing or invalid reset link." };
  if (password.length < 8) {
    return { error: "Password must be at least 8 characters." };
  }
  if (password !== confirm) {
    return { error: "Passwords do not match." };
  }

  const result = await resetPasswordRequest(token, password);
  if (!result.ok) return { error: result.error };
  redirect("/login?reset=1");
}
