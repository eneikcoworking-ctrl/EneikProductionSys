FROM python:3.12-slim
WORKDIR /app

# Docker-outside-of-Docker: this is the ONLY component in the whole factory allowed to hold the host
# docker socket (docs/reports/PLAN_client_runtime_observability_2026-08-09.md, Phase 1 - operator
# explicitly chose the sidecar architecture over mounting the socket into the main backend, to keep
# this exact privilege contained to the smallest possible surface).
# 2026-08-11 (live incident): docker.io on Debian trixie only ships the daemon (dockerd, docker-proxy,
# docker-init) - the actual `docker` CLI client is a SEPARATE package, docker-cli. Every /launch call
# failed with "docker: not found" until this was added; confirmed via `dpkg -L docker.io` inside the
# built image never listing /usr/bin/docker.
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io docker-cli git ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

# docker-compose-v2 is not a real apt package name on every base image's Debian release (confirmed:
# absent on python:3.12-slim's trixie repos, present as docker.io on eclipse-temurin's) - install the
# official static compose plugin binary directly instead, portable across any base image.
RUN mkdir -p /root/.docker/cli-plugins \
    && curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.1/docker-compose-linux-x86_64" \
        -o /root/.docker/cli-plugins/docker-compose \
    && chmod +x /root/.docker/cli-plugins/docker-compose

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY launcher.py .
EXPOSE 8091
CMD ["uvicorn", "launcher:app", "--host", "0.0.0.0", "--port", "8091"]
