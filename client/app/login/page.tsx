import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import AuthForm from "../components/AuthForm";
import { loginAction } from "../actions/auth";
import { getCurrentUser } from "../lib/auth";

export const metadata: Metadata = {
  title: "Log in",
  robots: { index: false, follow: true },
};

export default async function LoginPage() {
  // Already logged in? There's nothing to do here.
  if (await getCurrentUser()) redirect("/");

  return (
    <div className="flex flex-1 flex-col items-center px-6">
      <AuthForm title="Log in" submitLabel="Log in" action={loginAction} />
      <p className="mt-4 text-sm text-zinc-600 dark:text-zinc-400">
        No account?{" "}
        <Link href="/signup" className="underline">
          Sign up
        </Link>
      </p>
    </div>
  );
}
