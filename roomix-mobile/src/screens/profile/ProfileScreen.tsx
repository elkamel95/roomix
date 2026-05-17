import React from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, Alert, ScrollView,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../../store/slices/authStore';
import { api } from '../../services/api';
import { Quota } from '../../types';

export default function ProfileScreen() {
  const router = useRouter();
  const { user, logout } = useAuthStore();

  const { data: quota } = useQuery<Quota>({
    queryKey: ['quota'],
    queryFn: async () => {
      const { data } = await api.get('/users/me/quota');
      return data;
    },
  });

  const handleLogout = () => {
    Alert.alert('Déconnexion', 'Êtes-vous sûr de vouloir vous déconnecter ?', [
      { text: 'Annuler', style: 'cancel' },
      {
        text: 'Déconnexion', style: 'destructive',
        onPress: async () => {
          await logout();
          router.replace('/login');
        },
      },
    ]);
  };

  const planColors: Record<string, string> = {
    FREE: '#888', PREMIUM: '#F59E0B', PRO: '#7C3AED',
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Mon Profil</Text>

      {/* User card */}
      <View style={styles.userCard}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>
            {user?.firstName?.charAt(0).toUpperCase() ?? '?'}
          </Text>
        </View>
        <View style={styles.userInfo}>
          <Text style={styles.userName}>{user?.firstName} {user?.lastName}</Text>
          <Text style={styles.userEmail}>{user?.email}</Text>
        </View>
        <View style={[styles.planBadge, { backgroundColor: planColors[user?.plan ?? 'FREE'] + '22', borderColor: planColors[user?.plan ?? 'FREE'] }]}>
          <Text style={[styles.planText, { color: planColors[user?.plan ?? 'FREE'] }]}>
            {user?.plan}
          </Text>
        </View>
      </View>

      {/* Quota */}
      {quota && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Générations aujourd'hui</Text>
          <View style={styles.quotaCard}>
            <View style={styles.quotaNumbers}>
              <Text style={styles.quotaUsed}>{quota.dailyUsed}</Text>
              <Text style={styles.quotaSeparator}>/</Text>
              <Text style={styles.quotaLimit}>{quota.dailyLimit === -1 ? '∞' : quota.dailyLimit}</Text>
            </View>
            <Text style={styles.quotaLabel}>
              {quota.remaining === 2147483647 ? 'Illimité' : `${quota.remaining} restantes`}
            </Text>
          </View>
        </View>
      )}

      {/* Upgrade */}
      {user?.plan === 'FREE' && (
        <TouchableOpacity style={styles.upgradeCard}>
          <Text style={styles.upgradeEmoji}>✨</Text>
          <View style={styles.upgradeInfo}>
            <Text style={styles.upgradeTitle}>Passer à Premium</Text>
            <Text style={styles.upgradeSubtitle}>Illimité · HD · Sans watermark</Text>
          </View>
          <Text style={styles.upgradePrice}>9,99 €/mois</Text>
        </TouchableOpacity>
      )}

      {/* Settings */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Compte</Text>
        <View style={styles.settingsCard}>
          <TouchableOpacity style={styles.settingsItem}>
            <Text style={styles.settingsItemText}>📧 Changer l'email</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.settingsItem}>
            <Text style={styles.settingsItemText}>🔒 Changer le mot de passe</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.settingsItem}>
            <Text style={styles.settingsItemText}>📜 Conditions d'utilisation</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
          <View style={styles.divider} />
          <TouchableOpacity style={styles.settingsItem}>
            <Text style={styles.settingsItemText}>🔐 Politique de confidentialité</Text>
            <Text style={styles.chevron}>›</Text>
          </TouchableOpacity>
        </View>
      </View>

      <TouchableOpacity style={styles.logoutButton} onPress={handleLogout}>
        <Text style={styles.logoutText}>Se déconnecter</Text>
      </TouchableOpacity>

      <Text style={styles.version}>HomeGPT AI v1.0.0</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  content: { padding: 20, paddingTop: 60, paddingBottom: 40 },
  title: { fontSize: 26, fontWeight: '800', color: '#fff', marginBottom: 24 },
  userCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#1a1a3e', borderRadius: 16, padding: 16, marginBottom: 20, gap: 12 },
  avatar: { width: 56, height: 56, borderRadius: 28, backgroundColor: '#7C3AED', justifyContent: 'center', alignItems: 'center' },
  avatarText: { color: '#fff', fontSize: 22, fontWeight: '800' },
  userInfo: { flex: 1 },
  userName: { color: '#fff', fontSize: 16, fontWeight: '700' },
  userEmail: { color: '#888', fontSize: 13, marginTop: 2 },
  planBadge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12, borderWidth: 1 },
  planText: { fontSize: 12, fontWeight: '700' },
  section: { marginBottom: 20 },
  sectionTitle: { fontSize: 15, fontWeight: '700', color: '#888', marginBottom: 10, textTransform: 'uppercase', letterSpacing: 0.5 },
  quotaCard: { backgroundColor: '#1a1a3e', borderRadius: 16, padding: 20, alignItems: 'center' },
  quotaNumbers: { flexDirection: 'row', alignItems: 'baseline', gap: 4 },
  quotaUsed: { color: '#7C3AED', fontSize: 48, fontWeight: '800' },
  quotaSeparator: { color: '#555', fontSize: 32 },
  quotaLimit: { color: '#555', fontSize: 32, fontWeight: '600' },
  quotaLabel: { color: '#888', fontSize: 14, marginTop: 4 },
  upgradeCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#2d1b69', borderRadius: 16, padding: 16, marginBottom: 20, gap: 12, borderWidth: 1, borderColor: '#7C3AED' },
  upgradeEmoji: { fontSize: 32 },
  upgradeInfo: { flex: 1 },
  upgradeTitle: { color: '#fff', fontWeight: '700', fontSize: 16 },
  upgradeSubtitle: { color: '#aaa', fontSize: 12, marginTop: 2 },
  upgradePrice: { color: '#7C3AED', fontWeight: '800', fontSize: 15 },
  settingsCard: { backgroundColor: '#1a1a3e', borderRadius: 16, overflow: 'hidden' },
  settingsItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16 },
  settingsItemText: { color: '#ccc', fontSize: 15 },
  chevron: { color: '#555', fontSize: 20 },
  divider: { height: 1, backgroundColor: '#2a2a5e', marginHorizontal: 16 },
  logoutButton: { backgroundColor: '#2a1a1a', borderRadius: 12, paddingVertical: 14, alignItems: 'center', marginTop: 8, borderWidth: 1, borderColor: '#EF4444' },
  logoutText: { color: '#EF4444', fontWeight: '700', fontSize: 16 },
  version: { color: '#444', fontSize: 12, textAlign: 'center', marginTop: 20 },
});
