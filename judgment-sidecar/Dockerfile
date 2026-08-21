# The factory's judgment layer runs on the operator's Claude subscription, never on a metered API key.
#
# Separate, tiny image on purpose - the same rule the operator set for runtime-launcher and wrote into
# docker-compose.yml: contain a privilege in the smallest separately-reviewable surface, never mount it
# into the much larger main backend. A Claude credential is exactly that kind of privilege.
FROM node:22-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates git ripgrep \
    && rm -rf /var/lib/apt/lists/*
RUN npm install -g @anthropic-ai/claude-code
ENV HOME=/home/judge
RUN mkdir -p /home/judge/.claude && chmod -R 777 /home/judge
WORKDIR /app
COPY server.js .
EXPOSE 8092
CMD ["node", "server.js"]
