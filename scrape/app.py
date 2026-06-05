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
    app.run(host="0.0.0.0", port=5001)
