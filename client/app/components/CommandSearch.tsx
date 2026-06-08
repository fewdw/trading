"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import type { Fighter } from "../lib/types";
import { slugify } from "../lib/slug";
import { formatCoins } from "../lib/format";

/** A macOS-Spotlight-style fighter search, toggled with ⌘K / Ctrl+K. */
export default function CommandSearch({ fighters }: { fighters: Fighter[] }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setQuery("");
        setOpen((o) => !o);
      } else if (e.key === "Escape") {
        setOpen(false);
      }
    };
    // The navbar search bar opens the palette via this event.
    const onOpen = () => {
      setQuery("");
      setOpen(true);
    };
    window.addEventListener("keydown", onKey);
    window.addEventListener("command-search:open", onOpen);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("command-search:open", onOpen);
    };
  }, []);

  // Focus the input when the panel opens (DOM sync, not state).
  useEffect(() => {
    if (!open) return;
    const id = window.setTimeout(() => inputRef.current?.focus(), 0);
    return () => window.clearTimeout(id);
  }, [open]);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = q
      ? fighters.filter((f) => f.name.toLowerCase().includes(q))
      : fighters;
    return list.slice(0, 8);
  }, [query, fighters]);

  const go = (fighter: Fighter) => {
    setOpen(false);
    router.push(`/fighter/${slugify(fighter.name)}`);
  };

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 p-4 pt-[15vh] backdrop-blur-sm"
      onClick={() => setOpen(false)}
    >
      <div
        className="w-full max-w-xl overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-900"
        onClick={(e) => e.stopPropagation()}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (results[0]) go(results[0]);
          }}
        >
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search fighters…"
            className="w-full border-b border-zinc-200 bg-transparent px-4 py-3 text-base outline-none dark:border-zinc-800"
          />
        </form>
        <ul className="max-h-80 overflow-y-auto py-1">
          {results.length === 0 ? (
            <li className="px-4 py-6 text-center text-sm text-zinc-500">
              No fighters found.
            </li>
          ) : (
            results.map((f) => (
              <li key={f.id}>
                <button
                  type="button"
                  onClick={() => go(f)}
                  className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-zinc-100 dark:hover:bg-zinc-800"
                >
                  {f.photo ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={f.photo}
                      alt=""
                      className="h-8 w-8 rounded-full object-cover"
                    />
                  ) : (
                    <span className="flex h-8 w-8 items-center justify-center rounded-full bg-zinc-200 dark:bg-zinc-700">
                      🥊
                    </span>
                  )}
                  <span className="flex-1 truncate capitalize">{f.name}</span>
                  <span className="font-mono text-xs text-zinc-500">
                    {formatCoins(f.lastPrice)}
                  </span>
                </button>
              </li>
            ))
          )}
        </ul>
      </div>
    </div>
  );
}
