"use client";

import Link from "next/link";
import { useActionState } from "react";
import { verifyEmailAction, type FormState } from "../actions/auth";

const card =
  "mx-auto mt-16 flex w-full max-w-sm flex-col gap-4 rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-950";

export default function VerifyForm({ token }: { token: string }) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    verifyEmailAction,
    undefined,
  );

  if (!token) {
    return (
      <div className={card}>
        <h1 className="text-xl font-semibold">Invalid link</h1>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          This confirmation link is missing its token.{" "}
          <Link href="/resend-verification" className="underline">
            Resend confirmation
          </Link>
          .
        </p>
      </div>
    );
  }

  return (
    <form action={formAction} className={card}>
      <h1 className="text-xl font-semibold">Confirm your email</h1>
      <p className="text-sm text-zinc-600 dark:text-zinc-400">
        Click below to activate your account.
      </p>
      <input type="hidden" name="token" value={token} />
      {state?.error && (
        <p className="text-sm text-red-600 dark:text-red-400">
          {state.error}{" "}
          <Link href="/resend-verification" className="underline">
            Resend confirmation
          </Link>
        </p>
      )}
      <button
        type="submit"
        disabled={pending}
        className="rounded-md bg-zinc-900 px-4 py-2 text-white disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Confirming..." : "Confirm email"}
      </button>
    </form>
  );
}
