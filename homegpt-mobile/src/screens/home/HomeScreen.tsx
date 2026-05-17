import React, { useCallback, useEffect } from 'react';
import {
  View, Text, FlatList, TouchableOpacity,
  StyleSheet, RefreshControl, ActivityIndicator,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { projectService } from '../../services/projectService';
import { useAuthStore } from '../../store/slices/authStore';
import ProjectCard from '../../components/generation/ProjectCard';
import { Project } from '../../types';

export default function HomeScreen() {
  const router = useRouter();
  const { user } = useAuthStore();

  const { data, isLoading, refetch, isRefetching } = useQuery({
    queryKey: ['projects'],
    queryFn: () => projectService.getProjects(0, 20),
  });

  const projects = data?.content ?? [];

  const handleProjectPress = useCallback((project: Project) => {
    router.push(`/project/${project.id}`);
  }, [router]);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>Bonjour, {user?.firstName} 👋</Text>
          <Text style={styles.subtitle}>Transformez votre intérieur par IA</Text>
        </View>
        <TouchableOpacity
          style={styles.newButton}
          onPress={() => router.push('/upload')}
        >
          <Text style={styles.newButtonText}>+ Nouveau</Text>
        </TouchableOpacity>
      </View>

      {isLoading ? (
        <View style={styles.center}>
          <ActivityIndicator color="#7C3AED" size="large" />
        </View>
      ) : projects.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyEmoji}>🏠</Text>
          <Text style={styles.emptyTitle}>Aucun projet</Text>
          <Text style={styles.emptyText}>
            Prenez une photo de votre pièce et laissez l'IA la transformer
          </Text>
          <TouchableOpacity style={styles.startButton} onPress={() => router.push('/upload')}>
            <Text style={styles.startButtonText}>Commencer maintenant</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={projects}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <ProjectCard project={item} onPress={() => handleProjectPress(item)} />
          )}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={isRefetching} onRefresh={refetch} tintColor="#7C3AED" />
          }
          showsVerticalScrollIndicator={false}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  header: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 20, paddingTop: 60, paddingBottom: 20,
  },
  greeting: { fontSize: 22, fontWeight: '800', color: '#fff' },
  subtitle: { fontSize: 13, color: '#888', marginTop: 2 },
  newButton: { backgroundColor: '#7C3AED', paddingHorizontal: 16, paddingVertical: 10, borderRadius: 20 },
  newButtonText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  empty: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 32 },
  emptyEmoji: { fontSize: 72, marginBottom: 16 },
  emptyTitle: { fontSize: 22, fontWeight: '700', color: '#fff', marginBottom: 8 },
  emptyText: { fontSize: 14, color: '#888', textAlign: 'center', lineHeight: 20 },
  startButton: { marginTop: 24, backgroundColor: '#7C3AED', paddingHorizontal: 24, paddingVertical: 14, borderRadius: 12 },
  startButtonText: { color: '#fff', fontWeight: '700', fontSize: 16 },
  list: { paddingHorizontal: 20, paddingBottom: 20 },
});
