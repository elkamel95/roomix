import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, Image, TouchableOpacity,
  ActivityIndicator, ScrollView, Alert, Linking,
  PanResponder, Modal, StatusBar, useWindowDimensions, Animated,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import * as Sharing from 'expo-sharing';
import * as FileSystem from 'expo-file-system/legacy';
import * as MediaLibrary from 'expo-media-library';
import { projectService } from '../../services/projectService';
import { normalizeImageUrl } from '../../services/api';
import { useProjectStore } from '../../store/slices/projectStore';
import { useAuthStore }   from '../../store/slices/authStore';
import { Product } from '../../types';

// ── Constantes ─────────────────────────────────────────────────────────────────

const COMPARISON_HEIGHT = 320;
const CONTENT_PADDING   = 40;

const LOADING_STEPS = [
  { key: 'analyze', label: 'Analyse de la pièce',    emoji: '🔍' },
  { key: 'style',   label: 'Application du style',   emoji: '🎨' },
  { key: 'render',  label: 'Rendu photorealistic',    emoji: '✨' },
  { key: 'finish',  label: 'Finalisation',            emoji: '🏁' },
];

const LOADING_TIPS = [
  '💡 Un bon éclairage naturel améliore les résultats de 30%',
  '🛋️ Photographiez la pièce depuis un angle légèrement en hauteur',
  '🎨 Les styles Japandi et Scandinavian donnent les meilleurs résultats',
  '📐 Essayez différents styles sur la même photo pour comparer',
  '🌿 Ajouter des plantes rend toujours une pièce plus vivante',
  '💰 Le budget Premium donne des suggestions Roche Bobois et B&B Italia',
];

// ── Config marques ─────────────────────────────────────────────────────────────

const BRAND_CONFIG: Record<string, { label: string; color: string; bg: string; logo: string }> = {
  IKEA:        { label: 'IKEA',        color: '#FFB800', bg: '#2a2200', logo: '🟡' },
  CONFORAMA:   { label: 'Conforama',   color: '#E53935', bg: '#2a0a0a', logo: '🔴' },
  AMAZON:      { label: 'Amazon',      color: '#FF9900', bg: '#2a1a00', logo: '📦' },
  LEROY_MERLIN:{ label: 'Leroy Merlin',color: '#4CAF50', bg: '#0a1a0a', logo: '🟢' },
  ACTION:      { label: 'Action',      color: '#2196F3', bg: '#0a1020', logo: '🔵' },
  OTHER:       { label: 'Autre',       color: '#9E9E9E', bg: '#1a1a1a', logo: '🏪' },
};

const CATEGORY_EMOJI: Record<string, string> = {
  SOFA: '🛋️', TABLE: '🪵', CHAIR: '🪑', LAMP: '💡', CARPET: '🟫',
  PLANT: '🌿', DESK: '🖥️', CURTAIN: '🪟', SHELF: '📚', BED: '🛏️',
  DECORATION: '🖼️', STORAGE: '🗄️', OTHER: '📦',
};

// ── Composant ProductCard ──────────────────────────────────────────────────────
//
//  Layout pleine largeur, horizontal (style e-commerce) :
//  ┌───────────────────────────────────────────────────┐
//  │ ┌──────────┐  KIVIK Canapé d'angle 4 places       │
//  │ │          │  🟡 IKEA                             │
//  │ │  IMAGE   │                                      │
//  │ │  RÉELLE  │  Bleu foncé                          │
//  │ │          │                                      │
//  │ └──────────┘  1 499,00 €                          │
//  │               [ Acheter sur IKEA  →  ]            │
//  └───────────────────────────────────────────────────┘

function ProductCard({ product }: { product: Product }) {
  const brand    = BRAND_CONFIG[product.brand] ?? BRAND_CONFIG.OTHER;
  const catEmoji = CATEGORY_EMOJI[product.category] ?? '📦';
  const url      = product.productUrl;
  const color    = product.color ?? null;
  const price    = product.price != null
    ? `${Number(product.price).toLocaleString('fr-FR', { minimumFractionDigits: 2 })} €`
    : null;

  const [imgError, setImgError] = useState(false);
  const hasImage = !!product.imageUrl && !imgError;

  return (
    <View style={ps.card}>

      {/* ── Image à gauche ── */}
      <View style={ps.imgCol}>
        {hasImage ? (
          <Image
            source={{
              uri: product.imageUrl!,
              headers: { 'User-Agent': 'Mozilla/5.0', 'Accept': 'image/*' },
              cache: 'force-cache',
            }}
            style={ps.img}
            resizeMode="cover"
            onError={() => setImgError(true)}
          />
        ) : (
          <View style={[ps.imgFallback, { backgroundColor: brand.bg }]}>
            <Text style={ps.imgFallbackEmoji}>{catEmoji}</Text>
          </View>
        )}
      </View>

      {/* ── Infos à droite ── */}
      <View style={ps.infoCol}>

        {/* Nom */}
        <Text style={ps.name} numberOfLines={2}>{product.name}</Text>

        {/* Badge marque */}
        <View style={[ps.brandPill, { backgroundColor: brand.bg, borderColor: brand.color }]}>
          <Text style={[ps.brandPillText, { color: brand.color }]}>
            {brand.logo} {brand.label}
          </Text>
        </View>

        {/* Couleur / catégorie */}
        {color
          ? <Text style={ps.color}>{color}</Text>
          : <Text style={ps.cat}>{catEmoji} {product.category.replace(/_/g, ' ')}</Text>
        }

        {/* Prix */}
        <Text style={ps.price}>{price ?? 'Prix N/A'}</Text>

        {/* Bouton CTA */}
        {url ? (
          <TouchableOpacity
            style={[ps.buyBtn, { borderColor: brand.color }]}
            onPress={() => Linking.openURL(url).catch(() => {})}
            activeOpacity={0.75}
          >
            <Text style={[ps.buyBtnText, { color: brand.color }]}>
              Acheter sur {brand.label}
            </Text>
            <Text style={[ps.buyBtnArrow, { color: brand.color }]}>→</Text>
          </TouchableOpacity>
        ) : (
          <View style={ps.buyBtnOff}>
            <Text style={ps.buyBtnOffText}>Lien indisponible</Text>
          </View>
        )}
      </View>

    </View>
  );
}

// ── Composant ProductsSection ──────────────────────────────────────────────────

function ProductsSection({ products }: { products: Product[] }) {
  const total = products.reduce((sum, p) => sum + (Number(p.price) || 0), 0);

  return (
    <View style={ps.section}>

      <View style={ps.sectionHeader}>
        <Text style={ps.sectionTitle}>🛍️ Produits suggérés</Text>
        <View style={ps.countPill}>
          <Text style={ps.countPillText}>
            {products.length} article{products.length > 1 ? 's' : ''}
          </Text>
        </View>
      </View>

      {/* Liste pleine largeur */}
      {products.map(p => <ProductCard key={p.id} product={p} />)}

      {/* Budget total */}
      {total > 0 && (
        <View style={ps.totalBox}>
          <View>
            <Text style={ps.totalLabel}>💰 Budget total estimé</Text>
            <Text style={ps.totalSub}>Tous les produits listés</Text>
          </View>
          <Text style={ps.totalPrice}>
            {total.toLocaleString('fr-FR', { maximumFractionDigits: 0 })} €
          </Text>
        </View>
      )}

    </View>
  );
}

// ── Composant LoadingScreen ────────────────────────────────────────────────────

function LoadingScreen({ status }: { status: 'PENDING' | 'PROCESSING' }) {
  const [tipIndex,  setTipIndex]  = useState(0);
  const [stepIndex, setStepIndex] = useState(0);
  const dotAnim = useRef(new Animated.Value(0)).current;

  // Animation pulsation du cercle principal
  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(dotAnim, { toValue: 1, duration: 900, useNativeDriver: true }),
        Animated.timing(dotAnim, { toValue: 0, duration: 900, useNativeDriver: true }),
      ])
    ).start();
  }, []);

  // Rotation des tips toutes les 4s
  useEffect(() => {
    const t = setInterval(() => {
      setTipIndex(i => (i + 1) % LOADING_TIPS.length);
    }, 4000);
    return () => clearInterval(t);
  }, []);

  // Avancement des étapes
  useEffect(() => {
    if (status === 'PENDING') { setStepIndex(0); return; }
    setStepIndex(1);
    const intervals = [
      setTimeout(() => setStepIndex(2), 8000),
      setTimeout(() => setStepIndex(3), 30000),
    ];
    return () => intervals.forEach(clearTimeout);
  }, [status]);

  const pulseScale = dotAnim.interpolate({ inputRange: [0, 1], outputRange: [1, 1.12] });

  return (
    <View style={ls.wrapper}>
      {/* Cercle animé */}
      <Animated.View style={[ls.circle, { transform: [{ scale: pulseScale }] }]}>
        <LinearGradient colors={['#9B5DEA', '#6D28D9']} style={ls.circleGradient}>
          <Text style={ls.circleEmoji}>✨</Text>
        </LinearGradient>
      </Animated.View>

      <Text style={ls.title}>L'IA transforme votre pièce</Text>
      <Text style={ls.sub}>
        {status === 'PENDING' ? 'En attente de traitement…' : 'Génération en cours (1–3 min)'}
      </Text>

      {/* Étapes */}
      <View style={ls.steps}>
        {LOADING_STEPS.map((step, i) => {
          const done    = i < stepIndex;
          const current = i === stepIndex;
          return (
            <View key={step.key} style={ls.stepRow}>
              <View style={[ls.stepDot, done && ls.stepDotDone, current && ls.stepDotActive]}>
                {done
                  ? <Text style={ls.stepCheck}>✓</Text>
                  : current
                    ? <ActivityIndicator size="small" color="#fff" />
                    : <View style={ls.stepDotInner} />
                }
              </View>
              <Text style={[ls.stepLabel, done && ls.stepLabelDone, current && ls.stepLabelActive]}>
                {step.emoji} {step.label}
              </Text>
            </View>
          );
        })}
      </View>

      {/* Tip rotatif */}
      <View style={ls.tipBox}>
        <Text style={ls.tipText}>{LOADING_TIPS[tipIndex]}</Text>
      </View>

      <Text style={ls.hint}>L'écran se met à jour automatiquement</Text>
    </View>
  );
}

// ── Screen principal ───────────────────────────────────────────────────────────

export default function ResultScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router  = useRouter();
  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
  const getLocalImageUri = useProjectStore((s) => s.getLocalImageUri);
  const { refreshUser }  = useAuthStore();

  const [containerWidth, setContainerWidth] = useState(windowWidth - CONTENT_PADDING);
  const [sliderValue,    setSliderValue]    = useState(0.5);
  const [isSaving,       setIsSaving]       = useState(false);
  const [isSharing,      setIsSharing]      = useState(false);
  const [fullscreenUri,  setFullscreenUri]  = useState<string | null>(null);
  const [afterImgError,  setAfterImgError]  = useState(false);
  const [beforeImgError, setBeforeImgError] = useState(false);

  const startSliderRef   = useRef(0.5);
  const containerWRef    = useRef(windowWidth - CONTENT_PADDING);

  // ── Slider PanResponder ───────────────────────────────────────────────────
  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder:  () => true,
      onPanResponderGrant: () => {
        setSliderValue(v => { startSliderRef.current = v; return v; });
      },
      onPanResponderMove: (_, { dx }) => {
        const w = containerWRef.current;
        if (w <= 0) return;
        setSliderValue(Math.min(1, Math.max(0, startSliderRef.current + dx / w)));
      },
    })
  ).current;

  // ── Data ──────────────────────────────────────────────────────────────────
  const { data: project } = useQuery({
    queryKey: ['project', id],
    queryFn:  () => projectService.getProject(id),
    refetchInterval: (query) => {
      const s = (query.state.data as any)?.status;
      return s === 'PROCESSING' || s === 'PENDING' ? 3000 : false;
    },
  });

  const { data: products } = useQuery({
    queryKey: ['products', id],
    queryFn:  () => projectService.getProducts(id),
    enabled:  project?.status === 'DONE',
  });

  const isLoading = project?.status === 'PENDING' || project?.status === 'PROCESSING';
  const isDone    = project?.status === 'DONE';
  const isFailed  = project?.status === 'FAILED';

  // Rafraîchir le solde de tokens quand la génération est terminée
  useEffect(() => {
    if (isDone) { refreshUser(); }
  }, [isDone]);

  const beforeUri = (project ? getLocalImageUri(project.id) : undefined)
    ?? normalizeImageUrl(project?.originalImageUrl);

  // ── Actions ───────────────────────────────────────────────────────────────

  const handleSave = async () => {
    const imageUrl = normalizeImageUrl(project?.generation?.resultImageUrl);
    if (!imageUrl) return;

    const { status } = await MediaLibrary.requestPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission refusée', "Autorisez l'accès à la galerie dans les paramètres.");
      return;
    }

    setIsSaving(true);
    try {
      const filename = 'roomix_' + Date.now() + '.jpg';
      const localUri = FileSystem.cacheDirectory + filename;
      const { uri } = await FileSystem.downloadAsync(imageUrl, localUri);
      await MediaLibrary.saveToLibraryAsync(uri);
      Alert.alert('✅ Sauvegardé !', 'La décoration a été ajoutée à votre galerie.');
    } catch (err: any) {
      Alert.alert('Erreur', err?.message ?? 'Impossible de sauvegarder.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleShare = async () => {
    const imageUrl = normalizeImageUrl(project?.generation?.resultImageUrl);
    if (!imageUrl) return;

    const available = await Sharing.isAvailableAsync();
    if (!available) {
      Alert.alert('Partage indisponible', "Le partage n'est pas supporté sur cet appareil.");
      return;
    }

    setIsSharing(true);
    try {
      const filename = 'roomix_' + Date.now() + '.jpg';
      const localUri = FileSystem.cacheDirectory + filename;
      const { uri } = await FileSystem.downloadAsync(imageUrl, localUri);
      await Sharing.shareAsync(uri, {
        mimeType: 'image/jpeg',
        dialogTitle: '✨ Ma déco Roomix',
      });
    } catch (err: any) {
      Alert.alert('Erreur', err?.message ?? 'Impossible de partager.');
    } finally {
      setIsSharing(false);
    }
  };

  const handleRegenerate = () => {
    Alert.alert(
      '🔄 Régénérer',
      'Relancer la génération avec les mêmes paramètres ?',
      [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Régénérer', onPress: () => router.back() },
      ]
    );
  };

  // ── Render ────────────────────────────────────────────────────────────────

  if (!project) {
    return (
      <View style={s.center}>
        <ActivityIndicator color="#7C3AED" size="large" />
      </View>
    );
  }

  return (
    <ScrollView
      style={s.container}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
    >
      {/* Header */}
      <View style={s.header}>
        <TouchableOpacity style={s.backBtn} onPress={() => router.back()}>
          <Text style={s.backText}>←</Text>
        </TouchableOpacity>
        <View style={s.headerCenter}>
          <Text style={s.title} numberOfLines={1}>{project.name}</Text>
          <Text style={s.styleBadge}>{project.style.replace(/_/g, ' ')}</Text>
        </View>
        {isDone && (
          <TouchableOpacity style={s.regenBtn} onPress={handleRegenerate}>
            <Text style={s.regenText}>🔄</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* ── Chargement ───────────────────────────────── */}
      {isLoading && (
        <LoadingScreen status={project.status as 'PENDING' | 'PROCESSING'} />
      )}

      {/* ── Erreur ───────────────────────────────────── */}
      {isFailed && (
        <View style={s.errorBox}>
          <Text style={s.errorEmoji}>❌</Text>
          <Text style={s.errorTitle}>Génération échouée</Text>
          {project.generation?.errorMessage && (
            <Text style={s.errorDetail}>{project.generation.errorMessage}</Text>
          )}
          <TouchableOpacity style={s.retryBtn} onPress={() => router.back()}>
            <Text style={s.retryText}>← Réessayer</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* ── Résultat ─────────────────────────────────── */}
      {isDone && project.generation?.resultImageUrl && (() => {
        const afterUri = normalizeImageUrl(project.generation!.resultImageUrl)!;

        // Log URL en dev pour diagnostiquer les problèmes d'image
        if (__DEV__) console.log('[ResultScreen] afterUri:', afterUri, '| beforeUri:', beforeUri);

        return (
          <>
            {/* Slider Avant / Après */}
            <View
              style={s.slider}
              onLayout={(e) => {
                const w = e.nativeEvent.layout.width;
                if (w > 0) { setContainerWidth(w); containerWRef.current = w; }
              }}
              {...panResponder.panHandlers}
            >
              {/* Après — fond */}
              {afterImgError ? (
                <View style={[StyleSheet.absoluteFill, s.imgErrorBox]}>
                  <Text style={s.imgErrorEmoji}>🖼️</Text>
                  <Text style={s.imgErrorText}>Image indisponible</Text>
                  <Text style={s.imgErrorUrl} numberOfLines={2}>{afterUri}</Text>
                </View>
              ) : (
                <Image
                  source={{ uri: afterUri }}
                  style={[StyleSheet.absoluteFill, { width: containerWidth, height: COMPARISON_HEIGHT }]}
                  resizeMode="cover"
                  onError={() => { console.warn('[ResultScreen] Erreur chargement afterUri:', afterUri); setAfterImgError(true); }}
                />
              )}
              {/* Avant — clippé */}
              <View style={{
                position: 'absolute', top: 0, left: 0,
                width: Math.round(containerWidth * sliderValue),
                height: COMPARISON_HEIGHT, overflow: 'hidden',
              }}>
                {beforeImgError || !beforeUri ? null : (
                  <Image
                    source={{ uri: beforeUri ?? undefined }}
                    style={{ position: 'absolute', top: 0, left: 0, width: containerWidth, height: COMPARISON_HEIGHT }}
                    resizeMode="cover"
                    onError={() => setBeforeImgError(true)}
                  />
                )}
              </View>
              {/* Séparateur */}
              <View style={{
                position: 'absolute', top: 0,
                left: Math.round(containerWidth * sliderValue) - 1,
                width: 2, height: COMPARISON_HEIGHT, alignItems: 'center',
              }}>
                <View style={s.sliderLine} />
                <View style={s.sliderHandle}>
                  <Text style={s.sliderHandleText}>◀▶</Text>
                </View>
              </View>
              {/* Labels superposés */}
              <View style={s.sliderLabelLeft}>
                <Text style={s.sliderLabel}>Avant</Text>
              </View>
              <View style={s.sliderLabelRight}>
                <Text style={s.sliderLabel}>Après</Text>
              </View>
            </View>

            {/* Hint + zoom */}
            <View style={s.sliderFooter}>
              <Text style={s.sliderHint}>◀ Glissez pour comparer ▶</Text>
              <TouchableOpacity onPress={() => setFullscreenUri(afterUri)}>
                <Text style={s.zoomBtn}>🔍 Plein écran</Text>
              </TouchableOpacity>
            </View>

            {/* Actions */}
            <View style={s.actions}>
              {/* Sauvegarder */}
              <TouchableOpacity
                style={[s.actionBtn, s.actionSave, isSaving && s.actionDisabled]}
                onPress={handleSave}
                disabled={isSaving}
              >
                {isSaving
                  ? <ActivityIndicator color="#fff" size="small" />
                  : <Text style={s.actionText}>💾 Sauvegarder</Text>
                }
              </TouchableOpacity>

              {/* Partager */}
              <TouchableOpacity
                style={[s.actionBtn, s.actionShare, isSharing && s.actionDisabled]}
                onPress={handleShare}
                disabled={isSharing}
              >
                {isSharing
                  ? <ActivityIndicator color="#fff" size="small" />
                  : <Text style={s.actionText}>📤 Partager</Text>
                }
              </TouchableOpacity>
            </View>

            {/* Bouton acheter */}
            <TouchableOpacity
              style={s.shopBtn}
              onPress={() => router.push(`/products/${id}` as any)}
            >
              <LinearGradient
                colors={['#1a1a3e', '#2d1b69']}
                start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }}
                style={s.shopBtnGradient}
              >
                <Text style={s.shopBtnText}>🛒 Voir les meubles &amp; produits</Text>
                <Text style={s.shopBtnArrow}>›</Text>
              </LinearGradient>
            </TouchableOpacity>
          </>
        );
      })()}

      {/* ── Produits ─────────────────────────────────── */}
      {isDone && products && products.length > 0 && (
        <ProductsSection products={products} />
      )}

      {/* Modal plein écran */}
      <Modal
        visible={fullscreenUri !== null}
        transparent
        animationType="fade"
        onRequestClose={() => setFullscreenUri(null)}
        statusBarTranslucent
      >
        <View style={s.modalOverlay}>
          <StatusBar hidden />
          <TouchableOpacity style={s.modalClose} onPress={() => setFullscreenUri(null)}>
            <Text style={s.modalCloseText}>✕</Text>
          </TouchableOpacity>
          <ScrollView
            style={{ flex: 1 }}
            contentContainerStyle={s.modalContent}
            maximumZoomScale={4}
            minimumZoomScale={1}
            bouncesZoom
            centerContent
          >
            {fullscreenUri && (
              <Image
                source={{ uri: fullscreenUri ?? undefined }}
                style={{ width: windowWidth, height: windowHeight * 0.9, resizeMode: 'contain' }}
              />
            )}
          </ScrollView>
        </View>
      </Modal>

    </ScrollView>
  );
}

// ── Styles Produits ────────────────────────────────────────────────────────────

const ps = StyleSheet.create({

  // ── Section ──────────────────────────────────────────────────────────────────
  section: { marginTop: 12, marginBottom: 8 },
  sectionHeader: {
    flexDirection: 'row', alignItems: 'center',
    justifyContent: 'space-between', marginBottom: 14,
  },
  sectionTitle:  { fontSize: 18, fontWeight: '800', color: '#fff' },
  countPill: {
    backgroundColor: '#2d1b69', borderRadius: 20,
    paddingHorizontal: 10, paddingVertical: 4,
    borderWidth: 1, borderColor: '#4c1d95',
  },
  countPillText: { color: '#c4b5fd', fontSize: 12, fontWeight: '700' },

  // ── Card pleine largeur horizontale ──────────────────────────────────────────
  card: {
    flexDirection: 'row',
    alignItems: 'center',         // centrage vertical image/infos
    backgroundColor: '#1a1a3e',
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: '#2a2a5e',
    overflow: 'hidden',
    marginBottom: 10,
    padding: 10,
    gap: 12,
  },

  // ── Colonne image (gauche) — taille fixe carrée ───────────────────────────────
  imgCol: { width: 80, height: 80, flexShrink: 0, borderRadius: 10, overflow: 'hidden' },
  img:    { width: 80, height: 80 },
  imgFallback: {
    width: 80, height: 80,
    alignItems: 'center', justifyContent: 'center',
    borderRadius: 10,
  },
  imgFallbackEmoji: { fontSize: 28 },

  // ── Colonne infos (droite) ────────────────────────────────────────────────────
  infoCol: {
    flex: 1,
    gap: 4,
    justifyContent: 'center',
  },

  // Nom
  name: { color: '#fff', fontSize: 14, fontWeight: '700', lineHeight: 19 },

  // Badge marque inline
  brandPill: {
    alignSelf: 'flex-start',
    borderRadius: 8, borderWidth: 1.5,
    paddingHorizontal: 7, paddingVertical: 3,
  },
  brandPillText: { fontSize: 10, fontWeight: '900', letterSpacing: 0.3 },

  // Couleur / catégorie
  color: { color: '#a78bfa', fontSize: 12, fontStyle: 'italic' },
  cat:   { color: '#555',    fontSize: 11 },

  // Prix
  price: { color: '#fff', fontSize: 20, fontWeight: '900', letterSpacing: -0.5 },

  // Bouton CTA pleine largeur de la colonne
  buyBtn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    borderRadius: 10, borderWidth: 1.5,
    paddingVertical: 9, gap: 6,
  },
  buyBtnText:  { fontSize: 12, fontWeight: '800' },
  buyBtnArrow: { fontSize: 13, fontWeight: '900' },

  buyBtnOff: {
    borderRadius: 10, paddingVertical: 9,
    backgroundColor: '#111', borderWidth: 1, borderColor: '#222',
    alignItems: 'center',
  },
  buyBtnOffText: { color: '#444', fontSize: 11 },

  // ── Budget total ─────────────────────────────────────────────────────────────
  totalBox: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    backgroundColor: '#2d1b69', borderRadius: 16, padding: 18,
    marginTop: 4, marginBottom: 32,
    borderWidth: 1, borderColor: '#4c1d95',
  },
  totalLabel: { color: '#c4b5fd', fontSize: 15, fontWeight: '700' },
  totalSub:   { color: '#7c6fab', fontSize: 11, marginTop: 3 },
  totalPrice: { color: '#fff', fontSize: 28, fontWeight: '900' },
});

// ── Styles Loading ─────────────────────────────────────────────────────────────

const ls = StyleSheet.create({
  wrapper: {
    backgroundColor: '#1a1a3e', borderRadius: 20, padding: 28,
    alignItems: 'center', gap: 20, marginTop: 8,
  },
  circle: {
    width: 96, height: 96, borderRadius: 48, overflow: 'hidden',
    shadowColor: '#9B5DEA', shadowOpacity: 0.6, shadowRadius: 20,
    shadowOffset: { width: 0, height: 0 }, elevation: 12,
  },
  circleGradient: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  circleEmoji:    { fontSize: 42 },
  title:          { color: '#fff', fontSize: 18, fontWeight: '800' },
  sub:            { color: '#888', fontSize: 13, marginTop: -8 },

  steps: { width: '100%', gap: 12 },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  stepDot: {
    width: 28, height: 28, borderRadius: 14, backgroundColor: '#252550',
    alignItems: 'center', justifyContent: 'center', borderWidth: 1.5, borderColor: '#3a3a6e',
  },
  stepDotActive:  { borderColor: '#9B5DEA', backgroundColor: '#3d1b6e' },
  stepDotDone:    { borderColor: '#22c55e', backgroundColor: '#14532d' },
  stepDotInner:   { width: 8, height: 8, borderRadius: 4, backgroundColor: '#444' },
  stepCheck:      { color: '#22c55e', fontSize: 13, fontWeight: '800' },
  stepLabel:      { color: '#666', fontSize: 13, fontWeight: '600' },
  stepLabelActive:{ color: '#fff' },
  stepLabelDone:  { color: '#22c55e' },

  tipBox: {
    backgroundColor: '#252550', borderRadius: 12, padding: 14,
    width: '100%', borderLeftWidth: 3, borderLeftColor: '#7C3AED',
  },
  tipText: { color: '#ccc', fontSize: 12, lineHeight: 18 },
  hint:    { color: '#444', fontSize: 11 },
});

// ── Styles Screen ──────────────────────────────────────────────────────────────

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  content:   { padding: 20, paddingTop: 54, paddingBottom: 50 },
  center:    { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0f0f23' },

  // Header
  header: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 20 },
  backBtn: {
    width: 40, height: 40, borderRadius: 20, backgroundColor: '#1a1a3e',
    alignItems: 'center', justifyContent: 'center',
  },
  backText:     { color: '#fff', fontSize: 18, fontWeight: '700' },
  headerCenter: { flex: 1 },
  title:        { fontSize: 18, fontWeight: '800', color: '#fff' },
  styleBadge:   { color: '#7C3AED', fontWeight: '700', fontSize: 11, textTransform: 'uppercase', marginTop: 2 },
  regenBtn: {
    width: 40, height: 40, borderRadius: 20, backgroundColor: '#1a1a3e',
    alignItems: 'center', justifyContent: 'center',
  },
  regenText: { fontSize: 18 },

  // Error
  errorBox:   { backgroundColor: '#2a1a1a', borderRadius: 20, padding: 32, alignItems: 'center', gap: 14 },
  errorEmoji: { fontSize: 52 },
  errorTitle: { color: '#ff6b6b', fontSize: 18, fontWeight: '800' },
  errorDetail:{ color: '#cc8888', fontSize: 12, textAlign: 'center' },
  retryBtn:   { backgroundColor: '#7C3AED', paddingHorizontal: 24, paddingVertical: 12, borderRadius: 12 },
  retryText:  { color: '#fff', fontWeight: '700', fontSize: 15 },

  // Image error fallback
  imgErrorBox: {
    backgroundColor: '#12122e', alignItems: 'center',
    justifyContent: 'center', gap: 8, padding: 20,
  },
  imgErrorEmoji: { fontSize: 40 },
  imgErrorText:  { color: '#555', fontSize: 14, fontWeight: '700' },
  imgErrorUrl:   { color: '#333', fontSize: 9, textAlign: 'center', paddingHorizontal: 20 },

  // Slider
  slider: {
    width: '100%', height: COMPARISON_HEIGHT,
    borderRadius: 18, overflow: 'hidden', backgroundColor: '#111',
  },
  sliderLine:   { width: 2, height: COMPARISON_HEIGHT, backgroundColor: 'rgba(255,255,255,0.9)' },
  sliderHandle: {
    position: 'absolute', top: COMPARISON_HEIGHT / 2 - 22,
    backgroundColor: '#7C3AED', borderRadius: 22,
    width: 44, height: 44, alignItems: 'center', justifyContent: 'center',
    shadowColor: '#7C3AED', shadowOpacity: 0.8, shadowRadius: 8,
    shadowOffset: { width: 0, height: 0 }, elevation: 8,
    borderWidth: 2, borderColor: 'rgba(255,255,255,0.3)',
  },
  sliderHandleText: { color: '#fff', fontSize: 13, fontWeight: '800' },
  sliderLabelLeft: {
    position: 'absolute', bottom: 10, left: 10,
    backgroundColor: 'rgba(0,0,0,0.5)', borderRadius: 8,
    paddingHorizontal: 8, paddingVertical: 4,
  },
  sliderLabelRight: {
    position: 'absolute', bottom: 10, right: 10,
    backgroundColor: 'rgba(124,58,237,0.7)', borderRadius: 8,
    paddingHorizontal: 8, paddingVertical: 4,
  },
  sliderLabel: { color: '#fff', fontSize: 11, fontWeight: '700' },

  sliderFooter: {
    flexDirection: 'row', justifyContent: 'space-between',
    alignItems: 'center', marginTop: 8, marginBottom: 18,
  },
  sliderHint: { color: '#555', fontSize: 12 },
  zoomBtn:    { color: '#7C3AED', fontSize: 13, fontWeight: '700' },

  // Actions
  actions: { flexDirection: 'row', gap: 10, marginBottom: 12 },
  actionBtn: {
    flex: 1, borderRadius: 14, paddingVertical: 16,
    alignItems: 'center', justifyContent: 'center',
  },
  actionSave:    { backgroundColor: '#1a1a3e', borderWidth: 1.5, borderColor: '#22c55e' },
  actionShare:   { backgroundColor: '#7C3AED' },
  actionDisabled:{ opacity: 0.5 },
  actionText:    { color: '#fff', fontWeight: '700', fontSize: 14 },

  shopBtn: { borderRadius: 14, overflow: 'hidden', marginBottom: 28 },
  shopBtnGradient: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 20, paddingVertical: 16,
  },
  shopBtnText:  { color: '#fff', fontWeight: '700', fontSize: 15 },
  shopBtnArrow: { color: '#7C3AED', fontSize: 22, fontWeight: '800' },

  // Modal
  modalOverlay: { flex: 1, backgroundColor: '#000' },
  modalClose: {
    position: 'absolute', top: 50, right: 20, zIndex: 10,
    backgroundColor: 'rgba(0,0,0,0.7)', borderRadius: 20,
    width: 42, height: 42, justifyContent: 'center', alignItems: 'center',
  },
  modalCloseText: { color: '#fff', fontSize: 18, fontWeight: '800' },
  modalContent:   { flex: 1, justifyContent: 'center', alignItems: 'center' },
});
