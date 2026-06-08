import { ImageResponse } from "next/og";
import { BRAND_DARK, BRAND_GREEN } from "./lib/site";

export const size = { width: 180, height: 180 };
export const contentType = "image/png";

// Generated apple-touch icon: the brand "trending up" mark on the dark tile.
// iOS rounds the corners itself, so we fill the whole square.
export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: BRAND_DARK,
        }}
      >
        <svg width="120" height="120" viewBox="0 0 32 32" fill="none">
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
      </div>
    ),
    { ...size },
  );
}
