# syntax=docker/dockerfile:1.7

FROM python:3.13-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Dependencies first, so this layer is cached until requirements.txt changes.
COPY requirements.txt ./
RUN pip install -r requirements.txt

COPY personas.py api.py strategist.py executor.py main.py ./

# Run unprivileged.
RUN useradd --system --create-home app
USER app

# A long-running worker (no HTTP port): it loops forever placing trades. Logs go
# to stdout for Docker/Railway aggregation.
CMD ["python", "main.py"]
