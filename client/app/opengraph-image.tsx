import { ImageResponse } from "next/og";
import { SITE_NAME, SITE_TAGLINE, BRAND_DARK, BRAND_GREEN } from "./lib/site";

export const alt = `${SITE_NAME} — ${SITE_TAGLINE}`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// The default social-share card used across the site (file-based, so Next wires
// it into og:image / twitter:image automatically).
export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: "80px",
          background: BRAND_DARK,
          color: "#ffffff",
          fontFamily: "sans-serif",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 24 }}>
          <svg width="88" height="88" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="7" fill="#161616" />
            <path
              d="M5.5 20.5 L13 13 L18 17.5 L26.5 8"
              stroke={BRAND_GREEN}
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path
              d="M20 8 H26.5 V14.5"
              stroke={BRAND_GREEN}
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          <div style={{ display: "flex", fontSize: 40, color: "#a1a1aa" }}>
            {SITE_NAME}
          </div>
        </div>

        <div
          style={{
            display: "flex",
            fontSize: 86,
            fontWeight: "bold",
            marginTop: 44,
            lineHeight: 1.1,
          }}
        >
          Trade shares in UFC fighters.
        </div>

        <div
          style={{
            display: "flex",
            fontSize: 34,
            color: "#a1a1aa",
            marginTop: 28,
            maxWidth: 920,
          }}
        >
          Start with free coins. Buy and sell on a live order book. Climb the
          leaderboard.
        </div>

        <div style={{ display: "flex", marginTop: 52 }}>
          <div
            style={{
              display: "flex",
              background: BRAND_GREEN,
              color: "#06210f",
              fontSize: 30,
              fontWeight: "bold",
              padding: "14px 30px",
              borderRadius: 14,
            }}
          >
            Play money · No real risk
          </div>
        </div>
      </div>
    ),
    { ...size },
  );
}
