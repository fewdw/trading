import Link from "next/link";
import { getCurrentUser } from "../lib/auth";
import { logoutAction } from "../actions/auth";
import CoinBalance from "./CoinBalance";
import SearchBar from "./SearchBar";

export default async function Navbar() {
  const user = await getCurrentUser();
  return (
    <nav className="flex items-center gap-4 border-b border-zinc-200 bg-white px-6 py-3 dark:border-zinc-800 dark:bg-zinc-950">
      <Link href="/" className="shrink-0 font-semibold tracking-tight">
        App
      </Link>
      <div className="flex flex-1 justify-center">
        <SearchBar />
      </div>
      <div className="flex shrink-0 items-center gap-4 text-sm">
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
