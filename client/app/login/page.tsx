import Link from "next/link";
import AuthForm from "../components/AuthForm";
import { loginAction } from "../actions/auth";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ verified?: string; reset?: string }>;
}) {
  const sp = await searchParams;
  const notice = sp.verified
    ? "Your email is confirmed — you can now log in."
    : sp.reset
      ? "Your password has been reset — log in with your new password."
      : null;

  return (
    <div className="flex flex-1 flex-col items-center px-6">
      {notice && (
        <p className="mt-8 w-full max-w-sm rounded-md border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-700 dark:border-green-500/40 dark:bg-green-500/10 dark:text-green-400">
          {notice}
        </p>
      )}
      <AuthForm title="Log in" submitLabel="Log in" action={loginAction} />
      <div className="mt-4 flex flex-col items-center gap-1 text-sm text-zinc-600 dark:text-zinc-400">
        <Link href="/forgot-password" className="underline">
          Forgot password?
        </Link>
        <Link href="/resend-verification" className="underline">
          Need to confirm your email?
        </Link>
        <span>
          No account?{" "}
          <Link href="/signup" className="underline">
            Sign up
          </Link>
        </span>
      </div>
    </div>
  );
}
