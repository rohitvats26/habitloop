# HabitLoop Frontend (SvelteKit + Svelte 5 Runes + Stencil)

Production-shaped SaaS UI for HabitLoop. Session 2 of the assignment.

> **Build verified:** `npm run check` and `npm run build` pass; the Stencil component library also builds.

## Architecture decisions
- **SvelteKit 2 + Svelte 5 Runes** (`$state`, `$derived`, `$props`) for state.
- **Static SPA via `@sveltejs/adapter-static`** (hosted on **Amazon S3**, per the case-study brief). The
  whole app is client-rendered (`+layout.ts` sets `ssr = false`), with an `index.html` fallback so
  client-side routing and dynamic routes (`/challenges/[id]`) work on S3.
- **Client-side auth:** since a static site has no server, the JWT is stored in `sessionStorage` and sent as a
  `Authorization: Bearer` header by the client API module (`src/lib/api.ts`). This is the documented
  trade-off of S3 static hosting: tokens are still accessible to same-origin scripts, but sessionStorage
  clears them when the tab closes. Expired tokens are rejected by the client guard. Habit completion and
  challenge defaults use India Standard Time (`Asia/Kolkata`). Production serves the UI and routes
  `/api/*` through CloudFront to the ALB; local development calls service ports directly.
- **Stencil** component library in [`../components-stencil`](../components-stencil) for reusable,
  framework-agnostic web components (`hl-progress-ring`, `hl-chart`, `hl-stat-card`, `hl-habit-card`,
  `hl-challenge-card`, `hl-partner-tile`, `hl-recommendation-card`), registered on the client in the root
  layout's `onMount`. If registration fails, the app displays an error banner; build the component library
  before running the frontend.

## Auth & routing
- Login/register call user-service from the browser (`api.auth.*`) and store the JWT via
  `src/lib/auth.svelte.ts` (runes state + `sessionStorage`).
- The `(app)` route group has a client guard (`(app)/+layout.ts`) that redirects to `/login` when there is
  no valid, unexpired token. Each page loads its data in `onMount` via `src/lib/api.ts`.

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
Run `npm test` for frontend timezone/date utility tests.
Start the backend first (see ../RUNNING.md for the Docker Compose stack). Then open http://localhost:5173
and register, or log in with the seeded demo user `ria@acme.com` / `Password123!`.

## Build for S3
```bash
cd frontend
# point every VITE_*_SERVICE_URL at the HTTPS CloudFront URL (see .env.example), then:
npm run build                      # outputs static site to ./build
aws s3 sync build/ s3://<frontend-bucket>/ --delete   # bucket name = terraform output frontend_bucket
```
The HTTPS CloudFront URL is `terraform output frontend_url`; the S3 origin is private and accessible only
through CloudFront.

## Project layout
```
frontend/
├── src/
│   ├── app.css                    # design tokens (light/dark)
│   ├── lib/
│   │   ├── api.ts                 # client API (Bearer header, VITE_* base URLs)
│   │   ├── auth.svelte.ts         # client auth state (token in sessionStorage)
│   │   ├── stores/toast.svelte.ts # Svelte 5 runes store
│   │   ├── components/            # Svelte layout (Sidebar, Topbar, Toasts)
│   │   ├── jwt.ts, types.ts
│   └── routes/
│       ├── +layout.ts             # ssr=false (static SPA)
│       ├── login/ register/
│       └── (app)/                 # guarded group (+layout.ts), dashboard, habits, activity, challenges, …
```
