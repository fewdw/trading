import type { Metadata } from "next";
import Link from "next/link";
import EmailRequestForm from "../components/EmailRequestForm";
import { resendVerificationAction } from "../actions/auth";

export const metadata: Metadata = {
  title: "Resend confirmation",
  robots: { index: false, follow: true },
};

export default function ResendVerificationPage() {
  return (
    <div className="flex flex-1 flex-col items-center px-6">
      <EmailRequestForm
        title="Resend confirmation"
        description="Enter your email and we'll send a new account confirmation link."
        submitLabel="Resend confirmation"
        action={resendVerificationAction}
      />
      <p className="mt-4 text-sm text-zinc-600 dark:text-zinc-400">
        <Link href="/login" className="underline">
          Back to log in
        </Link>
      </p>
    </div>
  );
}
