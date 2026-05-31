import { api } from './api';
import {
  ColorPalette, DecorationStyle, Generation, PageResponse,
  Product, Project, ProjectStatus, RoomType,
} from '../types';

export interface ObjectRef {
  id: string;
  title: string;
  imageUri: string;
}

export type PromptMode = 'CREATIVE' | 'PRO' | 'CHAIN';

// ── Paramètres de rendu gpt-image-2 (ChatGPT uniquement) ─────────────────────
export type ImageSize =
  | 'auto'
  | '1024x1024'
  | '1536x1024'
  | '1024x1536'
  | '2048x2048'
  | '2048x1152'
  | '3840x2160'
  | '2160x3840';

export type ImageQuality    = 'auto' | 'low' | 'medium' | 'high';
export type ImageFormat     = 'jpeg' | 'png' | 'webp';
export type ImageBackground = 'auto' | 'opaque';

export interface CreateProjectParams {
  imageUri: string;
  style: DecorationStyle;
  aiModel?: 'QWEN' | 'FLUX' | 'CHATGPT' | 'SDXL';
  promptMode?: PromptMode;
  name?: string;
  roomType?: RoomType;
  colorPalette?: ColorPalette;
  customNote?: string;
  objectRefs?: ObjectRef[];
  // Paramètres de rendu (gpt-image-2 / ChatGPT uniquement)
  imageSize?:        ImageSize;
  imageQuality?:     ImageQuality;
  imageFormat?:      ImageFormat;
  imageCompression?: number;       // 0-100, pour jpeg/webp uniquement
  imageBackground?:  ImageBackground;
}

export const projectService = {
  async createProject(params: CreateProjectParams): Promise<Project> {
    const {
      imageUri, style, aiModel = 'QWEN', promptMode = 'CREATIVE',
      name, roomType, colorPalette, customNote, objectRefs,
      imageSize = 'auto', imageQuality = 'auto', imageFormat = 'jpeg',
      imageCompression = 85, imageBackground = 'auto',
    } = params;

    const formData = new FormData();
    formData.append('image', { uri: imageUri, type: 'image/jpeg', name: 'photo.jpg' } as unknown as Blob);
    formData.append('style',            style);
    formData.append('aiModel',          aiModel);
    formData.append('promptMode',       promptMode);
    formData.append('imageSize',        imageSize);
    formData.append('imageQuality',     imageQuality);
    formData.append('imageFormat',      imageFormat);
    formData.append('imageCompression', String(imageCompression));
    formData.append('imageBackground',  imageBackground);
    if (roomType)     formData.append('roomType',     roomType);
    if (name)         formData.append('name',         name);
    if (colorPalette) formData.append('colorPalette', colorPalette);
    if (customNote)   formData.append('customNote',   customNote);

    // Objets de référence (max 3 photos + titres)
    if (objectRefs && objectRefs.length > 0) {
      objectRefs.slice(0, 3).forEach((ref, i) => {
        formData.append('objectImages', {
          uri: ref.imageUri, type: 'image/jpeg', name: `object_${i}.jpg`,
        } as unknown as Blob);
        formData.append('objectTitles', ref.title || `Objet ${i + 1}`);
      });
    }

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
