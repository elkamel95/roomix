import { create } from 'zustand';
import { Project } from '../../types';

interface ProjectState {
  projects: Project[];
  selectedProject: Project | null;
  setProjects: (projects: Project[]) => void;
  addProject: (project: Project) => void;
  updateProject: (project: Project) => void;
  removeProject: (id: string) => void;
  selectProject: (project: Project | null) => void;
}

export const useProjectStore = create<ProjectState>((set) => ({
  projects: [],
  selectedProject: null,

  setProjects: (projects) => set({ projects }),

  addProject: (project) =>
    set((state) => ({ projects: [project, ...state.projects] })),

  updateProject: (updated) =>
    set((state) => ({
      projects: state.projects.map((p) => (p.id === updated.id ? updated : p)),
      selectedProject: state.selectedProject?.id === updated.id ? updated : state.selectedProject,
    })),

  removeProject: (id) =>
    set((state) => ({
      projects: state.projects.filter((p) => p.id !== id),
      selectedProject: state.selectedProject?.id === id ? null : state.selectedProject,
    })),

  selectProject: (project) => set({ selectedProject: project }),
}));
