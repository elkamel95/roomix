import * as SecureStore from 'expo-secure-store';
import { api } from './api';
import { AuthResponse } from '../types';

export const authService = {
  async register(email: string, password: string, firstName: string): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>('/auth/register', { email, password, firstName });
    await storeTokens(data);
    return data;
  },

  async login(email: string, password: string): Promise<AuthResponse> {
    const { data } = await api.post<AuthResponse>('/auth/login', { email, password });
    await storeTokens(data);
    return data;
  },

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } finally {
      await SecureStore.deleteItemAsync('access_token');
      await SecureStore.deleteItemAsync('refresh_token');
    }
  },

  async getStoredToken(): Promise<string | null> {
    return SecureStore.getItemAsync('access_token');
  },
};

async function storeTokens(auth: AuthResponse) {
  await SecureStore.setItemAsync('access_token', auth.accessToken);
  await SecureStore.setItemAsync('refresh_token', auth.refreshToken);
}
