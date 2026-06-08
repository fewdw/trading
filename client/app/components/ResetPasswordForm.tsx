"use client";

import Link from "next/link";
import { useActionState } from "react";
import { resetPasswordAction, type FormState } from "../actions/auth";

const card =
  "mx-auto mt-16 flex w-full max-w-sm flex-col gap-4 rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-950";
const input =
  "rounded-md border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

export default function ResetPasswordForm({ token }: { token: string }) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    resetPasswordAction,
    undefined,
  );

  if (!token) {
    return (
      <div className={card}>
        <h1 className="text-xl font-semibold">Invalid link</h1>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          This password reset link is missing its token.{" "}
          <Link href="/forgot-password" className="underline">
            Request a new one
          </Link>
          .
        </p>
      </div>
    );
  }

  return (
    <form action={formAction} className={card}>
      <h1 className="text-xl font-semibold">Choose a new password</h1>
      <input type="hidden" name="token" value={token} />
      <label className="flex flex-col gap-1 text-sm">
        New password
        <input
          name="password"
          type="password"
          required
          minLength={8}
          autoComplete="new-password"
          className={input}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Confirm password
        <input
          name="confirmPassword"
          type="password"
          required
          minLength={8}
          autoComplete="new-password"
          className={input}
        />
      </label>
      {state?.error && (
        <p className="text-sm text-red-600 dark:text-red-400">{state.error}</p>
      )}
      <button
        type="submit"
        disabled={pending}
        className="rounded-md bg-zinc-900 px-4 py-2 text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Please wait..." : "Reset password"}
      </button>
    </form>
  );
}
