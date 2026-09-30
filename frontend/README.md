# FreelaX frontend foundation

This browser frontend is the P05.1 foundation for the real Marketplace facade. It starts from the frozen backend/runtime commit and uses the D1/D2 design decisions and interactive demo v0.2 as visual references. No historical Flutter code or demo fixtures are shipped.

## Run locally

Requires Node.js 20.19+ or 22.12+ and a Marketplace backend on `http://localhost:9191`.

```bash
npm install
npm run dev
```

Open `http://localhost:3000/` (the origin allowed by the current Marketplace CORS configuration). The development server proxies only `/api/v1/**` to Marketplace. A production host must route the same path to Marketplace; the browser never calls payment, MISA, Solana gateway, or RPC directly.

```bash
npm test
npm run build
```

## Session and role

Sign-in sends email/password to `POST /api/v1/auth/sign-in`. The frontend keeps the access token in memory. The backend sets the HttpOnly refresh cookie; on page reload, the frontend calls `POST /api/v1/auth/refresh-token` with credentials and then `GET /api/v1/auth/me`. The role shown in navigation always comes from `/auth/me`. A missing or unrecognized role blocks the shell. Sign-out sends the current access token to `POST /api/v1/auth/sign-out`; if the request fails, the session remains visible so the user can retry.

Use `localhost` consistently for the browser and backend. The backend's refresh cookie is marked Secure, so browser handling of that cookie must be verified in the intended HTTPS deployment and local browser environment.

## Scope

Client work uses the paged participant endpoint. Freelancer discovery uses the paged discover endpoint with keyword, USD budget range, sort, and application filters. The remaining role navigation is an honest placeholder for later workpacks. No apply, job edit, payment, payout, or tax action is implemented here.
