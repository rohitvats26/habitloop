export interface AuthUser {
  userId: string;
  tenantId: string;
  roles: string[];
}

export interface Habit {
  id: string;
  name: string;
  description?: string;
  frequency: 'DAILY' | 'WEEKLY' | 'CUSTOM';
  schedule: Record<string, unknown>;
  targetPerPeriod: number;
  active: boolean;
  streak: { current: number; longest: number };
}

export interface ActivityLog {
  id: string;
  habitId?: string;
  activityType: string;
  value?: number;
  unit?: string;
  occurredAt: string;
  source: 'MANUAL' | 'WEARABLE' | 'PARTNER';
}

export interface ChallengeSummary {
  id: string;
  title: string;
  description?: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  status: string;
  target: number;
  startsOn: string;
  endsOn: string;
}

export interface ChallengeDetail extends ChallengeSummary {
  myProgress?: number;
  completed: boolean;
  leaderboard: { userId: string; progress: number; completed: boolean }[];
}

export interface Invitation {
  id: string;
  challengeId: string;
  inviterId: string;
  inviteeId: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
}

export interface Connection {
  connectionId: string;
  partner: string;
  status: string;
  apiKey?: string;
  hmacSecret?: string;
  webhookUrl: string;
  connectedAt?: string;
  lastEventAt?: string;
}

export interface AnalyticsSummary {
  completedToday: number;
  weekCompletions: number;
  trend: { week: string; completions: number }[];
}

export interface Recommendation {
  type: string;
  habitId?: string;
  title: string;
  message: string;
  action: Record<string, unknown>;
  confidence: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
