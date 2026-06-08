import Link from "next/link";
import { getCurrentUser } from "./lib/auth";
import { getFighters } from "./lib/fighters";
import FighterGrid from "./components/FighterGrid";

export default async function Home() {
  const [user, fighters] = await Promise.all([getCurrentUser(), getFighters()]);

  return (
    <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            {user ? `Welcome back, ${user.username}.` : "Fighter Market"}
          </h1>
          <p className="text-sm text-zinc-600 dark:text-zinc-400">
            Trade shares in UFC fighters.
            {!user && (
              <>
                {" "}
                <Link href="/login" className="underline">
                  Log in
                </Link>{" "}
                to trade.
              </>
            )}
          </p>
        </div>
      </div>

      {fighters.length === 0 ? (
        <p className="text-sm text-zinc-500">
          No fighters available right now.
        </p>
      ) : (
        <FighterGrid fighters={fighters} />
      )}
    </main>
  );
}
