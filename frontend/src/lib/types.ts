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
  type: 'no_free_jules_slot' | 'expired_lease_spike';
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

export type ProjectStatus = 'active' | 'analyzing' | 'waiting' | 'frozen' | 'accepted' | 'archived';

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
  status: 'queued' | 'claimed' | 'in_progress' | 'review' | 'done' | 'failed' | 'blocked' | 'spike_completed';
  payload?: unknown;
  julesSessionName?: string;
  julesDispatchStatus?: string;
  dependsOn?: string;
  qualityGatePassed?: boolean;
  priority?: number;
  cynefinDomain?: string;
};

export type EmsFlowStage = {
  stage: string;
  label: string;
  total: number;
  queued: number;
  active: number;
  done: number;
  blocked: number;
  completionRate: number;
  weightedScore: number;
};

export type EmsRoleDoctrineVerdict = {
  roleTag: string;
  doctrineName: string;
  doctrineFocus: string;
  stance: 'satisfied' | 'almost_satisfied' | 'objects' | 'refuses' | 'unknown';
  satisfactionScore: number;
  confidence: number;
  kanoPressure: 'none' | 'discovery' | 'must_be' | 'performance' | string;
  cynefinBias: string;
  topObjection: string;
  sourceWishlistPending: number;
  sourceWishlistTotal: number;
  ownerTasksTotal: number;
  ownerTasksOpen: number;
  ownerTasksBlocked: number;
  ownerTasksDone: number;
  defectWork: number;
  evidence: string[];
};

export type EmsRoleDoctrineReadiness = {
  roles: EmsRoleDoctrineVerdict[];
  rolesEvaluated: number;
  satisfied: number;
  almostSatisfied: number;
  objects: number;
  refuses: number;
  unknown: number;
  readinessScore: number;
  statusLabel: 'ready' | 'incomplete' | 'contested' | 'blocked' | string;
  interpretation: string;
};

export type EmsRoleKpi = {
  roleTag: string;
  total: number;
  queued: number;
  active: number;
  done: number;
  blocked: number;
  failed: number;
  defectWork: number;
  retryLoad: number;
  completionRate: number;
  gatePassRate: number;
  defectPressure: number;
  flowEfficiency: number;
  kpiScore: number;
  kpiTarget: number;
  statusLabel: 'on_target' | 'watch' | 'attention' | 'behind' | 'idle';
};

export type EmsDashboardMetrics = {
  generatedAt: string;
  flowChart: {
    stages: EmsFlowStage[];
    totalTasks: number;
    completionRate: number;
    weightedProgress: number;
  };
  roleDoctrineReadiness: EmsRoleDoctrineReadiness;
  roleKpis: EmsRoleKpi[];
  defectWork: {
    totalDefectWork: number;
    openDefectWork: number;
    blockedTasks: number;
    failedTasks: number;
    retryLoad: number;
    defectPressure: number;
    dpmo: number;
    interpretation: string;
  };
  graphHealth: {
    graphTasks: number;
    uniqueGraphs: number;
    linkedEdges: number;
    blockedByDependency: number;
    duplicateSemanticKeys: number;
    graphCoverage: number;
    dependencyCoverage: number;
    criticalPathLength: number;
    interpretation: string;
  };
  rules: string[];
};

export type ProductReadiness = {
  totalFeatures: number;
  completeFeatures: number;
  totalPlannedTasks: number;
  mergedPlannedTasks: number;
  mergedRatio: number;
  decompositionComplete: boolean;
  falsificationThreshold: number;
  falsificationEligible: boolean;
  status: 'decomposing' | 'building' | 'ready_for_falsification' | string;
};

export type ProjectDashboard = {
  project: ProjectSummary;
  agentCount: number;
  openWishlistCount: number;
  queue: QueueData;
  pipeline: PipelineData;
  productReadiness?: ProductReadiness;
  emsMetrics?: EmsDashboardMetrics;
  agents: Agent[];
  wishlist: WishlistItem[];
  tasks: Task[];
};
