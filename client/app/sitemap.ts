import type { MetadataRoute } from "next";
import { SITE_URL } from "./lib/site";
import { getFighters } from "./lib/fighters";
import { slugify } from "./lib/slug";

// Re-read live each request so newly listed fighters show up (and so a build
// with no backend running doesn't bake in an empty sitemap).
export const dynamic = "force-dynamic";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  const staticRoutes: MetadataRoute.Sitemap = [
    { url: `${SITE_URL}/`, lastModified: now, changeFrequency: "hourly", priority: 1 },
    {
      url: `${SITE_URL}/about`,
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.6,
    },
    {
      url: `${SITE_URL}/signup`,
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.5,
    },
  ];

  const fighters = await getFighters();
  const fighterRoutes: MetadataRoute.Sitemap = fighters.map((f) => ({
    url: `${SITE_URL}/fighter/${slugify(f.name)}`,
    lastModified: now,
    changeFrequency: "hourly",
    priority: 0.8,
  }));

  return [...staticRoutes, ...fighterRoutes];
}
