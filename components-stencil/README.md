# @habitloop/components — Stencil web component library

Reusable, framework-agnostic custom elements for the HabitLoop UI (Session 2). Built with Stencil so they
work in SvelteKit or any framework, with typed props and a generated loader.

## Components
| Tag | Props | Notes |
|-----|-------|-------|
| `<hl-progress-ring>` | `value`, `max`, `label` | circular progress (streaks, challenge progress) |
| `<hl-chart>` | `points` (JSON string) | minimal bar chart for trends |
| `<hl-stat-card>` | `label`, `value` | KPI / activity-summary tile |
| `<hl-habit-card>` | `name`, `habit-id`, `current`, `longest` | emits `hlComplete` |
| `<hl-challenge-card>` | `challenge-title`, `visibility`, `progress`, `target` | progress bar; use `challengeTitle` as a JavaScript property instead of `title` |
| `<hl-partner-tile>` | `partner`, `status`, `connection-id` | emits `hlRevoke` |
| `<hl-recommendation-card>` | `type`, `headline`, `message`, `confidence` | |

Components read theme CSS variables (`--primary`, `--surface`, `--border`, …) which pierce the shadow DOM,
so they match the host app's light/dark theme automatically.

## Build
```bash
npm install
npm run build        # outputs dist/ and loader/
npm test             # tests component calculation and input parsing
```
Consumed by the frontend via `import { defineCustomElements } from '@habitloop/components/loader'`.
