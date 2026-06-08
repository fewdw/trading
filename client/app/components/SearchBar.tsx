"use client";

/** The centered navbar search field — opens the ⌘K command palette. */
export default function SearchBar() {
  return (
    <button
      type="button"
      onClick={() => window.dispatchEvent(new Event("command-search:open"))}
      className="flex w-full max-w-md items-center justify-between gap-2 rounded-md border border-zinc-300 bg-zinc-50 px-3 py-1.5 text-sm text-zinc-500 hover:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-900 dark:hover:bg-zinc-800"
    >
      <span>Search fighters…</span>
      <kbd className="rounded border border-zinc-300 px-1.5 text-xs dark:border-zinc-600">
        ⌘K
      </kbd>
    </button>
  );
}
