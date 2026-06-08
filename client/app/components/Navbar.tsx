import Link from "next/link";
import { getCurrentUser } from "../lib/auth";
import { logoutAction } from "../actions/auth";
import CoinBalance from "./CoinBalance";

export default async function Navbar() {
  const user = await getCurrentUser();
  return (
    <nav className="flex items-center justify-between border-b border-zinc-200 bg-white px-6 py-3 dark:border-zinc-800 dark:bg-zinc-950">
      <Link href="/" className="font-semibold tracking-tight">
        App
      </Link>
      <div className="flex items-center gap-4 text-sm">
        {user ? (
          <>
            <CoinBalance
              availableCoins={user.availableCoins}
              reservedCoins={user.reservedCoins}
            />
            <span className="text-zinc-700 dark:text-zinc-300">
              {user.username}
            </span>
            <form action={logoutAction}>
              <button
                type="submit"
                className="rounded-md border border-zinc-300 px-3 py-1 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
              >
                Log out
              </button>
            </form>
          </>
        ) : (
          <>
            <Link href="/login" className="hover:underline">
              Log in
            </Link>
            <Link
              href="/signup"
              className="rounded-md bg-zinc-900 px-3 py-1 text-white hover:bg-zinc-700 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
            >
              Sign up
            </Link>
          </>
        )}
      </div>
    </nav>
  );
}
