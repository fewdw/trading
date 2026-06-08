import type { Metadata } from "next";
import Link from "next/link";
import EmailRequestForm from "../components/EmailRequestForm";
import { forgotPasswordAction } from "../actions/auth";

export const metadata: Metadata = {
  title: "Forgot password",
  robots: { index: false, follow: true },
};

export default function ForgotPasswordPage() {
  return (
    <div className="flex flex-1 flex-col items-center px-6">
      <EmailRequestForm
        title="Forgot password"
        description="Enter your email and we'll send you a link to reset your password."
        submitLabel="Send reset link"
        action={forgotPasswordAction}
      />
      <p className="mt-4 text-sm text-zinc-600 dark:text-zinc-400">
        <Link href="/login" className="underline">
          Back to log in
        </Link>
      </p>
    </div>
  );
}
