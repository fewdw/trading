import type { MetadataRoute } from "next";
import { SITE_URL } from "./lib/site";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      // Account-flow pages have no SEO value (and some carry one-time tokens).
      disallow: [
        "/login",
        "/verify",
        "/reset-password",
        "/forgot-password",
        "/resend-verification",
      ],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
