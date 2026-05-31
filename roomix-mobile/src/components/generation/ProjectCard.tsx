import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Image } from 'react-native';
import { Project, STYLE_OPTIONS } from '../../types';
import { normalizeImageUrl } from '../../services/api';

interface Props {
  project: Project;
  onPress: () => void;
}

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#F59E0B',
  PROCESSING: '#3B82F6',
  DONE: '#10B981',
  FAILED: '#EF4444',
};

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'En attente...',
  PROCESSING: 'Génération...',
  DONE: 'Terminé',
  FAILED: 'Échec',
};

export default function ProjectCard({ project, onPress }: Props) {
  const style = STYLE_OPTIONS.find((s) => s.key === project.style);
  const statusColor = STATUS_COLOR[project.status] ?? '#888';
  const statusLabel = STATUS_LABEL[project.status] ?? project.status;

  return (
    <TouchableOpacity style={styles.card} onPress={onPress} activeOpacity={0.8}>
      <View style={styles.imageContainer}>
        {project.generation?.resultImageUrl ? (
          <Image source={{ uri: normalizeImageUrl(project.generation.resultImageUrl)! }} style={styles.image} />
        ) : (
          <Image source={{ uri: normalizeImageUrl(project.originalImageUrl)! }} style={[styles.image, styles.imageOriginal]} />
        )}
        <View style={[styles.statusBadge, { backgroundColor: statusColor + '22', borderColor: statusColor }]}>
          <View style={[styles.statusDot, { backgroundColor: statusColor }]} />
          <Text style={[styles.statusText, { color: statusColor }]}>{statusLabel}</Text>
        </View>
      </View>

      <View style={styles.info}>
        <Text style={styles.name} numberOfLines={1}>{project.name}</Text>
        <View style={styles.styleBadge}>
          <Text style={styles.styleEmoji}>{style?.emoji ?? '🏠'}</Text>
          <Text style={styles.styleLabel}>{style?.label ?? project.style}</Text>
        </View>
        <Text style={styles.date}>
          {new Date(project.createdAt).toLocaleDateString('fr-FR', {
            day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
          })}
        </Text>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#1a1a3e', borderRadius: 16, marginBottom: 14, overflow: 'hidden' },
  imageContainer: { position: 'relative', height: 180 },
  image: { width: '100%', height: '100%' },
  imageOriginal: { opacity: 0.8 },
  statusBadge: {
    position: 'absolute', top: 10, right: 10, flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 10, paddingVertical: 4, borderRadius: 20, borderWidth: 1, gap: 6,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusText: { fontSize: 12, fontWeight: '700' },
  info: { padding: 14 },
  name: { fontSize: 16, fontWeight: '700', color: '#fff', marginBottom: 6 },
  styleBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 6 },
  styleEmoji: { fontSize: 16 },
  styleLabel: { color: '#7C3AED', fontWeight: '600', fontSize: 13 },
  date: { color: '#555', fontSize: 12 },
});
