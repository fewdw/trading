import json
import re
import unicodedata
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter

try:
    from urllib3.util.retry import Retry
except Exception:
    Retry = None

PHOTO_WORKERS = 16
HTTP_TIMEOUT = 15

_OG_IMAGE_RE = re.compile(r'<meta property="og:image" content="([^"]+)"')
_NON_ALNUM_RE = re.compile(r"[^a-z0-9]+")


def get_all_fighters(url="https://www.ufc.com/rankings", as_json=True):
    """
    Scrape UFC rankings and return a flat, de-duplicated list of every fighter
    (champions included) with their headshot photo, regardless of page language:

        [
            {"name": "name", "photo": "https://..."},
            {"name": "name", "photo": "https://..."},
            ...
        ]
    """
    # Pooled session: TCP+TLS to ufc.com negotiated once and kept alive.
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0 Safari/537.36"
            )
        }
    )
    pool = max(PHOTO_WORKERS, 10)
    if Retry is not None:
        retry = Retry(
            total=2,
            backoff_factor=0.3,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=frozenset(["GET"]),
        )
        adapter = HTTPAdapter(
            pool_connections=pool, pool_maxsize=pool, max_retries=retry
        )
    else:
        adapter = HTTPAdapter(pool_connections=pool, pool_maxsize=pool)
    session.mount("https://", adapter)
    session.mount("http://", adapter)

    def photo(name):
        # strip accents (Procházka -> Prochazka), drop apostrophes, hyphenate rest
        s = (
            unicodedata.normalize("NFKD", name)
            .encode("ascii", "ignore")
            .decode()
            .lower()
        )
        s = s.replace("'", "")
        s = _NON_ALNUM_RE.sub("-", s).strip("-")
        try:
            resp = session.get(f"https://www.ufc.com/athlete/{s}", timeout=HTTP_TIMEOUT)
            m = _OG_IMAGE_RE.search(resp.text)
            return m.group(1) if m else None
        except requests.RequestException:
            return None

    resp = session.get(url, timeout=HTTP_TIMEOUT)
    resp.raise_for_status()
    soup = BeautifulSoup(resp.text, "html.parser")

    # dict = insertion-ordered dedup in one structure
    seen = {}
    for grouping in soup.select("div.view-grouping"):
        champ_link = grouping.select_one("caption .info h5 a")
        if champ_link and champ_link.get_text(strip=True):
            seen.setdefault(champ_link.get_text(strip=True), None)
        for row in grouping.select("tbody tr"):
            cell = row.select_one("td.views-field-title a")
            if cell and cell.get_text(strip=True):
                seen.setdefault(cell.get_text(strip=True), None)

    names = list(seen)

    # Fetch all photos in parallel (each unique name once).
    if names:
        workers = min(PHOTO_WORKERS, len(names))
        with ThreadPoolExecutor(max_workers=workers) as ex:
            futures = {ex.submit(photo, n): n for n in names}
            for fut in as_completed(futures):
                n = futures[fut]
                try:
                    seen[n] = fut.result()
                except Exception:
                    seen[n] = None

    fighters = [{"name": n, "photo": seen[n]} for n in names]

    if as_json:
        return json.dumps(fighters, ensure_ascii=False, indent=2)
    return fighters
