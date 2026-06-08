### Docker Compose

```bash
docker compose down -v && docker compose build --no-cache && docker compose up
```

```bash
docker compose up --build
```

### TODOS, (dont implement now):

- user can click profile to show its portfolio (p&nl, realized / unrealized gains, etc, url is /<their own username>, and you can view peoples profile by going to /<username>)
- on the homepage you should get live updates of prices in through websickets, use up arrow or downarrow to know if price is going up or down. down arrow make price red and up arrow make it green. also remove coin emoji nexto fighters prices.
- make the searchbar centered in top navbar
- live price chart update when you buy or sell using webhook, and live webhook updates for everything like other ppls bids and asks
- make better scraper
