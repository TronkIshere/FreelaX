# FreelaX browser frontend

The browser talks only to Marketplace through same-origin `/api/v1`. Payment, MISA, Solana, MySQL, Redis, and signing services stay behind Marketplace.

## Local development

Set the required runtime credentials in the ignored repository-root `.env` (see `.env.example`). Demo Client, Freelancer, Payment test user, and MISA platform passwords must be distinct random values of at least 24 characters. On service startup, existing seed-account password hashes are updated to these values and their previous refresh sessions are revoked.

Run Marketplace, then from `frontend/`:

```bash
npm ci
npm run dev
```

Open `http://localhost:3000`. Vite proxies `/api/v1` to Marketplace. The browser uses the HttpOnly refresh cookie and resolves CLIENT/FREELANCER through `GET /auth/me`.

## Local production build

`frontend/Dockerfile` builds static assets and serves them with Caddy. The default `VITE_MARKETPLACE_API_ORIGIN` is blank, so all browser API calls stay on the current origin. Caddy proxies only `/api/v1/*` to `marketplace-backend:9191` and serves `index.html` for React Router deep links.

```bash
docker compose --profile deploy config --quiet
docker compose --profile deploy up -d
```

The preview is `http://localhost:8080`; `/healthz` is a local health check. All Compose host ports bind to `127.0.0.1`. Containers created before this binding change must be recreated to apply it. Keep the validator private as well. No internal service port should be forwarded or published.

## Safety and validation

Keep all real passwords, JWT signing secrets, internal API keys, and Solana signer material in ignored `.env` or a secret manager. Never put them in any `VITE_` variable. Marketplace is the only browser-facing API. Payment, MISA, Solana Gateway, MySQL, Redis, and validator ports remain local.

```bash
cd frontend
npm test
npm run build
```

Check Client and Freelancer login, role from `/auth/me`, refresh, all five navigation areas, SPA deep links, financial/tax views, and accepted-certificate PDF/XML through `http://localhost:8080`.
