import '../global.css';
import { useEffect } from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { LogBox } from 'react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as SecureStore from 'expo-secure-store';
import { useAuthStore } from '../src/store/slices/authStore';
import { api, registerForceLogout } from '../src/services/api';

// Supprimer les erreurs 401/403 du LogBox — elles sont gérées par l'intercepteur
LogBox.ignoreLogs([
  'AxiosError: Request failed with status code 401',
  'AxiosError: Request failed with status code 403',
  'Request failed with status code 401',
  'Request failed with status code 403',
]);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
});

function RootLayoutNav() {
  const { setUser, logout } = useAuthStore();

  useEffect(() => {
    registerForceLogout(() => logout());

    const restoreSession = async () => {
      const token = await SecureStore.getItemAsync('access_token');
      if (!token) return;

      try {
        const response = await api.get('/users/me');
        // response.data = null si le token était invalide (403 géré par intercepteur)
        if (response?.data) setUser(response.data);
        else await logout();
      } catch (err: any) {
        // Toute autre erreur (réseau, timeout…) → logout silencieux
        if (__DEV__) console.warn('[Session] Restauration échouée:', err?.message ?? err);
        await logout();
      }
    };

    restoreSession().catch(() => {});
  }, []);

  return (
    <>
      <StatusBar style="light" />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="upload/index" options={{ presentation: 'modal' }} />
        <Stack.Screen name="project/[id]" />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <RootLayoutNav />
    </QueryClientProvider>
  );
}
