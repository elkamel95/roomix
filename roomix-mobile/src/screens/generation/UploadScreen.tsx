import React, { useMemo, useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, Image,
  Alert, ActivityIndicator, ScrollView, TextInput, Switch,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import {
  ColorPalette, DecorationStyle,
  ROOM_TYPE_OPTIONS, RoomType, STYLE_CARDS,
} from '../../types';
import StylePickerModal from '../../components/StylePickerModal';
import {
  ObjectRef, PromptMode, ImageSize, ImageQuality, ImageFormat, ImageBackground,
  ProductBrand, projectService,
} from '../../services/projectService';
import { useProjectStore } from '../../store/slices/projectStore';
import { useAuthStore } from '../../store/slices/authStore';

// ── Types locaux ──────────────────────────────────────────────────────────────

type AiProvider  = 'QWEN' | 'FLUX' | 'CHATGPT';

// ── Données statiques ─────────────────────────────────────────────────────────

const AI_PROVIDERS: { key: AiProvider; label: string; emoji: string; recommended?: boolean }[] = [
  { key: 'QWEN',    label: 'Wan2.7',  emoji: '🚀', recommended: true },
  { key: 'CHATGPT', label: 'ChatGPT', emoji: '🤖' },
  { key: 'FLUX',    label: 'Flux.1',  emoji: '⚡' },
];

const COLOR_PALETTES: { key: ColorPalette; label: string; colors: string[] }[] = [
  { key: 'beige and white with warm neutral tones',              label: 'Beige & Blanc',  colors: ['#F5F0E8', '#E8DCC8', '#D4C4A8'] },
  { key: 'cool grey and silver neutral tones',                   label: 'Gris Neutre',    colors: ['#E0E0E0', '#BDBDBD', '#9E9E9E'] },
  { key: 'warm earthy tones — terracotta, rust, amber',          label: 'Terre & Brique', colors: ['#C0623E', '#D4825A', '#E8A878'] },
  { key: 'dark moody tones — charcoal, black, deep navy',        label: 'Dark Moody',     colors: ['#212121', '#37474F', '#1A237E'] },
  { key: 'vibrant colorful palette',                             label: 'Coloré vif',     colors: ['#E53935', '#43A047', '#1E88E5'] },
];

// ── Paramètres de rendu ChatGPT (gpt-image-2) ────────────────────────────────

const IMAGE_SIZES: { key: ImageSize; label: string; sub: string; badge?: string }[] = [
  { key: 'auto',      label: 'Auto',       sub: 'Automatique' },
  { key: '1024x1024', label: 'Carré',      sub: '1:1 · 1024×1024' },
  { key: '1536x1024', label: 'Paysage',    sub: '3:2 · 1536×1024' },
  { key: '1024x1536', label: 'Portrait',   sub: '2:3 · 1024×1536' },
  { key: '2048x2048', label: '2K Carré',   sub: '2048×2048',         badge: '2K' },
  { key: '2048x1152', label: '2K Paysage', sub: '16:9 · 2048×1152',  badge: '2K' },
  { key: '3840x2160', label: '4K',         sub: 'UHD · 3840×2160',   badge: '4K ⚗' },
  { key: '2160x3840', label: '4K Portrait',sub: 'UHD · 2160×3840',   badge: '4K ⚗' },
];

const IMAGE_QUALITIES: { key: ImageQuality; label: string; sub: string }[] = [
  { key: 'auto',   label: 'Auto',    sub: 'Automatique' },
  { key: 'high',   label: 'Haute',   sub: 'Meilleure qualité' },
  { key: 'medium', label: 'Moyenne', sub: 'Équilibre' },
  { key: 'low',    label: 'Basse',   sub: 'Draft rapide' },
];

const IMAGE_FORMATS: { key: ImageFormat; label: string; sub: string }[] = [
  { key: 'jpeg', label: 'JPEG', sub: 'Rapide · défaut' },
  { key: 'png',  label: 'PNG',  sub: 'Sans perte' },
  { key: 'webp', label: 'WebP', sub: 'Moderne' },
];

const COMPRESSIONS: { value: number; label: string }[] = [
  { value: 95, label: 'Max (95)' },
  { value: 85, label: 'Haute (85)' },
  { value: 70, label: 'Moy. (70)' },
  { value: 50, label: 'Légère (50)' },
];

// ── Screen ────────────────────────────────────────────────────────────────────

export default function UploadScreen() {
  const router = useRouter();
  const { addProject } = useProjectStore();

  const [imageUri,        setImageUri]        = useState<string | null>(null);
  const [selectedStyle,   setSelectedStyle]   = useState<DecorationStyle | null>(null);
  const [showStylePicker, setShowStylePicker] = useState(false);
  const [roomType,        setRoomType]        = useState<RoomType | null>(null);
  const [colorPalette,    setColorPalette]    = useState<ColorPalette | null>(null);
  const [customNote,      setCustomNote]      = useState('');
  const [objectRefs,      setObjectRefs]      = useState<ObjectRef[]>([]);
  const [promptMode,        setPromptMode]        = useState<PromptMode>('CREATIVE');
  const [selectedAi,        setSelectedAi]        = useState<AiProvider>('QWEN');
  const [isGenerating,      setIsGenerating]      = useState(false);
  // ── Paramètres de rendu gpt-image-2 (visible uniquement si CHATGPT sélectionné)
  const [imageSize,         setImageSize]         = useState<ImageSize>('auto');
  const [imageQuality,      setImageQuality]      = useState<ImageQuality>('auto');
  const [imageFormat,       setImageFormat]       = useState<ImageFormat>('jpeg');
  const [imageCompression,  setImageCompression]  = useState<number>(85);
  const [imageBackground,   setImageBackground]   = useState<ImageBackground>('auto');

  // ── Recherche produits en ligne ───────────────────────────────────────────
  const [productSearchEnabled, setProductSearchEnabled] = useState(false);
  const [selectedBrands,       setSelectedBrands]       = useState<string[]>(['IKEA']); // IKEA sélectionné par défaut

  const toggleBrand = (brand: string) => {
    setSelectedBrands(prev =>
      prev.includes(brand) ? prev.filter(b => b !== brand) : [...prev, brand]
    );
  };

  // ── Token system ──────────────────────────────────────────────────────────
  const { user } = useAuthStore();
  const tokenBalance = user?.tokenBalance ?? 0;

  /** Coût estimé en tokens selon la grille tarifaire gpt-image-2 */
  const estimatedCost = useMemo(() => {
    if (selectedAi !== 'CHATGPT') return 30; // Qwen/Flux : tarif fixe
    const isSquare = imageSize === 'auto' || imageSize === '1024x1024';
    const is4k = imageSize === '3840x2160' || imageSize === '2160x3840';
    const is2kSquare = imageSize === '2048x2048';
    const is2kRect = imageSize === '2048x1152' || (imageSize as string) === '1152x2048';
    const effectiveQuality = imageQuality === 'auto' ? 'medium' : imageQuality;

    const COST: Record<string, Record<string, number>> = {
      low:    { sq1024: 6,   rect1024: 5,   sq2048: 24,  rect2048: 12,  rect4k: 48   },
      medium: { sq1024: 53,  rect1024: 41,  sq2048: 212, rect2048: 106, rect4k: 424  },
      high:   { sq1024: 211, rect1024: 165, sq2048: 844, rect2048: 422, rect4k: 1688 },
    };
    const row = COST[effectiveQuality] ?? COST.medium;
    if (is4k)      return row.rect4k;
    if (is2kSquare) return row.sq2048;
    if (is2kRect)   return row.rect2048;
    if (isSquare)   return row.sq1024;
    return row.rect1024; // 1024×1536 ou 1536×1024
  }, [selectedAi, imageSize, imageQuality]);

  const hasEnoughTokens = tokenBalance >= estimatedCost;

  // ── Helpers ────────────────────────────────────────────────────────────────

  const pickImage = async (fromCamera: boolean) => {
    const perm = fromCamera
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();

    if (!perm.granted) {
      Alert.alert('Permission refusée', "Activez l'accès dans les paramètres.");
      return;
    }

    const result = fromCamera
      ? await ImagePicker.launchCameraAsync({ quality: 0.85, allowsEditing: true })
      : await ImagePicker.launchImageLibraryAsync({ quality: 0.85, allowsEditing: true, mediaTypes: ['images'] });

    if (!result.canceled && result.assets[0]) {
      const compressed = await ImageManipulator.manipulateAsync(
        result.assets[0].uri,
        [{ resize: { width: 1024 } }],
        { compress: 0.85, format: ImageManipulator.SaveFormat.JPEG }
      );
      setImageUri(compressed.uri);
    }
  };

  // ── Objets de référence ────────────────────────────────────────────────────

  const addObjectRef = async () => {
    if (objectRefs.length >= 15) {
      Alert.alert('Maximum atteint', '15 objets de référence maximum.');
      return;
    }
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert('Permission refusée', "Activez l'accès à la galerie dans les paramètres.");
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      quality: 0.8, allowsEditing: true, mediaTypes: ['images'],
    });
    if (!result.canceled && result.assets[0]) {
      const compressed = await ImageManipulator.manipulateAsync(
        result.assets[0].uri,
        [{ resize: { width: 800 } }],
        { compress: 0.8, format: ImageManipulator.SaveFormat.JPEG }
      );
      setObjectRefs(prev => [...prev, {
        id: Math.random().toString(36).slice(2),
        title: '',
        imageUri: compressed.uri,
      }]);
    }
  };

  const updateObjectRefTitle = (id: string, title: string) =>
    setObjectRefs(prev => prev.map(r => r.id === id ? { ...r, title } : r));

  const removeObjectRef = (id: string) =>
    setObjectRefs(prev => prev.filter(r => r.id !== id));

  const pickRandomStyle = () => {
    const keys = STYLE_CARDS.map(c => c.key);
    const random = keys[Math.floor(Math.random() * keys.length)];
    setSelectedStyle(random);
  };

  const handleGenerate = async () => {
    if (!imageUri || !selectedStyle) {
      Alert.alert('Requis', 'Ajoutez une photo et choisissez un style.');
      return;
    }
    setIsGenerating(true);
    try {
      const project = await projectService.createProject({
        imageUri,
        style:        selectedStyle,
        aiModel:      selectedAi,
        roomType:     roomType     ?? undefined,
        colorPalette: colorPalette ?? undefined,
        customNote:   customNote.trim() || undefined,
        promptMode,
        objectRefs:   objectRefs.filter(r => r.imageUri),
        // Params ChatGPT (ignorés par le backend si autre modèle)
        imageSize,
        imageQuality,
        imageFormat,
        imageCompression,
        imageBackground,
        // Recherche produits en ligne
        productSearchEnabled,
        preferredBrands: productSearchEnabled && selectedBrands.length > 0
          ? (selectedBrands as ProductBrand[])
          : undefined,
      });
      addProject(project, imageUri);
      router.replace(`/project/${project.id}`);
    } catch (e: any) {
      const msg = e?.response?.status === 402
        ? 'Quota gratuit atteint. Passez en Premium !'
        : 'Génération impossible. Réessayez.';
      Alert.alert('Erreur', msg);
    } finally {
      setIsGenerating(false);
    }
  };

  const canGenerate = !!imageUri && !!selectedStyle && !isGenerating;

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <ScrollView
      style={s.container}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
    >

      {/* ── Header ──────────────────────────────────────── */}
      <Text style={s.title}>Nouvelle Déco</Text>
      <Text style={s.subtitle}>Transformez votre intérieur en quelques secondes</Text>

      {/* ── 1. Photo ────────────────────────────────────── */}
      {imageUri ? (
        <View style={s.previewWrapper}>
          <Image source={{ uri: imageUri }} style={s.preview} />
          <View style={s.previewOverlay}>
            <TouchableOpacity style={s.changePhotoBtn} onPress={() => setImageUri(null)}>
              <Text style={s.changePhotoBtnText}>✕ Changer</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : (
        <View style={s.uploadZone}>
          <Text style={s.uploadIcon}>🏠</Text>
          <Text style={s.uploadTitle}>Photographiez votre pièce</Text>
          <Text style={s.uploadSub}>JPEG ou PNG · max 10 Mo</Text>
          <View style={s.uploadButtons}>
            <TouchableOpacity style={s.uploadBtn} onPress={() => pickImage(true)}>
              <Text style={s.uploadBtnEmoji}>📷</Text>
              <Text style={s.uploadBtnLabel}>Appareil photo</Text>
            </TouchableOpacity>
            <TouchableOpacity style={s.uploadBtn} onPress={() => pickImage(false)}>
              <Text style={s.uploadBtnEmoji}>🖼️</Text>
              <Text style={s.uploadBtnLabel}>Galerie</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* ── 2. Style ────────────────────────────────────── */}
      <View style={s.sectionHeader}>
        <Text style={s.sectionTitle}>🎨 Style de décoration</Text>
        <TouchableOpacity style={s.randomBtn} onPress={pickRandomStyle}>
          <Text style={s.randomBtnText}>🎲 Aléatoire</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity
        style={[s.styleBtn, selectedStyle && s.styleBtnSelected]}
        onPress={() => setShowStylePicker(true)}
        activeOpacity={0.8}
      >
        {selectedStyle ? (() => {
          const card = STYLE_CARDS.find(c => c.key === selectedStyle)!;
          return (
            <View style={s.styleBtnContent}>
              <View style={s.styleEmojiBox}>
                <Text style={s.styleEmoji}>{card.emoji}</Text>
              </View>
              <View style={s.styleInfo}>
                <Text style={s.styleName}>{card.label}</Text>
                <Text style={s.styleTagline} numberOfLines={1}>{card.tagline}</Text>
              </View>
              <Text style={s.styleChevron}>›</Text>
            </View>
          );
        })() : (
          <View style={s.styleBtnContent}>
            <View style={[s.styleEmojiBox, s.styleEmojiBoxEmpty]}>
              <Text style={s.styleEmoji}>🎨</Text>
            </View>
            <View style={s.styleInfo}>
              <Text style={s.stylePlaceholder}>Choisir un style</Text>
              <Text style={s.stylePlaceholderSub}>16 univers disponibles</Text>
            </View>
            <Text style={s.styleChevron}>›</Text>
          </View>
        )}
      </TouchableOpacity>

      <StylePickerModal
        visible={showStylePicker}
        selected={selectedStyle}
        onSelect={(style) => { setSelectedStyle(style); setShowStylePicker(false); }}
        onClose={() => setShowStylePicker(false)}
      />

      {/* ── 3. Type de pièce ────────────────────────────── */}
      <Text style={s.sectionTitle}>🏠 Type de pièce</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.chipScroll} contentContainerStyle={s.chipScrollContent}>
        {ROOM_TYPE_OPTIONS.map(opt => (
          <TouchableOpacity
            key={opt.key}
            style={[s.chip, roomType === opt.key && s.chipActive]}
            onPress={() => setRoomType(roomType === opt.key ? null : opt.key)}
          >
            <Text style={s.chipEmoji}>{opt.emoji}</Text>
            <Text style={[s.chipLabel, roomType === opt.key && s.chipLabelActive]}>{opt.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* ── 4. Palette de couleurs ──────────────────────── */}
      <Text style={[s.sectionTitle, s.mt24]}>🎨 Palette de couleurs</Text>
      <View style={s.paletteGrid}>
        {COLOR_PALETTES.map(p => {
          const active = colorPalette === p.key;
          return (
            <TouchableOpacity
              key={p.key}
              style={[s.paletteCard, active && s.paletteCardActive]}
              onPress={() => setColorPalette(active ? null : p.key)}
            >
              <View style={s.swatchRow}>
                {p.colors.map((hex, i) => (
                  <View key={i} style={[s.swatch, { backgroundColor: hex }]} />
                ))}
              </View>
              <Text style={[s.paletteLabel, active && s.paletteLabelActive]}>{p.label}</Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* ── 5. Note personnalisée ───────────────────────── */}
      <Text style={[s.sectionTitle, s.mt24]}>✍️ Envie spéciale</Text>
      <TextInput
        style={s.noteInput}
        placeholder="Ex : je veux une ambiance cosy avec des plantes et des coussins colorés…"
        placeholderTextColor="#555"
        value={customNote}
        onChangeText={setCustomNote}
        multiline
        numberOfLines={3}
        maxLength={300}
        textAlignVertical="top"
      />
      {customNote.length > 0 && (
        <Text style={s.noteCount}>{customNote.length}/300</Text>
      )}

      {/* ── 7. Objets de référence ──────────────────────── */}
      <View style={s.sectionHeader}>
        <Text style={[s.sectionTitle, s.mt24]}>📷 Intégrer des objets</Text>
        {objectRefs.length > 0 && (
          <Text style={s.refCount}>{objectRefs.length}/15</Text>
        )}
      </View>
      <Text style={s.refHint}>
        Ajoutez jusqu'à 3 photos d'objets (canapé, table, lampe…) que l'IA placera dans la pièce
      </Text>

      {objectRefs.map(ref => (
        <View key={ref.id} style={s.refCard}>
          <Image source={{ uri: ref.imageUri }} style={s.refImg} resizeMode="cover" />
          <TextInput
            style={s.refInput}
            placeholder="Nom de l'objet (ex: Mon canapé)"
            placeholderTextColor="#555"
            value={ref.title}
            onChangeText={t => updateObjectRefTitle(ref.id, t)}
            maxLength={40}
          />
          <TouchableOpacity style={s.refRemove} onPress={() => removeObjectRef(ref.id)}>
            <Text style={s.refRemoveText}>✕</Text>
          </TouchableOpacity>
        </View>
      ))}

      {objectRefs.length < 15 && (
        <TouchableOpacity style={s.refAddBtn} onPress={addObjectRef}>
          <Text style={s.refAddBtnText}>＋ Ajouter un objet</Text>
        </TouchableOpacity>
      )}

      {/* ── 8. Modèle IA ────────────────────────────────── */}
      <Text style={[s.sectionTitle, s.mt24]}>🤖 Modèle IA</Text>
      <View style={s.aiRow}>
        {AI_PROVIDERS.map(ai => {
          const active = selectedAi === ai.key;
          return (
            <TouchableOpacity
              key={ai.key}
              style={[s.aiCard, active && s.aiCardActive]}
              onPress={() => setSelectedAi(ai.key)}
            >
              {ai.recommended && (
                <View style={s.recBadge}><Text style={s.recBadgeText}>★</Text></View>
              )}
              <Text style={s.aiEmoji}>{ai.emoji}</Text>
              <Text style={[s.aiLabel, active && s.aiLabelActive]}>{ai.label}</Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* ── 9. Paramètres ChatGPT (gpt-image-2) ─────────── */}
      {selectedAi === 'CHATGPT' && (
        <View style={s.chatgptSection}>
          <View style={s.chatgptHeader}>
            <Text style={s.chatgptTitle}>🤖 Paramètres gpt-image-2</Text>
            <View style={s.chatgptBadge}><Text style={s.chatgptBadgeText}>ChatGPT</Text></View>
          </View>

          {/* ── Solde tokens + coût estimé ── */}
          <View style={s.tokenRow}>
            <View style={s.tokenBalance}>
              <Text style={s.tokenBalanceLabel}>💰 Solde</Text>
              <Text style={[s.tokenBalanceValue, !hasEnoughTokens && s.tokenBalanceInsuffisant]}>
                {tokenBalance} tokens
              </Text>
            </View>
            <View style={s.tokenArrow}><Text style={s.tokenArrowText}>→</Text></View>
            <View style={[s.tokenCost, !hasEnoughTokens && s.tokenCostInsuffisant]}>
              <Text style={s.tokenCostLabel}>Coût estimé</Text>
              <Text style={s.tokenCostValue}>{estimatedCost} tokens</Text>
            </View>
          </View>
          {!hasEnoughTokens && (
            <View style={s.tokenWarning}>
              <Text style={s.tokenWarningText}>
                ⚠️ Solde insuffisant — il vous manque {estimatedCost - tokenBalance} tokens
              </Text>
            </View>
          )}

          {/* Taille */}
          <Text style={s.subSectionTitle}>📐 Taille</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}
            style={s.chipScroll} contentContainerStyle={s.chipScrollContent}>
            {IMAGE_SIZES.map(opt => {
              const active = imageSize === opt.key;
              return (
                <TouchableOpacity key={opt.key}
                  style={[s.sizeChip, active && s.sizeChipActive]}
                  onPress={() => setImageSize(opt.key)}>
                  {opt.badge && (
                    <View style={[s.sizeBadge, opt.badge.includes('⚗') && s.sizeBadgeExp]}>
                      <Text style={s.sizeBadgeText}>{opt.badge}</Text>
                    </View>
                  )}
                  <Text style={[s.sizeChipLabel, active && s.sizeChipLabelActive]}>{opt.label}</Text>
                  <Text style={s.sizeChipSub}>{opt.sub}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>

          {/* Qualité */}
          <Text style={[s.subSectionTitle, s.mt12]}>✨ Qualité</Text>
          <View style={s.qualityRow}>
            {IMAGE_QUALITIES.map(opt => {
              const active = imageQuality === opt.key;
              return (
                <TouchableOpacity key={opt.key}
                  style={[s.qualityCard, active && s.qualityCardActive]}
                  onPress={() => setImageQuality(opt.key)}>
                  <Text style={[s.qualityLabel, active && s.qualityLabelActive]}>{opt.label}</Text>
                  <Text style={s.qualitySub}>{opt.sub}</Text>
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Format */}
          <Text style={[s.subSectionTitle, s.mt12]}>🗂 Format de sortie</Text>
          <View style={s.formatRow}>
            {IMAGE_FORMATS.map(opt => {
              const active = imageFormat === opt.key;
              return (
                <TouchableOpacity key={opt.key}
                  style={[s.formatCard, active && s.formatCardActive]}
                  onPress={() => setImageFormat(opt.key)}>
                  <Text style={[s.formatLabel, active && s.formatLabelActive]}>{opt.label}</Text>
                  <Text style={s.formatSub}>{opt.sub}</Text>
                </TouchableOpacity>
              );
            })}
          </View>

          {/* Compression (jpeg/webp seulement) */}
          {imageFormat !== 'png' && (
            <>
              <Text style={[s.subSectionTitle, s.mt12]}>🗜 Compression</Text>
              <View style={s.formatRow}>
                {COMPRESSIONS.map(opt => {
                  const active = imageCompression === opt.value;
                  return (
                    <TouchableOpacity key={opt.value}
                      style={[s.formatCard, active && s.formatCardActive]}
                      onPress={() => setImageCompression(opt.value)}>
                      <Text style={[s.formatLabel, active && s.formatLabelActive]}>{opt.label}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </>
          )}

          {/* Fond */}
          <Text style={[s.subSectionTitle, s.mt12]}>🖼 Fond</Text>
          <View style={s.bgRow}>
            {(['auto', 'opaque'] as ImageBackground[]).map(opt => {
              const active = imageBackground === opt;
              const labels: Record<string, string> = { auto: 'Auto', opaque: 'Opaque' };
              const subs:   Record<string, string> = { auto: 'Choix automatique', opaque: 'Fond plein' };
              return (
                <TouchableOpacity key={opt}
                  style={[s.formatCard, active && s.formatCardActive]}
                  onPress={() => setImageBackground(opt)}>
                  <Text style={[s.formatLabel, active && s.formatLabelActive]}>{labels[opt]}</Text>
                  <Text style={s.formatSub}>{subs[opt]}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>
      )}

      {/* ── 10. Mode de génération ──────────────────────── */}
      <Text style={[s.sectionTitle, s.mt24]}>⚙️ Mode de génération</Text>

      {/* Ligne 1 : Créatif + Pro */}
      <View style={s.modeRow}>

        <TouchableOpacity
          style={[s.modeCard, promptMode === 'CREATIVE' && s.modeCardActive]}
          onPress={() => setPromptMode('CREATIVE')}
          activeOpacity={0.8}
        >
          <View style={s.modeHeader}>
            <Text style={s.modeEmoji}>🎨</Text>
            {promptMode === 'CREATIVE' && <View style={s.modeDot} />}
          </View>
          <Text style={[s.modeLabel, promptMode === 'CREATIVE' && s.modeLabelActive]}>Créatif</Text>
          <Text style={s.modeDesc}>Transformation complète, liberté artistique maximale</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[s.modeCard, promptMode === 'PRO' && s.modeCardActive, s.modeCardPro]}
          onPress={() => setPromptMode('PRO')}
          activeOpacity={0.8}
        >
          <View style={s.modeHeader}>
            <Text style={s.modeEmoji}>🏛️</Text>
            {promptMode === 'PRO' && <View style={s.modeDot} />}
          </View>
          <Text style={[s.modeLabel, promptMode === 'PRO' && s.modeLabelActive]}>Pro</Text>
          <Text style={s.modeDesc}>Architecture préservée, résultat réaliste garanti</Text>
          <View style={s.proBadge}><Text style={s.proBadgeText}>NOUVEAU</Text></View>
        </TouchableOpacity>

      </View>

      {/* Ligne 2 : Expert (CHAIN) — pleine largeur */}
      <TouchableOpacity
        style={[s.modeCardFull, promptMode === 'CHAIN' && s.modeCardFullActive]}
        onPress={() => setPromptMode('CHAIN')}
        activeOpacity={0.8}
      >
        <View style={s.modeCardFullInner}>
          <View style={s.chainLeft}>
            <Text style={s.modeEmoji}>🧠</Text>
          </View>
          <View style={s.chainBody}>
            <View style={s.chainTitleRow}>
              <Text style={[s.modeLabel, promptMode === 'CHAIN' && s.modeLabelActive]}>Expert</Text>
              <View style={s.chainBadge}><Text style={s.chainBadgeText}>IA AVANCÉE</Text></View>
            </View>
            <Text style={s.modeDesc}>
              Analyse la pièce en profondeur · Élabore une stratégie de rénovation · Génère un prompt sur-mesure
            </Text>
            <View style={s.chainSteps}>
              <Text style={s.chainStep}>① Analyse visuelle</Text>
              <Text style={s.chainArrow}>→</Text>
              <Text style={s.chainStep}>② Stratégie</Text>
              <Text style={s.chainArrow}>→</Text>
              <Text style={s.chainStep}>③ Génération</Text>
            </View>
          </View>
          {promptMode === 'CHAIN' && <View style={[s.modeDot, s.chainDot]} />}
        </View>
      </TouchableOpacity>

      {/* Détail du mode sélectionné */}
      {promptMode === 'CREATIVE' && (
        <View style={s.modeDetail}>
          <Text style={s.modeDetailText}>
            ✦ Prompt détaillé par style · Liberté créative · Transformation profonde
          </Text>
        </View>
      )}
      {promptMode === 'PRO' && (
        <View style={[s.modeDetail, s.modeDetailPro]}>
          <Text style={s.modeDetailText}>
            ✦ Règles strictes · Murs/fenêtres/structure inchangés · Qualité magazine
          </Text>
        </View>
      )}
      {promptMode === 'CHAIN' && (
        <View style={[s.modeDetail, s.modeDetailChain]}>
          <Text style={s.modeDetailText}>
            ✦ Le modèle IA analyse votre pièce, planifie la rénovation puis génère automatiquement le meilleur prompt · Résultat plus long mais plus précis
          </Text>
        </View>
      )}

      {/* ── Produits en ligne ───────────────────────────── */}
      <View style={s.productSection}>
        <View style={s.productHeader}>
          <Text style={s.productTitle}>🛍️ Produits en ligne</Text>
          <Switch
            value={productSearchEnabled}
            onValueChange={(v) => {
              setProductSearchEnabled(v);
              if (!v) setSelectedBrands([]);
            }}
            trackColor={{ false: '#2a2a5e', true: '#6D28D9' }}
            thumbColor={productSearchEnabled ? '#9B5DEA' : '#888'}
          />
        </View>
        <Text style={s.productSubtitle}>
          Trouve des meubles réels correspondant à votre style
        </Text>
        {productSearchEnabled && (
          <View style={s.brandRow}>
            {/* IKEA — API publique disponible */}
            <TouchableOpacity
              style={[s.brandChip, selectedBrands.includes('IKEA') && { borderColor: '#FFD700', backgroundColor: '#FFD70022' }]}
              onPress={() => toggleBrand('IKEA')}
              activeOpacity={0.8}
            >
              <Text style={[s.brandChipText, selectedBrands.includes('IKEA') && { color: '#FFD700' }]}>
                🟡 IKEA
              </Text>
            </TouchableOpacity>

            {/* Conforama — pas d'API publique, désactivé */}
            <View style={[s.brandChip, s.brandChipDisabled]}>
              <Text style={s.brandChipTextDisabled}>🔴 Conforama</Text>
              <View style={s.brandChipSoonBadge}>
                <Text style={s.brandChipSoonText}>bientôt</Text>
              </View>
            </View>
          </View>
        )}
      </View>

      {/* ── Bouton générer ──────────────────────────────── */}
      <TouchableOpacity
        style={[s.generateBtn, !canGenerate && s.generateBtnDisabled]}
        onPress={handleGenerate}
        disabled={!canGenerate}
        activeOpacity={0.85}
      >
        <LinearGradient
          colors={canGenerate ? ['#9B5DEA', '#6D28D9'] : ['#2a2a5e', '#2a2a5e']}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={s.generateBtnGradient}
        >
          {isGenerating
            ? <ActivityIndicator color="#fff" size="small" />
            : <Text style={s.generateBtnText}>✨ Générer la décoration</Text>
          }
        </LinearGradient>
      </TouchableOpacity>

    </ScrollView>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s = StyleSheet.create({
  container:   { flex: 1, backgroundColor: '#0f0f23' },
  content:     { padding: 20, paddingTop: 60, paddingBottom: 50 },
  title:       { fontSize: 28, fontWeight: '800', color: '#fff', marginBottom: 4 },
  subtitle:    { fontSize: 13, color: '#666', marginBottom: 28 },
  mt24:        { marginTop: 24 },

  // Photo
  uploadZone: {
    backgroundColor: '#1a1a3e', borderRadius: 20, borderWidth: 2,
    borderColor: '#2a2a5e', borderStyle: 'dashed',
    alignItems: 'center', paddingVertical: 32, marginBottom: 28,
  },
  uploadIcon:    { fontSize: 48, marginBottom: 10 },
  uploadTitle:   { color: '#ccc', fontSize: 16, fontWeight: '700', marginBottom: 4 },
  uploadSub:     { color: '#555', fontSize: 12, marginBottom: 20 },
  uploadButtons: { flexDirection: 'row', gap: 12 },
  uploadBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    backgroundColor: '#252550', borderRadius: 14, paddingVertical: 12, paddingHorizontal: 20,
  },
  uploadBtnEmoji: { fontSize: 20 },
  uploadBtnLabel: { color: '#ccc', fontWeight: '600', fontSize: 14 },

  previewWrapper: { borderRadius: 20, overflow: 'hidden', marginBottom: 28 },
  preview:        { width: '100%', height: 220 },
  previewOverlay: { position: 'absolute', top: 10, right: 10 },
  changePhotoBtn: {
    backgroundColor: 'rgba(0,0,0,0.6)', borderRadius: 20,
    paddingHorizontal: 14, paddingVertical: 7,
  },
  changePhotoBtnText: { color: '#fff', fontSize: 12, fontWeight: '700' },

  // Section header
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  sectionTitle:  { fontSize: 17, fontWeight: '700', color: '#fff', marginBottom: 12 },
  randomBtn:     { backgroundColor: '#1a1a3e', borderRadius: 20, paddingHorizontal: 14, paddingVertical: 6, borderWidth: 1, borderColor: '#2a2a5e' },
  randomBtnText: { color: '#9B5DEA', fontSize: 13, fontWeight: '700' },

  // Style picker button
  styleBtn: {
    backgroundColor: '#1a1a3e', borderRadius: 16, borderWidth: 2,
    borderColor: '#2a2a5e', marginBottom: 28,
  },
  styleBtnSelected: { borderColor: '#7C3AED' },
  styleBtnContent:  { flexDirection: 'row', alignItems: 'center', padding: 14, gap: 14 },
  styleEmojiBox: {
    width: 52, height: 52, borderRadius: 14,
    backgroundColor: '#2d1b69', alignItems: 'center', justifyContent: 'center',
  },
  styleEmojiBoxEmpty: { backgroundColor: '#1e1e4e' },
  styleEmoji:         { fontSize: 26 },
  styleInfo:          { flex: 1 },
  styleName:          { color: '#fff', fontWeight: '800', fontSize: 16, marginBottom: 3 },
  styleTagline:       { color: '#888', fontSize: 12 },
  stylePlaceholder:   { color: '#aaa', fontWeight: '700', fontSize: 15, marginBottom: 2 },
  stylePlaceholderSub:{ color: '#555', fontSize: 12 },
  styleChevron:       { color: '#555', fontSize: 24, fontWeight: '300' },

  // Room type chips — horizontal scroll
  chipScroll:        { marginBottom: 4 },
  chipScrollContent: { gap: 8, paddingBottom: 4 },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: '#1a1a3e', borderRadius: 20, borderWidth: 1.5,
    borderColor: '#2a2a5e', paddingHorizontal: 14, paddingVertical: 9,
  },
  chipActive:      { backgroundColor: '#2d1b69', borderColor: '#7C3AED' },
  chipEmoji:       { fontSize: 16 },
  chipLabel:       { color: '#aaa', fontSize: 13, fontWeight: '600' },
  chipLabelActive: { color: '#fff' },

  // Color palette
  paletteGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 4 },
  paletteCard: {
    width: '47%', backgroundColor: '#1a1a3e', borderRadius: 14, borderWidth: 1.5,
    borderColor: '#2a2a5e', paddingVertical: 12, paddingHorizontal: 12,
    alignItems: 'center', gap: 8,
  },
  paletteCardActive: { borderColor: '#7C3AED', backgroundColor: '#1e1040' },
  swatchRow:         { flexDirection: 'row', gap: 5 },
  swatch:            { width: 22, height: 22, borderRadius: 11, borderWidth: 1, borderColor: 'rgba(255,255,255,0.15)' },
  paletteLabel:      { color: '#aaa', fontSize: 12, fontWeight: '600' },
  paletteLabelActive:{ color: '#fff' },

  // Objets de référence
  refHint: { color: '#555', fontSize: 12, marginBottom: 14, marginTop: -4 },
  refCount: { color: '#7C3AED', fontSize: 13, fontWeight: '700', marginBottom: 12, marginTop: 24 },
  refCard: {
    backgroundColor: '#1a1a3e', borderRadius: 14, borderWidth: 1.5,
    borderColor: '#2a2a5e', flexDirection: 'row', alignItems: 'center',
    gap: 12, padding: 10, marginBottom: 10,
  },
  refImg:        { width: 68, height: 68, borderRadius: 10 },
  refInput: {
    flex: 1, backgroundColor: '#0f0f23', borderRadius: 10, paddingHorizontal: 12,
    paddingVertical: 10, color: '#fff', fontSize: 13, borderWidth: 1, borderColor: '#2a2a5e',
  },
  refRemove: {
    width: 28, height: 28, borderRadius: 14, backgroundColor: '#2a1a1a',
    alignItems: 'center', justifyContent: 'center',
  },
  refRemoveText: { color: '#ff6b6b', fontSize: 13, fontWeight: '800' },
  refAddBtn: {
    borderWidth: 1.5, borderColor: '#7C3AED', borderStyle: 'dashed',
    borderRadius: 14, paddingVertical: 14, alignItems: 'center', marginBottom: 4,
  },
  refAddBtnText: { color: '#7C3AED', fontWeight: '700', fontSize: 14 },

  // Note
  noteInput: {
    backgroundColor: '#1a1a3e', borderRadius: 14, borderWidth: 1.5,
    borderColor: '#2a2a5e', color: '#fff', fontSize: 14, lineHeight: 21,
    paddingHorizontal: 16, paddingVertical: 14, minHeight: 90, marginBottom: 4,
  },
  noteCount: { color: '#444', fontSize: 11, textAlign: 'right', marginBottom: 4 },

  // AI model
  aiRow: { flexDirection: 'row', gap: 10, marginBottom: 4 },
  aiCard: {
    flex: 1, backgroundColor: '#1a1a3e', borderRadius: 14, borderWidth: 1.5,
    borderColor: '#2a2a5e', alignItems: 'center', paddingVertical: 14, gap: 6,
  },
  aiCardActive: { borderColor: '#7C3AED', backgroundColor: '#1e1040' },
  aiEmoji:      { fontSize: 22 },
  aiLabel:      { color: '#aaa', fontSize: 12, fontWeight: '700' },
  aiLabelActive:{ color: '#fff' },
  recBadge:     { position: 'absolute', top: 6, right: 6, backgroundColor: '#7C3AED', borderRadius: 6, paddingHorizontal: 5, paddingVertical: 2 },
  recBadgeText: { color: '#fff', fontSize: 9, fontWeight: '800' },

  // Mode de prompt
  modeRow: { flexDirection: 'row', gap: 10, marginBottom: 4 },
  modeCard: {
    flex: 1, backgroundColor: '#1a1a3e', borderRadius: 16, borderWidth: 2,
    borderColor: '#2a2a5e', padding: 16, gap: 6,
  },
  modeCardActive: { borderColor: '#7C3AED', backgroundColor: '#1e1040' },
  modeCardPro:    { position: 'relative' },
  modeHeader:     { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  modeEmoji:      { fontSize: 28 },
  modeDot:        { width: 10, height: 10, borderRadius: 5, backgroundColor: '#7C3AED' },
  modeLabel:      { color: '#aaa', fontSize: 15, fontWeight: '800' },
  modeLabelActive:{ color: '#fff' },
  modeDesc:       { color: '#555', fontSize: 11, lineHeight: 16 },
  proBadge: {
    position: 'absolute', top: 10, right: 10,
    backgroundColor: '#F59E0B', borderRadius: 6,
    paddingHorizontal: 6, paddingVertical: 2,
  },
  proBadgeText: { color: '#000', fontSize: 9, fontWeight: '900' },
  modeDetail: {
    backgroundColor: '#1a1a3e', borderRadius: 12, borderLeftWidth: 3,
    borderLeftColor: '#7C3AED', padding: 12, marginBottom: 4,
  },
  modeDetailPro:   { borderLeftColor: '#F59E0B' },
  modeDetailChain: { borderLeftColor: '#06B6D4' },
  modeDetailText:  { color: '#888', fontSize: 12, lineHeight: 18 },

  // CHAIN card (pleine largeur)
  modeCardFull: {
    backgroundColor: '#1a1a3e', borderRadius: 16, borderWidth: 2,
    borderColor: '#1e3a4e', padding: 16, marginBottom: 4,
  },
  modeCardFullActive: { borderColor: '#06B6D4', backgroundColor: '#0d2535' },
  modeCardFullInner:  { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  chainLeft:          { paddingTop: 2 },
  chainBody:          { flex: 1, gap: 5 },
  chainTitleRow:      { flexDirection: 'row', alignItems: 'center', gap: 8 },
  chainBadge: {
    backgroundColor: '#06B6D4', borderRadius: 6,
    paddingHorizontal: 7, paddingVertical: 2,
  },
  chainBadgeText: { color: '#000', fontSize: 9, fontWeight: '900' },
  chainSteps: {
    flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap',
    gap: 4, marginTop: 4,
  },
  chainStep:  { color: '#06B6D4', fontSize: 11, fontWeight: '700' },
  chainArrow: { color: '#334155', fontSize: 11, fontWeight: '700' },
  chainDot:   { alignSelf: 'center', backgroundColor: '#06B6D4' },

  // ── Section ChatGPT ─────────────────────────────────────────────────────────
  chatgptSection: {
    backgroundColor: '#0d1f2d', borderRadius: 18, borderWidth: 1.5,
    borderColor: '#1a3a5c', padding: 16, marginBottom: 4, marginTop: 8,
  },
  chatgptHeader:    { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 14 },
  chatgptTitle:     { color: '#fff', fontSize: 15, fontWeight: '800', flex: 1 },
  chatgptBadge:     { backgroundColor: '#10a37f', borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3 },
  chatgptBadgeText: { color: '#fff', fontSize: 10, fontWeight: '900' },
  subSectionTitle:  { color: '#aaa', fontSize: 13, fontWeight: '700', marginBottom: 8 },
  mt12:             { marginTop: 12 },

  // Size chips
  sizeChip: {
    backgroundColor: '#1a2d3e', borderRadius: 12, borderWidth: 1.5,
    borderColor: '#1a3a5c', paddingHorizontal: 12, paddingVertical: 10,
    alignItems: 'center', minWidth: 80, position: 'relative',
  },
  sizeChipActive:      { borderColor: '#10a37f', backgroundColor: '#0d2d25' },
  sizeChipLabel:       { color: '#aaa', fontSize: 12, fontWeight: '700' },
  sizeChipLabelActive: { color: '#10a37f' },
  sizeChipSub:         { color: '#445', fontSize: 10, marginTop: 2 },
  sizeBadge: {
    position: 'absolute', top: -6, right: -6, backgroundColor: '#1E88E5',
    borderRadius: 5, paddingHorizontal: 5, paddingVertical: 1,
  },
  sizeBadgeExp:  { backgroundColor: '#FF6D00' },
  sizeBadgeText: { color: '#fff', fontSize: 8, fontWeight: '900' },

  // Quality cards
  qualityRow: { flexDirection: 'row', gap: 8, marginBottom: 4 },
  qualityCard: {
    flex: 1, backgroundColor: '#1a2d3e', borderRadius: 12, borderWidth: 1.5,
    borderColor: '#1a3a5c', paddingVertical: 10, alignItems: 'center', gap: 3,
  },
  qualityCardActive: { borderColor: '#10a37f', backgroundColor: '#0d2d25' },
  qualityLabel:      { color: '#aaa', fontSize: 12, fontWeight: '800' },
  qualityLabelActive:{ color: '#10a37f' },
  qualitySub:        { color: '#445', fontSize: 9 },

  // Format / compression / background cards
  formatRow: { flexDirection: 'row', gap: 8, marginBottom: 4 },
  formatCard: {
    flex: 1, backgroundColor: '#1a2d3e', borderRadius: 12, borderWidth: 1.5,
    borderColor: '#1a3a5c', paddingVertical: 10, alignItems: 'center', gap: 3,
  },
  formatCardActive: { borderColor: '#10a37f', backgroundColor: '#0d2d25' },
  formatLabel:      { color: '#aaa', fontSize: 12, fontWeight: '800' },
  formatLabelActive:{ color: '#10a37f' },
  formatSub:        { color: '#445', fontSize: 9 },
  bgRow:            { flexDirection: 'row', gap: 8, marginBottom: 4 },

  // ── Token widget ──────────────────────────────────────────────────────────
  tokenRow: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: '#0a1a2a',
    borderRadius: 12, padding: 12, marginBottom: 12, gap: 8,
  },
  tokenBalance:      { flex: 1, alignItems: 'center' },
  tokenBalanceLabel: { color: '#888', fontSize: 10, fontWeight: '700', marginBottom: 2 },
  tokenBalanceValue: { color: '#10a37f', fontSize: 16, fontWeight: '900' },
  tokenBalanceInsuffisant: { color: '#EF4444' },
  tokenArrow:        { alignItems: 'center' },
  tokenArrowText:    { color: '#555', fontSize: 18 },
  tokenCost:         { flex: 1, alignItems: 'center', backgroundColor: '#0d2d25', borderRadius: 8, padding: 6 },
  tokenCostInsuffisant: { backgroundColor: '#2d0d0d' },
  tokenCostLabel:    { color: '#888', fontSize: 10, fontWeight: '700', marginBottom: 2 },
  tokenCostValue:    { color: '#10a37f', fontSize: 16, fontWeight: '900' },
  tokenWarning: {
    backgroundColor: '#2d1010', borderRadius: 10, padding: 10, marginBottom: 12,
    borderWidth: 1, borderColor: '#EF4444',
  },
  tokenWarningText: { color: '#EF4444', fontSize: 12, fontWeight: '700', textAlign: 'center' },

  // Generate button
  // Produits en ligne
  productSection:   { marginTop: 28, backgroundColor: '#1a1a3e', borderRadius: 16, padding: 16, borderWidth: 1, borderColor: '#2a2a5e' },
  productHeader:    { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  productTitle:     { fontSize: 15, fontWeight: '700', color: '#fff' },
  productSubtitle:  { fontSize: 12, color: '#888', marginTop: 4 },
  brandRow:         { flexDirection: 'row', gap: 10, marginTop: 14 },
  brandChip:        { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, borderWidth: 1.5, borderColor: '#3a3a6e' },
  brandChipText:        { fontSize: 13, fontWeight: '700', color: '#aaa' },
  brandChipDisabled:    { opacity: 0.45, position: 'relative' },
  brandChipTextDisabled:{ fontSize: 13, fontWeight: '700', color: '#555' },
  brandChipSoonBadge:   { position: 'absolute', top: -8, right: -8, backgroundColor: '#444', borderRadius: 6, paddingHorizontal: 5, paddingVertical: 1 },
  brandChipSoonText:    { fontSize: 8, color: '#aaa', fontWeight: '700' },

  generateBtn:          { borderRadius: 16, overflow: 'hidden', marginTop: 32 },
  generateBtnDisabled:  { opacity: 0.45 },
  generateBtnGradient:  { paddingVertical: 20, alignItems: 'center' },
  generateBtnText:      { color: '#fff', fontSize: 17, fontWeight: '800', letterSpacing: 0.3 },
});
