import { create } from 'zustand';
import { Project } from '../../types';

interface ProjectState {
  projects: Project[];
  selectedProject: Project | null;
  /** URI locale de l'image originale par project.id (contourne localhost en dev) */
  localImageUris: Record<string, string>;
  setProjects: (projects: Project[]) => void;
  addProject: (project: Project, localImageUri?: string) => void;
  updateProject: (project: Project) => void;
  removeProject: (id: string) => void;
  selectProject: (project: Project | null) => void;
  getLocalImageUri: (projectId: string) => string | undefined;
}

export const useProjectStore = create<ProjectState>((set, get) => ({
  projects: [],
  selectedProject: null,
  localImageUris: {},

  setProjects: (projects) => set({ projects }),

  addProject: (project, localImageUri) =>
    set((state) => ({
      projects: [project, ...state.projects],
      localImageUris: localImageUri
        ? { ...state.localImageUris, [project.id]: localImageUri }
        : state.localImageUris,
    })),

  updateProject: (updated) =>
    set((state) => ({
      projects: state.projects.map((p) => (p.id === updated.id ? updated : p)),
      selectedProject:
        state.selectedProject?.id === updated.id ? updated : state.selectedProject,
    })),

  removeProject: (id) =>
    set((state) => {
      const { [id]: _, ...remainingUris } = state.localImageUris;
      return {
        projects: state.projects.filter((p) => p.id !== id),
        selectedProject: state.selectedProject?.id === id ? null : state.selectedProject,
        localImageUris: remainingUris,
      };
    }),

  selectProject: (project) => set({ selectedProject: project }),

  getLocalImageUri: (projectId) => get().localImageUris[projectId],
}));
