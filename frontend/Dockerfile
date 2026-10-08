FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_MARKETPLACE_API_ORIGIN=
ENV VITE_MARKETPLACE_API_ORIGIN=${VITE_MARKETPLACE_API_ORIGIN}
ARG VITE_SOLANA_CLUSTER=localnet
ENV VITE_SOLANA_CLUSTER=${VITE_SOLANA_CLUSTER}
RUN npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
