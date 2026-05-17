import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, Image, TouchableOpacity,
  ActivityIndicator, ScrollView, Animated, Alert, Linking,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import * as Sharing from 'expo-sharing';
import { projectService } from '../../services/projectService';
import { Product } from '../../types';

export default function ResultScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const [sliderValue, setSliderValue] = useState(0.5);
  const sliderAnim = useRef(new Animated.Value(0.5)).current;

  const { data: project, refetch } = useQuery({
    queryKey: ['project', id],
    queryFn: () => projectService.getProject(id),
    refetchInterval: (data) =>
      data?.status === 'PROCESSING' || data?.status === 'PENDING' ? 3000 : false,
  });

  const { data: products } = useQuery({
    queryKey: ['products', id],
    queryFn: () => projectService.getProducts(id),
    enabled: project?.status === 'DONE',
  });

  const isLoading = project?.status === 'PENDING' || project?.status === 'PROCESSING';
  const isDone = project?.status === 'DONE';
  const isFailed = project?.status === 'FAILED';

  const handleShare = async () => {
    if (!project?.generation?.resultImageUrl) return;
    if (await Sharing.isAvailableAsync()) {
      await Sharing.shareAsync(project.generation.resultImageUrl);
    }
  };

  if (!project) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color="#7C3AED" size="large" />
      </View>
    );
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}>
        <Text style={styles.backText}>← Retour</Text>
      </TouchableOpacity>

      <Text style={styles.title}>{project.name}</Text>
      <Text style={styles.styleBadge}>{project.style.replace('_', ' ')}</Text>

      {/* Zone image */}
      {isLoading && (
        <View style={styles.loadingBox}>
          <ActivityIndicator color="#7C3AED" size="large" />
          <Text style={styles.loadingText}>L'IA transforme votre pièce...</Text>
          <Text style={styles.loadingSubtext}>Environ 30-45 secondes</Text>
        </View>
      )}

      {isFailed && (
        <View style={styles.errorBox}>
          <Text style={styles.errorEmoji}>❌</Text>
          <Text style={styles.errorText}>Génération échouée</Text>
          <TouchableOpacity style={styles.retryBtn} onPress={() => refetch()}>
            <Text style={styles.retryText}>Réessayer</Text>
          </TouchableOpacity>
        </View>
      )}

      {isDone && project.generation?.resultImageUrl && (
        <View>
          {/* Avant / Après slider */}
          <View style={styles.comparisonContainer}>
            <Image source={{ uri: project.originalImageUrl }} style={styles.comparisonImage} />
            <View style={[styles.afterOverlay, { width: `${sliderValue * 100}%` as any }]}>
              <Image
                source={{ uri: project.generation.resultImageUrl }}
                style={styles.comparisonImage}
              />
            </View>
            <View style={[styles.sliderHandle, { left: `${sliderValue * 100}%` as any }]}>
              <View style={styles.sliderLine} />
              <View style={styles.sliderCircle}>
                <Text style={styles.sliderArrows}>◀ ▶</Text>
              </View>
            </View>
          </View>

          <View style={styles.labels}>
            <Text style={styles.label}>Avant</Text>
            <Text style={styles.label}>Après</Text>
          </View>

          {/* Actions */}
          <View style={styles.actions}>
            <TouchableOpacity style={styles.actionBtn} onPress={handleShare}>
              <Text style={styles.actionBtnText}>📤 Partager</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.actionBtn, styles.actionBtnSecondary]}
              onPress={() => router.push(`/products/${id}`)}
            >
              <Text style={styles.actionBtnText}>🛒 Acheter les meubles</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* Products section */}
      {isDone && products && products.length > 0 && (
        <View style={styles.productsSection}>
          <Text style={styles.productsTitle}>🛍️ Produits utilisés</Text>
          {products.map((product: Product) => (
            <TouchableOpacity
              key={product.id}
              style={styles.productCard}
              onPress={() => product.affiliateUrl && Linking.openURL(product.affiliateUrl)}
            >
              <View style={styles.productInfo}>
                <Text style={styles.productName}>{product.name}</Text>
                <Text style={styles.productBrand}>{product.brand}</Text>
              </View>
              <View style={styles.productPriceBox}>
                <Text style={styles.productPrice}>
                  {product.price ? `${product.price} €` : 'Prix N/A'}
                </Text>
                <Text style={styles.productLink}>Voir →</Text>
              </View>
            </TouchableOpacity>
          ))}

          {products.length > 0 && (
            <View style={styles.totalBox}>
              <Text style={styles.totalLabel}>Total estimé</Text>
              <Text style={styles.totalPrice}>
                {products.reduce((sum, p) => sum + (p.price ?? 0), 0).toFixed(0)} €
              </Text>
            </View>
          )}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  content: { padding: 20, paddingTop: 56 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0f0f23' },
  backBtn: { marginBottom: 16 },
  backText: { color: '#7C3AED', fontSize: 16, fontWeight: '600' },
  title: { fontSize: 24, fontWeight: '800', color: '#fff', marginBottom: 6 },
  styleBadge: { color: '#7C3AED', fontWeight: '700', fontSize: 13, marginBottom: 20, textTransform: 'uppercase' },
  loadingBox: { backgroundColor: '#1a1a3e', borderRadius: 16, padding: 40, alignItems: 'center', gap: 12 },
  loadingText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  loadingSubtext: { color: '#888', fontSize: 13 },
  errorBox: { backgroundColor: '#2a1a1a', borderRadius: 16, padding: 32, alignItems: 'center', gap: 12 },
  errorEmoji: { fontSize: 48 },
  errorText: { color: '#ff6b6b', fontSize: 16, fontWeight: '700' },
  retryBtn: { backgroundColor: '#7C3AED', paddingHorizontal: 20, paddingVertical: 10, borderRadius: 10 },
  retryText: { color: '#fff', fontWeight: '700' },
  comparisonContainer: { width: '100%', height: 280, borderRadius: 16, overflow: 'hidden', position: 'relative' },
  comparisonImage: { width: '100%', height: '100%', position: 'absolute' },
  afterOverlay: { position: 'absolute', top: 0, left: 0, height: '100%', overflow: 'hidden' },
  sliderHandle: { position: 'absolute', top: 0, height: '100%', alignItems: 'center' },
  sliderLine: { width: 2, flex: 1, backgroundColor: '#fff' },
  sliderCircle: { position: 'absolute', top: '45%', backgroundColor: '#7C3AED', borderRadius: 20, padding: 8 },
  sliderArrows: { color: '#fff', fontSize: 12 },
  labels: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8, marginBottom: 20 },
  label: { color: '#888', fontSize: 12 },
  actions: { gap: 10, marginBottom: 24 },
  actionBtn: { backgroundColor: '#7C3AED', borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  actionBtnSecondary: { backgroundColor: '#1a1a3e', borderWidth: 1, borderColor: '#7C3AED' },
  actionBtnText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  productsSection: { marginTop: 8 },
  productsTitle: { fontSize: 18, fontWeight: '700', color: '#fff', marginBottom: 14 },
  productCard: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    backgroundColor: '#1a1a3e', borderRadius: 12, padding: 14, marginBottom: 8,
  },
  productInfo: { flex: 1 },
  productName: { color: '#fff', fontWeight: '600', fontSize: 14 },
  productBrand: { color: '#888', fontSize: 12, marginTop: 2 },
  productPriceBox: { alignItems: 'flex-end' },
  productPrice: { color: '#7C3AED', fontWeight: '800', fontSize: 16 },
  productLink: { color: '#888', fontSize: 12 },
  totalBox: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    backgroundColor: '#2d1b69', borderRadius: 12, padding: 16, marginTop: 4, marginBottom: 32,
  },
  totalLabel: { color: '#ccc', fontSize: 15, fontWeight: '600' },
  totalPrice: { color: '#fff', fontSize: 22, fontWeight: '800' },
});
