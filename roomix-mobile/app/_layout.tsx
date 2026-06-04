import '../global.css';
import { useEffect } from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as SecureStore from 'expo-secure-store';
import { useAuthStore } from '../src/store/slices/authStore';
import { api, registerForceLogout } from '../src/services/api';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
});

function RootLayoutNav() {
  const { setUser, logout } = useAuthStore();

  useEffect(() => {
    // Enregistrer le callback de déconnexion forcée (appelé sur 403)
    registerForceLogout(() => logout());

    const restoreSession = async () => {
      const token = await SecureStore.getItemAsync('access_token');
      if (!token) return;
      try {
        const { data } = await api.get('/users/me');
        if (data) setUser(data);   // data=null si 403 résolu silencieusement
      } catch {
        // Token expiré ou serveur indisponible → logout silencieux
        await logout();
      }
    };
    restoreSession().catch(() => {});  // jamais d'erreur non gérée
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
