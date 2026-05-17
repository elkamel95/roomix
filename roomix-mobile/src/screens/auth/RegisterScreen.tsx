import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity,
  StyleSheet, KeyboardAvoidingView, Platform,
  ActivityIndicator, Alert, ScrollView,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuthStore } from '../../store/slices/authStore';

export default function RegisterScreen() {
  const router = useRouter();
  const { register, isLoading } = useAuthStore();
  const [firstName, setFirstName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');

  const handleRegister = async () => {
    if (!firstName.trim() || !email.trim() || !password.trim()) {
      Alert.alert('Erreur', 'Veuillez remplir tous les champs');
      return;
    }
    if (password !== confirm) {
      Alert.alert('Erreur', 'Les mots de passe ne correspondent pas');
      return;
    }
    if (password.length < 8) {
      Alert.alert('Erreur', 'Le mot de passe doit contenir au moins 8 caractères');
      return;
    }
    try {
      await register(email.trim(), password, firstName.trim());
      router.replace('/(tabs)');
    } catch (e: any) {
      const msg = e?.response?.data?.detail ?? 'Inscription impossible';
      Alert.alert('Erreur', msg);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <View style={styles.header}>
          <Text style={styles.logo}>🏠</Text>
          <Text style={styles.title}>Créer un compte</Text>
          <Text style={styles.subtitle}>Commencez à décorer avec l'IA</Text>
        </View>

        <View style={styles.form}>
          <TextInput style={styles.input} placeholder="Prénom" placeholderTextColor="#999"
            value={firstName} onChangeText={setFirstName} />
          <TextInput style={styles.input} placeholder="Email" placeholderTextColor="#999"
            keyboardType="email-address" autoCapitalize="none"
            value={email} onChangeText={setEmail} />
          <TextInput style={styles.input} placeholder="Mot de passe (8 car. min)" placeholderTextColor="#999"
            secureTextEntry value={password} onChangeText={setPassword} />
          <TextInput style={styles.input} placeholder="Confirmer le mot de passe" placeholderTextColor="#999"
            secureTextEntry value={confirm} onChangeText={setConfirm} />

          <TouchableOpacity
            style={[styles.button, isLoading && styles.buttonDisabled]}
            onPress={handleRegister}
            disabled={isLoading}
          >
            {isLoading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Créer mon compte</Text>}
          </TouchableOpacity>

          <TouchableOpacity style={styles.linkButton} onPress={() => router.back()}>
            <Text style={styles.linkText}>
              Déjà un compte ? <Text style={styles.linkHighlight}>Se connecter</Text>
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  scroll: { flexGrow: 1, justifyContent: 'center', paddingHorizontal: 24, paddingVertical: 40 },
  header: { alignItems: 'center', marginBottom: 40 },
  logo: { fontSize: 56, marginBottom: 12 },
  title: { fontSize: 28, fontWeight: '800', color: '#fff' },
  subtitle: { fontSize: 14, color: '#888', marginTop: 6 },
  form: { gap: 14 },
  input: {
    backgroundColor: '#1a1a3e', borderRadius: 12, paddingHorizontal: 16,
    paddingVertical: 14, fontSize: 16, color: '#fff', borderWidth: 1, borderColor: '#2a2a5e',
  },
  button: { backgroundColor: '#7C3AED', borderRadius: 12, paddingVertical: 16, alignItems: 'center', marginTop: 8 },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  linkButton: { alignItems: 'center', paddingVertical: 8 },
  linkText: { color: '#888', fontSize: 14 },
  linkHighlight: { color: '#7C3AED', fontWeight: '700' },
});
