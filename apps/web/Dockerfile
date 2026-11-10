# The studio's static demo build (mocked data, no API) served by an unprivileged nginx (dp.2). Build context: apps/web.
# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /web
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
COPY package.json pnpm-lock.yaml ./
# the store path is what `pnpm store path` prints in this image
RUN --mount=type=cache,target=/root/.local/share/pnpm/store/v3 pnpm install --frozen-lockfile
COPY . .
RUN pnpm build:demo

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
