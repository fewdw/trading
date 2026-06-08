import Link from "next/link";
import { redirect } from "next/navigation";
import SignupForm from "../components/SignupForm";
import { getCurrentUser } from "../lib/auth";

export default async function SignupPage() {
  // Already logged in? There's no account to create.
  if (await getCurrentUser()) redirect("/");

  return (
    <div className="flex flex-1 flex-col items-center px-6">
      <SignupForm />
      <p className="mt-4 text-sm text-zinc-600 dark:text-zinc-400">
        Already have an account?{" "}
        <Link href="/login" className="underline">
          Log in
        </Link>
      </p>
    </div>
  );
}
