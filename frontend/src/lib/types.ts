export type AgentTaskStatus = 'TODO' | 'CLAIMED' | 'IN_PROGRESS' | 'REVIEW' | 'DONE' | 'BLOCKED';

export type AgentAccount = {
  id: string;
  accountCode: string;
  displayName: string;
  focusTags: string[];
  status: string;
  createdAt: string;
  lastClaimedAt: string | null;
};

export type AgentTask = {
  id: string;
  requirementId: string;
  requirementTitle: string;
  description: string;
  agentTag: string;
  status: AgentTaskStatus;
  claimedByAccountCode: string | null;
  claimedByDisplayName: string | null;
  createdAt: string;
  claimedAt: string | null;
  updatedAt: string;
};

export type AgentSnapshot = {
  accounts: AgentAccount[];
  tasks: AgentTask[];
  summary: Record<string, number>;
};

export type LeanGreeting = {
  id: string;
  message: string;
  currentStatus: 'RECEIVED' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED';
  createdAt: string;
  leadTimeSeconds: number;
};
