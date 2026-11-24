# HabitLoop Frontend (SvelteKit + Svelte 5 Runes + Stencil)

Production-shaped SaaS UI for HabitLoop. Session 2 of the assignment.

> **Not yet built here.** Node/npm were not available in the authoring environment, so this app has not
> been `npm install`-ed or compiled. Run the steps below locally to build and run it.

## Architecture decisions
- **SvelteKit 2 + Svelte 5 Runes** (`$state`, `$derived`, `$props`) for state.
- **Static SPA via `@sveltejs/adapter-static`** (hosted on **Amazon S3**, per the case-study brief). The
  whole app is client-rendered (`+layout.ts` sets `ssr = false`), with an `index.html` fallback so
  client-side routing and dynamic routes (`/challenges/[id]`) work on S3.
- **Client-side auth:** since a static site has no server, the JWT is stored in `localStorage` and sent as a
  `Authorization: Bearer` header by the client API module (`src/lib/api.ts`). This is the documented
  trade-off of S3 static hosting — a Node/SSR variant could instead keep an httpOnly cookie, but the brief
  specifies S3 for the frontend. Because the browser now calls the backend cross-origin, the services enable
  **CORS** (`habitloop.cors.allowed-origins`, default `*`).
- **Stencil** component library in [`../components-stencil`](../components-stencil) for reusable,
  framework-agnostic web components (`hl-progress-ring`, `hl-chart`, `hl-stat-card`, `hl-habit-card`,
  `hl-challenge-card`, `hl-partner-tile`, `hl-recommendation-card`), registered on the client in the root
  layout's `onMount`. If the library isn't built, those elements degrade gracefully and pages still work.

## Auth & routing
- Login/register call user-service from the browser (`api.auth.*`) and store the JWT via
  `src/lib/auth.svelte.ts` (runes state + `localStorage`).
- The `(app)` route group has a client guard (`(app)/+layout.ts`) that redirects to `/login` when there is
  no token. Each page loads its data in `onMount` via `src/lib/api.ts`.

## Pages (all 14 required)
Register · Login · Onboarding · Dashboard · Habits · Activity history · Challenge feed · Challenge details
· Create challenge · Invitations · Integrations · (Partner connection is part of Integrations) · Analytics
· Recommendations · Profile & settings.

## Run locally
```bash
# 1. Build the Stencil component library first
cd components-stencil
npm install
npm run build

# 2. Install and run the SvelteKit app
cd ../frontend
npm install
cp .env.example .env     # VITE_* base URLs (defaults = local service ports)
npm run dev              # http://localhost:5173
```
Start the backend first (see ../RUNNING.md for the Docker Compose stack). Then open http://localhost:5173
and register, or log in with the seeded demo user `ria@acme.com` / `Password123!`.

## Build for S3
```bash
cd frontend
# point every VITE_*_SERVICE_URL at the ALB URL (see .env.example), then:
npm run build                      # outputs static site to ./build
aws s3 sync build/ s3://<frontend-bucket>/ --delete   # bucket name = terraform output frontend_bucket
```
The S3 website URL is `terraform output frontend_url`. (S3 website endpoints are HTTP-only; HTTPS/global
CDN would need CloudFront, which is outside the brief's resource list.)

## Project layout
```
frontend/
├── src/
│   ├── app.css                    # design tokens (light/dark)
│   ├── lib/
│   │   ├── api.ts                 # client API (Bearer header, VITE_* base URLs)
│   │   ├── auth.svelte.ts         # client auth state (token in localStorage)
│   │   ├── stores/toast.svelte.ts # Svelte 5 runes store
│   │   ├── components/            # Svelte layout (Sidebar, Topbar, Toasts)
│   │   ├── jwt.ts, types.ts
│   └── routes/
│       ├── +layout.ts             # ssr=false (static SPA)
│       ├── login/ register/
│       └── (app)/                 # guarded group (+layout.ts), dashboard, habits, activity, challenges, …
```
