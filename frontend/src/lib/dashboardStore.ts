import { writable, get } from 'svelte/store';
import type { ProjectDashboard, ProjectSummary } from './types';
import { API_BASE } from './api';

export const projectsStore = writable<ProjectSummary[]>([]);
export const currentDashboardStore = writable<ProjectDashboard | null>(null);
export const dashboardLoadingStore = writable<boolean>(true);
export const dashboardErrorStore = writable<string | null>(null);

let activePollingInterval: ReturnType<typeof setInterval> | null = null;
let currentActiveProjectId: string | null = null;

export async function fetchProjects(): Promise<ProjectSummary[]> {
  try {
    const res = await fetch(`${API_BASE}/api/projects`);
    if (!res.ok) throw new Error(`Projects API returned ${res.status}`);
    const data: ProjectSummary[] = await res.json();
    projectsStore.set(data);
    return data;
  } catch (e: any) {
    dashboardErrorStore.set(e?.message || 'Failed to load projects');
    return [];
  }
}

export async function fetchDashboard(projectId: string): Promise<ProjectDashboard | null> {
  if (!projectId) return null;
  try {
    const res = await fetch(`${API_BASE}/api/projects/${projectId}/dashboard`);
    if (!res.ok) {
      dashboardErrorStore.set(`Project dashboard API returned ${res.status}`);
      return null;
    }
    const data: ProjectDashboard = await res.json();
    currentDashboardStore.set(data);
    dashboardErrorStore.set(null);
    return data;
  } catch (e: any) {
    dashboardErrorStore.set(e?.message || 'Failed to load project dashboard');
    return null;
  } finally {
    dashboardLoadingStore.set(false);
  }
}

export function startUnifiedDashboardPolling(projectId: string, intervalMs: number = 10000) {
  if (activePollingInterval && currentActiveProjectId === projectId) {
    return; // Already polling this exact project cleanly
  }

  stopUnifiedDashboardPolling();
  currentActiveProjectId = projectId;

  fetchDashboard(projectId);

  activePollingInterval = setInterval(() => {
    if (currentActiveProjectId) {
      fetchDashboard(currentActiveProjectId);
    }
  }, intervalMs);
}

export function stopUnifiedDashboardPolling() {
  if (activePollingInterval) {
    clearInterval(activePollingInterval);
    activePollingInterval = null;
  }
  currentActiveProjectId = null;
}
