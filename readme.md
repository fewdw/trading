### Docker Compose

```bash
docker compose down -v && docker compose build --no-cache && docker compose up
```

```bash
docker compose up --build
```

### TODOS, (dont implement now):

- user can click profile to show its portfolio (p&nl, realized / unrealized gains, etc, url is /<their own username>, and you can view peoples profile by going to /<username>)
- live price chart update when you buy or sell using webhook, and live webhook updates for everything like other ppls bids and asks
- make better scraper
