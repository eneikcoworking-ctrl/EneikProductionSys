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

export type ProjectStatus = 'active' | 'waiting' | 'frozen' | 'accepted' | 'archived';

export type ProjectSummary = {
  id: string;
  name: string;
  slug?: string;
  repositoryName?: string;
  repositoryUrl?: string;
  repoUrl?: string;
  linearProjectKey?: string;
  githubRepositoryStatus?: string;
  githubRepositoryId?: string;
  linearProjectStatus?: string;
  linearProjectId?: string;
  workspacePath?: string;
  factoryStatus?: string;
  factoryReport?: string;
  status: ProjectStatus;
  createdAt: string;
  acceptedAt?: string;
  accountCount?: number;
  accountsCount?: number;
  tasksQueued?: number;
  tasksInProgress?: number;
  tasksDone?: number;
};

export type Project = ProjectSummary;

export type WishlistItem = {
  id: string;
  projectId: string;
  text: string;
  type: 'client_wish' | 'role_advice';
  status: 'open' | 'converted' | 'ignored';
  sourceRoleTag?: string;
  createdAt: string;
};

export type Task = {
  id: string;
  tag: string;
  description: string;
  status: 'queued' | 'claimed' | 'in_progress' | 'review' | 'done' | 'failed';
  payload?: unknown;
  julesSessionName?: string;
  julesDispatchStatus?: string;
};

export type ProjectDashboard = {
  project: ProjectSummary;
  agentCount: number;
  openWishlistCount: number;
  queue: QueueData;
  pipeline: PipelineData;
  agents: Agent[];
  wishlist: WishlistItem[];
  tasks: Task[];
};
