# FreelaX browser frontend

The browser talks only to Marketplace `/api/v1`. Payment, MISA, Solana, database, and signing services remain behind Marketplace.

## Local development

Run Marketplace on `http://localhost:9191`, then:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:3000`. Vite proxies `/api/v1` to Marketplace in development. The browser uses the HttpOnly refresh cookie and resolves its CLIENT/FREELANCER role through `GET /auth/me`.

## Production image and local preview

`frontend/Dockerfile` builds static Vite assets and serves them with Caddy. Caddy sends only `/api/v1/*` to `marketplace-backend:9191`; all other unknown paths fall back to `index.html` for React Router. The default browser API origin is the same origin, so no internal service address appears in the bundle.

Copy `.env.example` to ignored `.env` and supply `MARKETPLACE_JWT_SECRET` with the current Marketplace signing secret. A changed JWT secret invalidates existing sessions. Compose requires this value at startup. Never commit the value.

For a loopback-only preview of the production build while the backend is already running:

```bash
docker compose --profile deploy config --quiet
docker compose --profile deploy up -d --no-deps --build frontend
```

With the local defaults, open `http://localhost:8080/healthz` and `http://localhost:8080/work`. Ports 8080 and 8443 bind only to `127.0.0.1`. Compose config binds backend, database, and Redis host ports to loopback; existing containers retain old port bindings until they are recreated. Containers still communicate on the Compose network.

## Public deployment: freelax.freedev.app

Public exposure remains blocked: Marketplace DataInitializer creates seed accounts with fixed credentials, and Compose has development credential defaults. Before opening ports 80/443, an authorized backend change must secure seeding, seed credentials must be rotated, development credentials replaced, and the ingress host verified. Until then, keep this loopback preview and leave DNS unchanged. Once these gates are met, set these values on the actual deployment host in ignored runtime configuration or a secret manager:

```dotenv
FREELAX_SITE_ADDRESS=freelax.freedev.app
FREELAX_HTTP_BIND=0.0.0.0:80
FREELAX_HTTPS_BIND=0.0.0.0:443
MARKETPLACE_CORS_ALLOWED_ORIGINS=https://freelax.freedev.app
MARKETPLACE_SPRING_PROFILE=prod
MARKETPLACE_JWT_SECRET=<existing-secret-from-secure-store>
```

Also replace every development password and internal API key before exposing the host. Keep Solana signer material and all service credentials outside Git. Only ports 80 and 443 should be reachable publicly. Preserve the `caddy-data` volume so Caddy can keep its HTTPS certificate. Caddy requests a certificate and redirects HTTP to HTTPS when the domain resolves to this host and ports 80/443 reach it.

Point the DNS **A** record for `freelax.freedev.app` to the deployment ingress public IPv4 address. Add an **AAAA** record only if IPv6 ingress is working. The domain must not be pointed to an arbitrary workstation or NAT address without forwarding and firewall checks.

After DNS and ingress are ready, validate the resolved Compose configuration without printing secrets, then start the deployment profile:

```bash
docker compose --profile deploy config --quiet
docker compose --profile deploy up -d --build
```

Verify `https://freelax.freedev.app/healthz`, a deep link such as `/work`, login/session refresh, and `/api/v1` routing before announcing the domain.

`VITE_MARKETPLACE_API_ORIGIN` is optional and public. Leave it blank for same-origin routing. If a separate API host is used, set only its HTTPS origin at build time and include that exact frontend origin in `MARKETPLACE_CORS_ALLOWED_ORIGINS`. Never put JWT secrets, keys, database credentials, or internal URLs in any `VITE_` variable.

## Validation

```bash
cd frontend
npm test
npm run build
```
