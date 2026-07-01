export type LeanGreeting = {
  id: string;
  message: string;
  currentStatus: 'RECEIVED' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED';
  createdAt: string;
  leadTimeSeconds: number;
};

export type Agent = {
  accountId: string;
  name: string;
  status: 'idle' | 'busy' | 'offline';
  currentRoleTag?: string;
  currentTaskDescription?: string;
  claimedAt?: string;
  leaseExpiresAt?: string;
  lastHeartbeat?: string;
};

export type TagCount = {
  tag: string;
  count: number;
  oldestWaitingMinutes: number;
};

export type QueueData = {
  byTag: TagCount[];
  totalQueued: number;
};

export type Bottleneck = {
  type: 'no_capable_agent' | 'expired_lease_spike';
  tag?: string;
  accountId?: string;
  queuedCount?: number;
  waitingMinutes?: number;
  expiredCount24h?: number;
  reason: string;
};

export type PipelineData = {
  queued: number;
  claimed: number;
  in_progress: number;
  review: number;
  done: number;
  failed: number;
};
