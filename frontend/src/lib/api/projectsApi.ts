import type { Project } from '../types';

const USE_MOCK = import.meta.env.VITE_USE_MOCK_PROJECTS === 'true';
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

// Mock data helpers
const getMockProjects = (): Project[] => {
  const data = localStorage.getItem('mock_projects');
  return data ? JSON.parse(data) : [];
};

const saveMockProjects = (projects: Project[]) => {
  localStorage.setItem('mock_projects', JSON.stringify(projects));
};

export const projectsApi = {
  async createProject(name: string, onboardingMode?: string, initialWishlist?: string): Promise<Project> {
    if (USE_MOCK) {
      await sleep(400);
      const newProject: Project = {
        id: Math.random().toString(36).substr(2, 9),
        name,
        status: 'active',
        createdAt: new Date().toISOString(),
        accountsCount: 0,
        tasksQueued: 0,
        tasksInProgress: 0,
        tasksDone: 0,
      };
      const projects = getMockProjects();
      projects.push(newProject);
      saveMockProjects(projects);
      return newProject;
    }

    const res = await fetch(`${API_BASE}/api/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, onboardingMode, initialWishlist }),
    });
    if (!res.ok) {
      const err = await res.text();
      throw new Error(err || 'Failed to create project');
    }
    return res.json();
  },

  async getProjects(): Promise<Project[]> {
    if (USE_MOCK) {
      await sleep(300);
      return getMockProjects();
    }

    const res = await fetch(`${API_BASE}/api/projects`);
    if (!res.ok) throw new Error('Failed to fetch projects');
    return res.json();
  },

  async getProject(id: string): Promise<Project> {
    if (USE_MOCK) {
      await sleep(300);
      const projects = getMockProjects();
      const project = projects.find((p) => p.id === id);
      if (!project) throw new Error('Project not found');

      // Simulate background progress in mock
      if (project.status === 'active') {
        project.accountsCount = (project.accountsCount || 0) + (Math.random() > 0.8 ? 1 : 0);
        project.tasksQueued = (project.tasksQueued || 0) + Math.floor(Math.random() * 3);
        if (project.tasksQueued > 0 && Math.random() > 0.5) {
          project.tasksQueued--;
          project.tasksInProgress = (project.tasksInProgress || 0) + 1;
        }
        if ((project.tasksInProgress || 0) > 0 && Math.random() > 0.6) {
          project.tasksInProgress!--;
          project.tasksDone = (project.tasksDone || 0) + 1;
        }
        saveMockProjects(projects);
      }

      return project;
    }

    const res = await fetch(`${API_BASE}/api/projects/${id}`);
    if (!res.ok) throw new Error('Failed to fetch project');
    return res.json();
  },

  async acceptProject(id: string): Promise<Project> {
    if (USE_MOCK) {
      await sleep(500);
      const projects = getMockProjects();
      const index = projects.findIndex((p) => p.id === id);
      if (index === -1) throw new Error('Project not found');

      projects[index] = {
        ...projects[index],
        status: 'accepted',
        acceptedAt: new Date().toISOString(),
      };
      saveMockProjects(projects);
      return projects[index];
    }

    const res = await fetch(`${API_BASE}/api/projects/${id}/accept`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error('Failed to accept project');
    return res.json();
  },
};
