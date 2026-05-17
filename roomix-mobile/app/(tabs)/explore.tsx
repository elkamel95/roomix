import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { STYLE_OPTIONS } from '../../src/types';

export default function ExploreScreen() {
  const router = useRouter();

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Styles de décoration</Text>
      <Text style={styles.subtitle}>Choisissez votre univers</Text>

      <View style={styles.grid}>
        {STYLE_OPTIONS.map((style) => (
          <TouchableOpacity
            key={style.key}
            style={[styles.card, { backgroundColor: style.color + '22' }]}
            onPress={() => router.push({ pathname: '/upload', params: { style: style.key } })}
          >
            <Text style={styles.emoji}>{style.emoji}</Text>
            <Text style={styles.label}>{style.label}</Text>
            <Text style={styles.desc}>{style.description}</Text>
            <Text style={styles.cta}>Essayer →</Text>
          </TouchableOpacity>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  content: { padding: 20, paddingTop: 60 },
  title: { fontSize: 26, fontWeight: '800', color: '#fff', marginBottom: 6 },
  subtitle: { fontSize: 14, color: '#888', marginBottom: 24 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  card: {
    width: '47%', borderRadius: 16, padding: 16,
    borderWidth: 1, borderColor: '#2a2a5e',
  },
  emoji: { fontSize: 36, marginBottom: 8 },
  label: { color: '#fff', fontWeight: '700', fontSize: 15, marginBottom: 4 },
  desc: { color: '#888', fontSize: 12, marginBottom: 10 },
  cta: { color: '#7C3AED', fontWeight: '700', fontSize: 13 },
});
