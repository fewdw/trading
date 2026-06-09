import { ImageResponse } from "next/og";
import { SITE_NAME, SITE_TAGLINE, BRAND_DARK, BRAND_GREEN } from "./lib/site";

export const alt = `${SITE_NAME} — ${SITE_TAGLINE}`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// The optimized bulletproof social-share card that prevents any platform text overlapping.
export default function OpengraphImage() {
  return new ImageResponse(
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: "80px",
        background: BRAND_DARK,
        color: "#ffffff",
        fontFamily: "sans-serif",
        boxSizing: "border-box",
      }}
    >
      {/* 1. Header Area: Site Identity */}
      <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
        <svg width="64" height="64" viewBox="0 0 32 32" fill="none">
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
        <div
          style={{
            display: "flex",
            fontSize: 36,
            color: "#e4e4e7",
            fontWeight: "bold",
            letterSpacing: "0.05em",
          }}
        >
          {SITE_NAME}
        </div>
      </div>

      {/* 2. Main Content Group: Bulletproof flex layout avoiding absolute collision */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          marginTop: "auto",
          marginBottom: "auto",
        }}
      >
        {/* Main Headliner Text */}
        <div
          style={{
            display: "flex",
            fontSize: 64,
            fontWeight: "bold",
            lineHeight: 1.15,
            color: "#ffffff",
            maxWidth: "1040px",
            marginBottom: "20px",
          }}
        >
          Trade shares in UFC fighters.
        </div>

        {/* Secondary Subtitle details */}
        <div
          style={{
            display: "flex",
            fontSize: 26,
            color: "#a1a1aa",
            lineHeight: 1.4,
            maxWidth: "960px",
          }}
        >
          Start with free coins. Buy and sell on a live order book. Climb the
          leaderboard.
        </div>
      </div>

      {/* 3. Footer Action Badge Area */}
      <div style={{ display: "flex", marginTop: "auto" }}>
        <div
          style={{
            display: "flex",
            background: BRAND_GREEN,
            color: "#06210f",
            fontSize: 24,
            fontWeight: "bold",
            padding: "12px 28px",
            borderRadius: 12,
            letterSpacing: "0.02em",
          }}
        >
          Play money · No real risk
        </div>
      </div>
    </div>,
    { ...size },
  );
}
