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
  featureReadinessRatio: number;
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

export type OperationalTruth = {
  generatedAt: string;
  mode: string;
  project: {
    id: string;
    name: string;
    status: string;
    repositoryName?: string;
  };
  delivery: {
    totalFeatures: number;
    completeFeatures: number;
    totalPlannedTasks: number;
    mergedPlannedTasks: number;
    featureReadinessRatio: number;
    mergedRatio: number;
    decompositionComplete: boolean;
    status: string;
    headline: string;
  };
  trust: {
    score: number;
    level: 'trusted' | 'watch' | 'degraded' | 'blocked' | string;
    positiveSignals: string[];
    warnings: string[];
  };
  activeFlow: {
    queued: number;
    active: number;
    review: number;
    done: number;
    failed: number;
    pendingWishlist: number;
    compilingWishlist: number;
    openSessions: number;
    narrative: string[];
  };
  blockedValue: {
    count: number;
    headline: string;
    blockers: Array<{
      type: string;
      severity: 'high' | 'medium' | 'low' | string;
      subjectId: string;
      title: string;
      reason: string;
    }>;
  };
  evidence: {
    mergedReviews: number;
    openReviews: number;
    pendingReviews: number;
    failingReviews: number;
    qualityGatePassed: number;
    qualityGateFailed: number;
    screenshots: number;
    strongestSignals: Array<{
      kind: string;
      strength: number;
      subject: string;
      meaning: string;
    }>;
  };
  defects: {
    recentDefects: number;
    items: Array<{
      severity: string;
      category: string;
      component: string;
      defectType: string;
      description: string;
    }>;
  };
  learning: {
    candidateDefects: number;
    invariantsObserved: number;
    unresolvedLearning: string[];
  };
  recommendedNextAction: string;
};

export type TocDbrStatus = {
  primaryConstraintNode: string;
  constraintQueueLength: number;
  constraintUtilization: number;
  constraintMeanDurationMs: number;
  bufferSize: number;
  maxBufferCapacity: number;
  ropeThrottlingActive: boolean;
  lastEvaluatedAt: string;
  recommendation: string;
};

export type TocNodeDto = {
  name: string;
  inFlightCount: number;
  completedCount: number;
  errorCount: number;
  meanDurationMs: number;
  stdDevMs: number;
  throughput: number;
  utilization: number;
  stallBottleneck: boolean;
  primaryConstraint: boolean;
};

export type TocEdgeDto = {
  sourceNode: string;
  targetNode: string;
  transitionCount: number;
};

export type TocAnomalyReport = {
  id: string;
  type: 'CYCLE_DETECTED' | 'STALL_DETECTED' | 'DEADLOCK_DETECTED' | 'BUFFER_OVERFLOW';
  tokenId?: string;
  nodeName?: string;
  resourceId?: string;
  details: string;
  actionTaken: string;
  timestamp: string;
};

export type TocGraphData = {
  nodeCount: number;
  nodes: TocNodeDto[];
  edges: TocEdgeDto[];
  activeTokenCount: number;
  arrivalRatePerSec: number;
};

export type SixSigmaAuditReport = {
  projectId?: string;
  projectName?: string;
  totalOpportunities: number;
  totalDefects: number;
  dpmo: number;
  yieldRatePercent: number;
  sigmaLevel: number;
  qualityTier: string;
  defectBreakdown: Record<string, { opportunities: number; defects: number; dpmo: number }>;
  tocOperationalMetrics: Record<string, unknown>;
  auditedAt: string;
};

// Corrected 2026-08-10 to match the real backend enum (KaizenProposal.Category) - the previous
// version had a nonexistent SPEED_OPTIMIZATION and was missing the 3 categories that actually route
// proposals into the Кузница rooms (SYSTEMIC_DEFECT/ROLE_QUALITY_DRIFT -> Factory room,
// PRODUCT_RUNTIME_DEFECT -> Product room; WASTE_REDUCTION/DEFECT_ELIMINATION/BUFFER_TUNING -> Delivery
// room).
export type KaizenProposalDto = {
  id: string;
  title: string;
  category: 'WASTE_REDUCTION' | 'DEFECT_ELIMINATION' | 'BUFFER_TUNING' | 'SYSTEMIC_DEFECT'
    | 'ROLE_QUALITY_DRIFT' | 'PRODUCT_RUNTIME_DEFECT';
  targetComponent: string;
  actionDescription: string;
  expectedGainPercent: number;
  projectId?: string;
  projectName?: string;
  createdAt: string;
  status: 'PROPOSED' | 'APPLIED' | 'STANDARDIZED' | 'REVERTED';
  baselineMetric?: number;
  postMetric?: number;
  appliedAt?: string;
};

// GET /api/projects/{id}/runtime-health (2026-08-10) - Роща canopy glow + Кузница/Product room.
export type RuntimeObservationDto = {
  id: string;
  projectId: string;
  observedAt: string;
  launchSuccess: boolean;
  launchDurationMs?: number;
  healthStatusCode?: number;
  healthLatencyMs?: number;
  errorText?: string;
};

export type RuntimeHealthSummary = {
  observationCount: number;
  posteriorMean: number;
  credibleIntervalWidth: number;
  lastObservationHealthy: boolean | null;
  lastObservedAt: string | null;
  recentObservations: RuntimeObservationDto[];
  // 2026-08-11 (bounded live-preview window): null unless a launched instance is still within its idle
  // window (client-runtime-observability.live-preview-idle-minutes, default 15) - null most of the time
  // by design, appears only right after a real successful launch, never a stale/dead link.
  liveUrl: string | null;
};

// GET /api/projects/{id}/coherence-graph (2026-08-10) - Кузница/Delivery room, Thagard ECHO +
// Gärdenfors AGM evidence graph, previously only reachable via the localhost-only gemini-observer API.
export type CoherenceGraphNode = {
  id: string;
  polarity: 'NEGATIVE_FINDING' | 'POSITIVE_CONFIRMATION' | 'NEUTRAL_OBSERVATION';
  sourceType: string;
  summaryText: string;
  featureId?: string;
  prNumber?: number;
  createdAt: string;
  accepted: boolean;
  confidence?: number;
};

// GET /api/projects/{id}/observer-journal (2026-08-10) - Кузница/Delivery room, real cycles only
// (geminiCalled=true entries, same filter Gemini's own continuity logic uses).
export type GeminiObserverJournalEntry = {
  id: string;
  projectId: string;
  createdAt: string;
  entry: string;
  findingsCount: number;
  readinessRatio: number;
  geminiCalled: boolean;
};

export type CoherenceGraphSnapshot = {
  projectId: string;
  nodes: CoherenceGraphNode[];
  hasCoherenceRun: boolean;
  lastRunAt?: string;
  coherenceScore: number;
  totalNodes: number;
  acceptedNodes: number;
};

