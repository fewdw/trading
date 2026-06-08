// Single source of truth for site-wide branding and SEO. Imported by the root
// metadata, OG image, manifest, robots, and sitemap so they never drift.

/** Public origin of the site, used for canonical/OG/sitemap absolute URLs. */
export const SITE_URL = process.env.SITE_URL ?? "http://localhost:3000";

export const SITE_NAME = "Fighter Market";
export const SITE_TAGLINE = "Trade shares in UFC fighters";

export const SITE_DESCRIPTION =
  "Fighter Market is a fantasy stock market for UFC fighters. Start with free coins, buy and sell shares on a live price/time order book, track your P&L, and climb the leaderboard — no real money.";

/** Brand colors (match the app's chart accent + dark background). */
export const BRAND_GREEN = "#16a34a";
export const BRAND_DARK = "#0a0a0a";

export const SITE_KEYWORDS = [
  "UFC",
  "MMA",
  "fighters",
  "fantasy stock market",
  "fantasy trading",
  "prediction market",
  "fighter stocks",
  "order book",
  "trading game",
  "Fighter Market",
];
