import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  ActivityIndicator, Alert, ScrollView,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as WebBrowser from 'expo-web-browser';
import { api } from '../../services/api';
import { useAuthStore } from '../../store/slices/authStore';

// ── Types ─────────────────────────────────────────────────────────────────────

interface TokenPackData {
  id:             string;   // STARTER | STANDARD | PRO
  label:          string;
  tokens:         number;
  price:          number;   // centimes EUR
  priceFormatted: string;   // "2,99 €"
  tagline:        string;
  description:    string;
  savings:        number;   // % économie vs Starter (0 pour Starter)
  bestValue:      boolean;
}

// ── Couleurs par pack ─────────────────────────────────────────────────────────

const PACK_COLORS: Record<string, { primary: string; bg: string; border: string }> = {
  STARTER:  { primary: '#60A5FA', bg: '#0d1f3c', border: '#1e3a6e' },
  STANDARD: { primary: '#A78BFA', bg: '#1a0d3c', border: '#3a1e6e' },
  PRO:      { primary: '#F59E0B', bg: '#2d1a00', border: '#6e3e00' },
};

const PACK_EMOJI: Record<string, string> = {
  STARTER:  '🌱',
  STANDARD: '⚡',
  PRO:      '🔥',
};

// ── Composant ─────────────────────────────────────────────────────────────────

export default function TokenPacksScreen() {
  const router       = useRouter();
  const { user, refreshUser } = useAuthStore();
  const queryClient  = useQueryClient();
  const [buying, setBuying] = useState<string | null>(null);

  const { data: packs, isLoading } = useQuery<TokenPackData[]>({
    queryKey: ['token-packs'],
    queryFn:  async () => (await api.get('/payments/packs')).data,
    staleTime: 60_000,
  });

  const handleBuy = async (pack: TokenPackData) => {
    setBuying(pack.id);
    try {
      const { data } = await api.post<{ checkoutUrl: string }>('/payments/checkout', { pack: pack.id });
      const result   = await WebBrowser.openAuthSessionAsync(
        data.checkoutUrl,
        'ROOMIX://payment',
      );

      if (result.type === 'success') {
        // Paiement complété — rafraîchir le solde après 2s (le webhook peut prendre un instant)
        setTimeout(async () => {
          await refreshUser();
          queryClient.invalidateQueries({ queryKey: ['quota'] });
          Alert.alert(
            '✅ Paiement réussi !',
            `${pack.tokens.toLocaleString('fr-FR')} tokens ont été ajoutés à votre compte.`,
            [{ text: 'Parfait !', onPress: () => router.back() }]
          );
        }, 2000);
      }
    } catch (err: any) {
      Alert.alert(
        'Erreur',
        err?.response?.data?.error ?? 'Impossible de créer la session de paiement.',
      );
    } finally {
      setBuying(null);
    }
  };

  return (
    <ScrollView style={s.container} contentContainerStyle={s.content}>
      {/* Header */}
      <TouchableOpacity style={s.backBtn} onPress={() => router.back()}>
        <Text style={s.backText}>← Retour</Text>
      </TouchableOpacity>

      <Text style={s.title}>Acheter des tokens</Text>
      <Text style={s.subtitle}>
        1 token = $0,001 — chaque génération coûte selon la qualité et la taille choisies
      </Text>

      {/* Solde actuel */}
      <View style={s.balanceCard}>
        <Text style={s.balanceLabel}>💰 Solde actuel</Text>
        <Text style={s.balanceValue}>{(user?.tokenBalance ?? 0).toLocaleString('fr-FR')} tokens</Text>
      </View>

      {/* Packs */}
      {isLoading ? (
        <ActivityIndicator color="#7C3AED" size="large" style={{ marginTop: 40 }} />
      ) : (
        packs?.map((pack) => {
          const colors  = PACK_COLORS[pack.id] ?? PACK_COLORS.STARTER;
          const isBuying = buying === pack.id;

          return (
            <View
              key={pack.id}
              style={[s.packCard, { backgroundColor: colors.bg, borderColor: colors.border },
                      pack.bestValue && s.packCardBest]}
            >
              {pack.bestValue && (
                <View style={[s.bestBadge, { backgroundColor: colors.primary }]}>
                  <Text style={s.bestBadgeText}>MEILLEUR PRIX</Text>
                </View>
              )}
              {pack.savings > 0 && (
                <View style={[s.savingsBadge, { backgroundColor: colors.primary + '33', borderColor: colors.primary }]}>
                  <Text style={[s.savingsText, { color: colors.primary }]}>-{pack.savings}%</Text>
                </View>
              )}

              <View style={s.packTop}>
                <Text style={s.packEmoji}>{PACK_EMOJI[pack.id]}</Text>
                <View style={s.packInfo}>
                  <Text style={[s.packLabel, { color: colors.primary }]}>{pack.label}</Text>
                  <Text style={s.packTagline}>{pack.tagline}</Text>
                </View>
                <View style={s.packPriceBox}>
                  <Text style={[s.packPrice, { color: colors.primary }]}>{pack.priceFormatted}</Text>
                </View>
              </View>

              <View style={s.packMid}>
                <Text style={[s.packTokens, { color: colors.primary }]}>
                  {pack.tokens.toLocaleString('fr-FR')}
                </Text>
                <Text style={s.packTokensLabel}> tokens</Text>
              </View>

              <Text style={s.packDesc}>{pack.description}</Text>

              <TouchableOpacity
                style={[s.buyBtn, { backgroundColor: colors.primary }, isBuying && s.buyBtnDisabled]}
                onPress={() => handleBuy(pack)}
                disabled={isBuying || buying !== null}
              >
                {isBuying ? (
                  <ActivityIndicator color="#fff" size="small" />
                ) : (
                  <Text style={s.buyBtnText}>Acheter — {pack.priceFormatted}</Text>
                )}
              </TouchableOpacity>
            </View>
          );
        })
      )}

      {/* Info bas de page */}
      <View style={s.footer}>
        <Text style={s.footerText}>
          🔒 Paiement sécurisé par Stripe.{'\n'}
          Les tokens sont crédités instantanément après confirmation du paiement.{'\n'}
          Non remboursables.
        </Text>
      </View>
    </ScrollView>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  content:   { padding: 20, paddingTop: 60, paddingBottom: 40 },

  backBtn:  { marginBottom: 8 },
  backText: { color: '#7C3AED', fontSize: 16, fontWeight: '600' },

  title:    { fontSize: 26, fontWeight: '800', color: '#fff', marginBottom: 6 },
  subtitle: { fontSize: 13, color: '#888', lineHeight: 18, marginBottom: 20 },

  balanceCard: {
    backgroundColor: '#1a1a3e', borderRadius: 14, padding: 16,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    marginBottom: 24, borderWidth: 1, borderColor: '#2a2a6e',
  },
  balanceLabel: { color: '#aaa', fontSize: 14, fontWeight: '600' },
  balanceValue: { color: '#A78BFA', fontSize: 20, fontWeight: '900' },

  packCard: {
    borderRadius: 20, borderWidth: 1.5, padding: 20,
    marginBottom: 16, position: 'relative', overflow: 'hidden',
  },
  packCardBest: { borderWidth: 2 },

  bestBadge: {
    position: 'absolute', top: 0, right: 0,
    paddingHorizontal: 12, paddingVertical: 4,
    borderBottomLeftRadius: 12,
  },
  bestBadgeText: { color: '#fff', fontSize: 10, fontWeight: '900', letterSpacing: 0.5 },

  savingsBadge: {
    alignSelf: 'flex-start', borderRadius: 8, borderWidth: 1,
    paddingHorizontal: 8, paddingVertical: 2, marginBottom: 10,
  },
  savingsText: { fontSize: 12, fontWeight: '900' },

  packTop:      { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 12 },
  packEmoji:    { fontSize: 32 },
  packInfo:     { flex: 1 },
  packLabel:    { fontSize: 18, fontWeight: '800' },
  packTagline:  { color: '#aaa', fontSize: 12, marginTop: 2 },
  packPriceBox: { alignItems: 'flex-end' },
  packPrice:    { fontSize: 22, fontWeight: '900' },

  packMid:       { flexDirection: 'row', alignItems: 'baseline', marginBottom: 6 },
  packTokens:    { fontSize: 36, fontWeight: '900' },
  packTokensLabel: { color: '#aaa', fontSize: 16, fontWeight: '600' },

  packDesc: { color: '#666', fontSize: 12, marginBottom: 16 },

  buyBtn:         { borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  buyBtnDisabled: { opacity: 0.5 },
  buyBtnText:     { color: '#fff', fontSize: 15, fontWeight: '800' },

  footer: {
    marginTop: 16, backgroundColor: '#1a1a2e', borderRadius: 12, padding: 16,
  },
  footerText: { color: '#666', fontSize: 12, textAlign: 'center', lineHeight: 18 },
});
