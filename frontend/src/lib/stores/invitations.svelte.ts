import { api } from '$lib/api';

const REFRESH_INTERVAL_MS = 60_000;

export const pendingInvitations = $state<{ count: number | null }>({ count: null });
let lastLoaded = 0;
let inFlight: Promise<void> | null = null;

/** Refreshes the pending-invitation count; throttled unless forced. Failures keep the last known count. */
export function refreshPendingInvitations(force = false): Promise<void> {
  if (!force && (inFlight || Date.now() - lastLoaded < REFRESH_INTERVAL_MS)) return inFlight ?? Promise.resolve();
  inFlight = api.invitations.count()
    .then((count) => {
      pendingInvitations.count = count;
      lastLoaded = Date.now();
    })
    .catch(() => undefined)
    .finally(() => {
      inFlight = null;
    });
  return inFlight;
}

export function setPendingInvitationCount(count: number) {
  pendingInvitations.count = count;
  lastLoaded = Date.now();
}
