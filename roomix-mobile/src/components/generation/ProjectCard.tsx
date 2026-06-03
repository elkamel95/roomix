import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Image } from 'react-native';
import { Project, STYLE_OPTIONS } from '../../types';
import { normalizeImageUrl } from '../../services/api';

interface Props {
  project: Project;
  onPress: () => void;
}

const STATUS_COLOR: Record<string, string> = {
  PENDING:    '#F59E0B',
  PROCESSING: '#3B82F6',
  DONE:       '#10B981',
  FAILED:     '#EF4444',
};

const STATUS_LABEL: Record<string, string> = {
  PENDING:    'En attente…',
  PROCESSING: 'Génération…',
  DONE:       'Terminé',
  FAILED:     'Échec',
};

export default function ProjectCard({ project, onPress }: Props) {
  const style       = STYLE_OPTIONS.find((s) => s.key === project.style);
  const statusColor = STATUS_COLOR[project.status] ?? '#888';
  const statusLabel = STATUS_LABEL[project.status] ?? project.status;

  const resultUrl   = normalizeImageUrl(project.generation?.resultImageUrl);
  const originalUrl = normalizeImageUrl(project.originalImageUrl);
  const imageUrl    = resultUrl ?? originalUrl;

  const [imgError, setImgError] = useState(false);

  return (
    <TouchableOpacity style={styles.card} onPress={onPress} activeOpacity={0.8}>
      <View style={styles.imageContainer}>

        {imageUrl && !imgError ? (
          <Image
            source={{ uri: imageUrl }}
            style={[styles.image, !resultUrl && styles.imageOriginal]}
            onError={() => setImgError(true)}
          />
        ) : (
          /* Fallback quand l'image ne charge pas */
          <View style={styles.imageFallback}>
            <Text style={styles.fallbackEmoji}>{style?.emoji ?? '🏠'}</Text>
            <Text style={styles.fallbackLabel}>{style?.label ?? 'Décoration'}</Text>
          </View>
        )}

        {/* Indicateur avant/après */}
        {resultUrl && !imgError && (
          <View style={styles.afterBadge}>
            <Text style={styles.afterBadgeText}>✨ Après</Text>
          </View>
        )}

        {/* Statut */}
        <View style={[styles.statusBadge, { backgroundColor: statusColor + '22', borderColor: statusColor }]}>
          <View style={[styles.statusDot, { backgroundColor: statusColor }]} />
          <Text style={[styles.statusText, { color: statusColor }]}>{statusLabel}</Text>
        </View>

      </View>

      <View style={styles.info}>
        <Text style={styles.name} numberOfLines={1}>{project.name}</Text>
        <View style={styles.styleBadge}>
          <Text style={styles.styleEmoji}>{style?.emoji ?? '🏠'}</Text>
          <Text style={styles.styleLabel}>{style?.label ?? project.style.replace(/_/g, ' ')}</Text>
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
  image:          { width: '100%', height: '100%', resizeMode: 'cover' },
  imageOriginal:  { opacity: 0.85 },

  imageFallback: {
    width: '100%', height: '100%',
    backgroundColor: '#12122e',
    alignItems: 'center', justifyContent: 'center', gap: 8,
  },
  fallbackEmoji: { fontSize: 48 },
  fallbackLabel: { color: '#555', fontSize: 13, fontWeight: '600' },

  afterBadge: {
    position: 'absolute', top: 10, left: 10,
    backgroundColor: 'rgba(124,58,237,0.85)', borderRadius: 10,
    paddingHorizontal: 8, paddingVertical: 3,
  },
  afterBadgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },

  statusBadge: {
    position: 'absolute', top: 10, right: 10,
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 10, paddingVertical: 4,
    borderRadius: 20, borderWidth: 1, gap: 6,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusText: { fontSize: 12, fontWeight: '700' },

  info:       { padding: 14 },
  name:       { fontSize: 16, fontWeight: '700', color: '#fff', marginBottom: 6 },
  styleBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 6 },
  styleEmoji: { fontSize: 16 },
  styleLabel: { color: '#7C3AED', fontWeight: '600', fontSize: 13 },
  date:       { color: '#555', fontSize: 12 },
});
