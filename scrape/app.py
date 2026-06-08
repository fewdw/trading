import logging

from flask import Flask

from scrape import get_all_fighters

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
)

app = Flask(__name__)
app.json.ensure_ascii = False


@app.get("/fighters")
def fighters():
    return get_all_fighters(as_json=False)


if __name__ == "__main__":
    # "::" binds both IPv6 and IPv4 (dual-stack). Required for Railway's
    # private networking, which resolves service DNS over IPv6; still works
    # locally / in docker-compose where peers connect over IPv4.
    app.run(host="::", port=5001)
