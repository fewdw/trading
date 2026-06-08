# syntax=docker/dockerfile:1.7

FROM python:3.13-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Dependencies first (cached until requirements.txt changes); the cache mount
# keeps the wheel cache out of the image while speeding up rebuilds.
COPY requirements.txt ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt

COPY scrape.py app.py ./

# Run unprivileged.
RUN useradd --system --create-home app
USER app

EXPOSE 5001
# Gunicorn instead of Flask's dev server. "[::]" binds IPv6 dual-stack (also
# accepts IPv4) for Railway's private networking. The generous timeout covers
# the long-running rankings scrape; gthread workers handle concurrent requests.
CMD ["gunicorn", "--bind", "[::]:5001", "--workers", "2", "--threads", "4", "--timeout", "120", "--access-logfile", "-", "app:app"]
