/** Turn a fighter name into a URL slug, e.g. "Islam Makhachev" -> "islam-makhachev". */
export function slugify(name: string): string {
  return name
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}
