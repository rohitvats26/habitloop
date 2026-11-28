import type { ChallengeDetail } from './types';

export function canJoinChallenge(challenge: Pick<ChallengeDetail, 'visibility' | 'status'>, joined: boolean): boolean {
  return !joined && challenge.visibility === 'PUBLIC' && challenge.status === 'ACTIVE';
}

export function memberLabel(userId: string, displayName: string | null | undefined, currentUserId?: string | null): string {
  const name = displayName?.trim();
  if (userId === currentUserId) return name ? `${name} (you)` : 'You';
  return name || `Member ${userId.slice(0, 8)}`;
}

/** Returns a user-facing problem with the challenge date range, or an empty string when valid. */
export function challengeDateError(startsOn: string, endsOn: string, today: string): string {
  if (!startsOn || !endsOn) return 'Choose both a start date and an end date.';
  if (endsOn < startsOn) return 'The end date must be on or after the start date.';
  if (endsOn < today) return 'The end date must be today or later so participants can take part.';
  return '';
}
