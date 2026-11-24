# HabitLoop Frontend (SvelteKit + Svelte 5 Runes + Stencil)

Production-shaped SaaS UI for HabitLoop. Session 2 of the assignment.

> **Not yet built here.** Node/npm were not available in the authoring environment, so this app has not
> been `npm install`-ed or compiled. Run the steps below locally to build and run it.

## Architecture decisions
- **SvelteKit 2 + Svelte 5 Runes** (`$state`, `$derived`, `$props`) for state.
- **SSR with `@sveltejs/adapter-node`** (frontend runs as a Node server / ECS container behind the ALB),
  **not** static S3. Rationale: the brief requires **SvelteKit form actions** (create activity, accept
  invite, connect integration) and secure **httpOnly-cookie JWT auth** — both need a server. All backend
  calls happen server-side (`src/lib/server/backend.ts`), so there are no CORS concerns and the JWT never
  reaches the browser. A pure-SPA variant on S3+CloudFront (client-side token) remains possible if static
  hosting is required instead.
- **Stencil** component library in [`../components-stencil`](../components-stencil) for reusable,
  framework-agnostic web components (`hl-progress-ring`, `hl-chart`, `hl-stat-card`, `hl-habit-card`,
  `hl-challenge-card`, `hl-partner-tile`, `hl-recommendation-card`), registered on the client in the root
  layout's `onMount`. If the library isn't built, those elements degrade gracefully and pages still work.

## Auth & routing
- Login/register are form actions that call user-service and set an **httpOnly `hl_token` cookie**.
- `src/hooks.server.ts` reads the cookie, exposes `locals.user`, and **guards** every route outside
  `/login` and `/register` (redirect to `/login`), bouncing logged-in users off the auth pages.
- Protected pages live in the `(app)` route group with a shared sidebar layout.

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
cp .env.example .env     # point at your running backend (defaults = local ports)
npm run dev              # http://localhost:5173
```
Start the backend first (see ../RUNNING.md for the Docker Compose stack). Then open http://localhost:5173
and register, or log in with the seeded demo user `ria@acme.com` / `Password123!`.

## Build for production
```bash
cd frontend && npm run build && node build    # adapter-node server on PORT (default 3000)
```
Containerize this `build/` output as an ECS service behind the ALB (same origin as `/api/*`).

## Project layout
```
frontend/
├── src/
│   ├── hooks.server.ts            # JWT cookie + route guard
│   ├── app.css                    # design tokens (light/dark)
│   ├── lib/
│   │   ├── server/backend.ts      # typed server-side API client (per service)
│   │   ├── stores/toast.svelte.ts # Svelte 5 runes store
│   │   ├── components/            # Svelte layout (Sidebar, Topbar, Toasts)
│   │   ├── cookies.ts, jwt.ts, types.ts
│   └── routes/
│       ├── login/ register/ logout/
│       └── (app)/                 # protected group (dashboard, habits, activity, challenges, …)
```
