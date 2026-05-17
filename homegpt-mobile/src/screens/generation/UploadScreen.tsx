import React, { useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet,
  Image, Alert, ActivityIndicator, ScrollView,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import { useRouter } from 'expo-router';
import { STYLE_OPTIONS, StyleOption, DecorationStyle } from '../../types';
import { projectService } from '../../services/projectService';
import { useProjectStore } from '../../store/slices/projectStore';

export default function UploadScreen() {
  const router = useRouter();
  const { addProject } = useProjectStore();
  const [imageUri, setImageUri] = useState<string | null>(null);
  const [selectedStyle, setSelectedStyle] = useState<DecorationStyle | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  const pickImage = async (fromCamera: boolean) => {
    const permission = fromCamera
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();

    if (!permission.granted) {
      Alert.alert('Permission refusée', 'Activez l\'accès dans les paramètres');
      return;
    }

    const result = fromCamera
      ? await ImagePicker.launchCameraAsync({ quality: 0.8, allowsEditing: true })
      : await ImagePicker.launchImageLibraryAsync({ quality: 0.8, allowsEditing: true, mediaTypes: ImagePicker.MediaTypeOptions.Images });

    if (!result.canceled && result.assets[0]) {
      const compressed = await ImageManipulator.manipulateAsync(
        result.assets[0].uri,
        [{ resize: { width: 1024 } }],
        { compress: 0.85, format: ImageManipulator.SaveFormat.JPEG }
      );
      setImageUri(compressed.uri);
    }
  };

  const handleGenerate = async () => {
    if (!imageUri || !selectedStyle) {
      Alert.alert('Requis', 'Choisissez une photo et un style');
      return;
    }

    setIsGenerating(true);
    try {
      const project = await projectService.createProject(imageUri, selectedStyle);
      addProject(project);
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

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Nouvelle Décoration</Text>
      <Text style={styles.subtitle}>Photographiez votre pièce, choisissez un style</Text>

      {/* Zone upload */}
      <View style={styles.uploadSection}>
        {imageUri ? (
          <TouchableOpacity onPress={() => setImageUri(null)}>
            <Image source={{ uri: imageUri }} style={styles.preview} />
            <Text style={styles.changePhoto}>Changer la photo</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.uploadButtons}>
            <TouchableOpacity style={styles.uploadBtn} onPress={() => pickImage(true)}>
              <Text style={styles.uploadBtnEmoji}>📷</Text>
              <Text style={styles.uploadBtnText}>Prendre une photo</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.uploadBtn} onPress={() => pickImage(false)}>
              <Text style={styles.uploadBtnEmoji}>🖼️</Text>
              <Text style={styles.uploadBtnText}>Depuis la galerie</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* Sélection style */}
      <Text style={styles.sectionTitle}>Choisissez un style</Text>
      <View style={styles.stylesGrid}>
        {STYLE_OPTIONS.map((style: StyleOption) => (
          <TouchableOpacity
            key={style.key}
            style={[
              styles.styleCard,
              selectedStyle === style.key && styles.styleCardSelected,
            ]}
            onPress={() => setSelectedStyle(style.key)}
          >
            <Text style={styles.styleEmoji}>{style.emoji}</Text>
            <Text style={styles.styleLabel}>{style.label}</Text>
            <Text style={styles.styleDesc}>{style.description}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Bouton générer */}
      <TouchableOpacity
        style={[
          styles.generateButton,
          (!imageUri || !selectedStyle || isGenerating) && styles.generateButtonDisabled,
        ]}
        onPress={handleGenerate}
        disabled={!imageUri || !selectedStyle || isGenerating}
      >
        {isGenerating ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.generateButtonText}>✨ Générer avec l'IA</Text>
        )}
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  content: { padding: 20, paddingTop: 60 },
  title: { fontSize: 26, fontWeight: '800', color: '#fff', marginBottom: 6 },
  subtitle: { fontSize: 14, color: '#888', marginBottom: 24 },
  uploadSection: { marginBottom: 24 },
  uploadButtons: { flexDirection: 'row', gap: 12 },
  uploadBtn: {
    flex: 1, backgroundColor: '#1a1a3e', borderRadius: 16, padding: 20,
    alignItems: 'center', borderWidth: 2, borderColor: '#2a2a5e', borderStyle: 'dashed',
  },
  uploadBtnEmoji: { fontSize: 36, marginBottom: 8 },
  uploadBtnText: { color: '#ccc', fontSize: 14, textAlign: 'center', fontWeight: '600' },
  preview: { width: '100%', height: 220, borderRadius: 16, marginBottom: 8 },
  changePhoto: { color: '#7C3AED', textAlign: 'center', fontWeight: '600' },
  sectionTitle: { fontSize: 18, fontWeight: '700', color: '#fff', marginBottom: 14 },
  stylesGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 32 },
  styleCard: {
    width: '47%', backgroundColor: '#1a1a3e', borderRadius: 12,
    padding: 14, borderWidth: 2, borderColor: '#2a2a5e',
  },
  styleCardSelected: { borderColor: '#7C3AED', backgroundColor: '#2d1b69' },
  styleEmoji: { fontSize: 28, marginBottom: 6 },
  styleLabel: { color: '#fff', fontWeight: '700', fontSize: 14, marginBottom: 2 },
  styleDesc: { color: '#888', fontSize: 11 },
  generateButton: {
    backgroundColor: '#7C3AED', borderRadius: 14, paddingVertical: 18,
    alignItems: 'center', marginBottom: 40,
  },
  generateButtonDisabled: { opacity: 0.4 },
  generateButtonText: { color: '#fff', fontSize: 18, fontWeight: '800' },
});
