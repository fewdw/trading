"use client";

import { useActionState } from "react";
import type { FormState } from "../actions/auth";

type Props = {
  title: string;
  submitLabel: string;
  action: (state: FormState, formData: FormData) => Promise<FormState>;
};

export default function AuthForm({ title, submitLabel, action }: Props) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    action,
    undefined,
  );
  return (
    <form
      action={formAction}
      className="mx-auto mt-16 flex w-full max-w-sm flex-col gap-4 rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-950"
    >
      <h1 className="text-xl font-semibold">{title}</h1>
      <label className="flex flex-col gap-1 text-sm">
        Username
        <input
          name="username"
          type="text"
          required
          autoComplete="username"
          defaultValue={state?.values?.username ?? ""}
          className="rounded-md border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Password
        <input
          name="password"
          type="password"
          required
          autoComplete="current-password"
          className="rounded-md border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
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
        {pending ? "Please wait..." : submitLabel}
      </button>
    </form>
  );
}
