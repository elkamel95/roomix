import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';

const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1';

// Callback appelé quand le serveur retourne 403 (token invalide)
// Enregistré par le composant racine pour déclencher un logout propre
let onForceLogout: (() => void) | null = null;
export function registerForceLogout(fn: () => void) { onForceLogout = fn; }

/**
 * Normalise une URL d'image retournée par le backend.
 *
 * Cas gérés :
 *  1. URL Supabase / S3 externe  → retournée telle quelle
 *  2. URL localhost:PORT           → remplacée par l'hôte Railway/prod
 *  3. URL IP LAN (192.168.x.x)    → remplacée par l'hôte Railway/prod
 *  4. URL /api/v1/storage/...     → chemin relatif → URL absolue Railway
 */
export function normalizeImageUrl(url: string | null | undefined): string | null {
  if (!url) return null;

  // Déjà une URL CDN externe (Supabase, S3, IKEA, Conforama…) → OK
  if (url.startsWith('https://') && !url.includes('localhost') && !url.match(/https?:\/\/\d+\.\d+/)) {
    return url;
  }

  const serverBase = API_URL.replace(/\/api\/v1\/?$/, '');

  // localhost:PORT → serveur prod
  let normalized = url.replace(/^https?:\/\/localhost(:\d+)?/, serverBase);

  // IP LAN 192.168.x.x:PORT → serveur prod
  normalized = normalized.replace(/^https?:\/\/\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?/, serverBase);

  // Chemin relatif → URL absolue
  if (normalized.startsWith('/')) {
    normalized = serverBase + normalized;
  }

  return normalized;
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
    const status = error.response?.status;

    // 401 → essai refresh
    if (status === 401 && !originalRequest._retry) {
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

    // 403 → token invalide — vider silencieusement et déconnecter
    if (status === 403) {
      await SecureStore.deleteItemAsync('access_token');
      await SecureStore.deleteItemAsync('refresh_token');
      onForceLogout?.();
      if (__DEV__) console.warn('[API] 403 — token invalide, déconnexion forcée');
      // Ne pas re-lancer l'erreur : la session sera redirigée vers login proprement
      return Promise.resolve({ data: null, status: 403, headers: {}, config: error.config! } as any);
    }

    return Promise.reject(error);
  }
);
