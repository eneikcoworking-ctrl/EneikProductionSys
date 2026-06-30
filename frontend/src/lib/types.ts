export type LeanGreeting = {
  id: string;
  message: string;
  currentStatus: 'RECEIVED' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED';
  createdAt: string;
  leadTimeSeconds: number;
};
