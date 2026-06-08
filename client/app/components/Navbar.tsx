import Link from "next/link";
import { getCurrentUser } from "../lib/auth";
import { logoutAction } from "../actions/auth";
import CoinBalance from "./CoinBalance";
import SearchBar from "./SearchBar";

export default async function Navbar() {
  const user = await getCurrentUser();
  return (
    // Three equal columns keep the search bar at the true centre of the page,
    // regardless of how wide the logo (left) or account controls (right) are.
    <nav className="grid grid-cols-3 items-center gap-4 border-b border-zinc-200 bg-white px-6 py-3 dark:border-zinc-800 dark:bg-zinc-950">
      <div className="flex min-w-0 items-center gap-4">
        <Link
          href="/"
          className="flex shrink-0 items-center gap-2 font-semibold tracking-tight"
          aria-label="Fighter Market — home"
        >
          <svg
            viewBox="0 0 32 32"
            className="h-6 w-6 shrink-0"
            fill="none"
            aria-hidden
          >
            <rect width="32" height="32" rx="7" fill="#0a0a0a" />
            <path
              d="M5.5 20.5 L13 13 L18 17.5 L26.5 8"
              stroke="#16a34a"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path
              d="M20 8 H26.5 V14.5"
              stroke="#16a34a"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          <span className="hidden sm:inline">Fighter Market</span>
        </Link>
        <Link
          href="/about"
          className="shrink-0 text-sm text-zinc-500 hover:underline"
        >
          About
        </Link>
      </div>
      <div className="flex justify-center">
        <SearchBar />
      </div>
      <div className="flex min-w-0 shrink-0 items-center justify-end gap-4 text-sm">
        {user ? (
          <>
            <CoinBalance
              availableCoins={user.availableCoins}
              reservedCoins={user.reservedCoins}
            />
            <Link
              href={`/${user.username}`}
              className="text-zinc-700 hover:underline dark:text-zinc-300"
            >
              {user.username}
            </Link>
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
