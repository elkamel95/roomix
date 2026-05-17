import { api } from './api';
import { DecorationStyle, Generation, PageResponse, Product, Project, ProjectStatus } from '../types';

export const projectService = {
  async createProject(
    imageUri: string,
    style: DecorationStyle,
    name?: string,
    budget?: number
  ): Promise<Project> {
    const formData = new FormData();

    formData.append('image', {
      uri: imageUri,
      type: 'image/jpeg',
      name: 'photo.jpg',
    } as unknown as Blob);

    formData.append('data', JSON.stringify({ style, name, budget }));

    const { data } = await api.post<Project>('/projects', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    });

    return data;
  },

  async getProjects(page = 0, size = 10, status?: ProjectStatus): Promise<PageResponse<Project>> {
    const params: Record<string, unknown> = { page, size };
    if (status) params.status = status;
    const { data } = await api.get<PageResponse<Project>>('/projects', { params });
    return data;
  },

  async getProject(id: string): Promise<Project> {
    const { data } = await api.get<Project>(`/projects/${id}`);
    return data;
  },

  async getGenerationStatus(id: string): Promise<Generation> {
    const { data } = await api.get<Generation>(`/projects/${id}/status`);
    return data;
  },

  async getProducts(id: string): Promise<Product[]> {
    const { data } = await api.get<Product[]>(`/projects/${id}/products`);
    return data;
  },

  async renameProject(id: string, name: string): Promise<void> {
    await api.put(`/projects/${id}`, { name });
  },

  async deleteProject(id: string): Promise<void> {
    await api.delete(`/projects/${id}`);
  },
};
