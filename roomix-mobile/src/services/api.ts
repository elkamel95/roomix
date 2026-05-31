import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';

const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1';

/**
 * Normalise une URL d'image retournée par le backend.
 * En mode dev local, le backend génère des URLs avec "localhost" qui sont
 * inaccessibles depuis un appareil mobile — on remplace l'origine localhost
 * par l'hôte du backend configuré dans EXPO_PUBLIC_API_URL.
 */
export function normalizeImageUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  // Extraire la base du serveur (ex: http://192.168.0.11:8080) depuis l'API URL
  const serverBase = API_URL.replace(/\/api\/v1\/?$/, '');
  // Remplacer http://localhost:PORT par la vraie base du serveur
  return url.replace(/^https?:\/\/localhost:\d+/, serverBase);
}

export const api = axios.create({
  baseURL: API_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await SecureStore.getItemAsync('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const refreshToken = await SecureStore.getItemAsync('refresh_token');
        if (!refreshToken) throw new Error('No refresh token');

        const { data } = await axios.post(`${API_URL}/auth/refresh`, { refreshToken });
        await SecureStore.setItemAsync('access_token', data.accessToken);
        await SecureStore.setItemAsync('refresh_token', data.refreshToken);

        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return api(originalRequest);
      } catch {
        await SecureStore.deleteItemAsync('access_token');
        await SecureStore.deleteItemAsync('refresh_token');
      }
    }

    return Promise.reject(error);
  }
);
