### Docker Compose

run

```bash
docker compose up --build
```

run + reset DB

```bash
docker compose down -v && docker compose build --no-cache && docker compose up
```
