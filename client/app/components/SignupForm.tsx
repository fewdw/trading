"use client";

import { useActionState } from "react";
import { signupAction, type FormState } from "../actions/auth";

const card =
  "mx-auto mt-16 flex w-full max-w-sm flex-col gap-4 rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-950";
const input =
  "rounded-md border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

export default function SignupForm() {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    signupAction,
    undefined,
  );

  return (
    <form action={formAction} className={card}>
      <h1 className="text-xl font-semibold">Sign up</h1>
      <label className="flex flex-col gap-1 text-sm">
        Username
        <input
          name="username"
          type="text"
          required
          minLength={3}
          maxLength={30}
          pattern="[A-Za-z0-9_]+"
          title="Letters, numbers, and underscores only"
          autoComplete="username"
          defaultValue={state?.values?.username ?? ""}
          className={input}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Password
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
        {pending ? "Please wait..." : "Create account"}
      </button>
    </form>
  );
}
