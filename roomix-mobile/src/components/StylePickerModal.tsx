import React, { useRef, useCallback } from 'react';
import {
  Modal, View, Text, TouchableOpacity, ScrollView,
  StyleSheet, useWindowDimensions, Animated, StatusBar,
  SafeAreaView,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { DecorationStyle, STYLE_CARDS, StyleCard } from '../types';

interface Props {
  visible: boolean;
  selected: DecorationStyle | null;
  onSelect: (style: DecorationStyle) => void;
  onClose: () => void;
}

const CARD_HEIGHT = 190;
const CARD_GAP    = 10;
const H_PADDING   = 16;

// ── Card individuelle ─────────────────────────────────────────────────────────

function StyleCardItem({
  card,
  isSelected,
  onPress,
  cardWidth,
}: {
  card: StyleCard;
  isSelected: boolean;
  onPress: () => void;
  cardWidth: number;
}) {
  const scale = useRef(new Animated.Value(1)).current;

  const handlePressIn = () =>
    Animated.spring(scale, { toValue: 0.95, useNativeDriver: true, speed: 60, bounciness: 0 }).start();

  const handlePressOut = () =>
    Animated.spring(scale, { toValue: 1, useNativeDriver: true, speed: 20, bounciness: 10 }).start();

  return (
    <Animated.View style={{ transform: [{ scale }], width: cardWidth }}>
      <TouchableOpacity
        onPress={onPress}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        activeOpacity={1}
      >
        <LinearGradient
          colors={card.gradient}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={[
            styles.card,
            { height: CARD_HEIGHT },
            isSelected && styles.cardSelected,
          ]}
        >
          {/* Overlay sombre pour profondeur */}
          <View style={styles.cardOverlay} />

          {/* Contenu */}
          <View style={styles.cardContent}>
            <Text style={styles.cardEmoji}>{card.emoji}</Text>
            <Text style={styles.cardName}>{card.label}</Text>
            <Text style={styles.cardTagline} numberOfLines={2}>{card.tagline}</Text>
          </View>

          {/* Badge sélectionné */}
          {isSelected && (
            <View style={styles.checkBadge}>
              <Text style={styles.checkBadgeText}>✓</Text>
            </View>
          )}
        </LinearGradient>
      </TouchableOpacity>
    </Animated.View>
  );
}

// ── Modal principal ───────────────────────────────────────────────────────────

export default function StylePickerModal({ visible, selected, onSelect, onClose }: Props) {
  const { width } = useWindowDimensions();
  const cardWidth = Math.floor((width - H_PADDING * 2 - CARD_GAP) / 2);

  // Construction du tableau en 2 colonnes
  const rows: [StyleCard, StyleCard | null][] = [];
  for (let i = 0; i < STYLE_CARDS.length; i += 2) {
    rows.push([STYLE_CARDS[i], STYLE_CARDS[i + 1] ?? null]);
  }

  const handleSelect = useCallback((key: DecorationStyle) => {
    onSelect(key);
  }, [onSelect]);

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onClose}
    >
      <StatusBar barStyle="light-content" backgroundColor="#0f0f23" />
      <View style={styles.container}>

        {/* ── Header ─── */}
        <SafeAreaView>
          <View style={styles.header}>
            <TouchableOpacity style={styles.closeBtn} onPress={onClose}>
              <Text style={styles.closeBtnText}>✕</Text>
            </TouchableOpacity>
            <View style={styles.headerTextBlock}>
              <Text style={styles.headerTitle}>Choisissez votre style</Text>
              <Text style={styles.headerSubtitle}>Dans quel univers voulez-vous vivre ?</Text>
            </View>
            {/* Spacer pour centrer le texte */}
            <View style={{ width: 40 }} />
          </View>
        </SafeAreaView>

        {/* ── Grille ─── */}
        <ScrollView
          contentContainerStyle={[styles.grid, { paddingHorizontal: H_PADDING }]}
          showsVerticalScrollIndicator={false}
        >
          {rows.map(([left, right], rowIndex) => (
            <View key={rowIndex} style={styles.row}>
              <StyleCardItem
                card={left}
                isSelected={selected === left.key}
                onPress={() => handleSelect(left.key)}
                cardWidth={cardWidth}
              />
              {right ? (
                <StyleCardItem
                  card={right}
                  isSelected={selected === right.key}
                  onPress={() => handleSelect(right.key)}
                  cardWidth={cardWidth}
                />
              ) : (
                <View style={{ width: cardWidth }} />
              )}
            </View>
          ))}
          <View style={{ height: 100 }} />
        </ScrollView>

        {/* ── Bouton Appliquer ─── */}
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.applyBtn, !selected && styles.applyBtnDisabled]}
            onPress={selected ? onClose : undefined}
            disabled={!selected}
          >
            <LinearGradient
              colors={selected ? ['#9B5DEA', '#7C3AED'] : ['#2a2a5e', '#2a2a5e']}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 0 }}
              style={styles.applyBtnGradient}
            >
              <Text style={styles.applyBtnText}>
                {selected
                  ? `✓ Appliquer — ${STYLE_CARDS.find(c => c.key === selected)?.label}`
                  : 'Sélectionnez un style'}
              </Text>
            </LinearGradient>
          </TouchableOpacity>
        </View>

      </View>
    </Modal>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f0f23',
  },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 20,
  },
  closeBtn: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#1a1a3e',
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  headerTextBlock: {
    flex: 1,
    alignItems: 'center',
  },
  headerTitle: {
    color: '#fff',
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  headerSubtitle: {
    color: '#888',
    fontSize: 12,
    marginTop: 2,
  },

  // Grid
  grid: {
    paddingTop: 8,
    gap: CARD_GAP,
  },
  row: {
    flexDirection: 'row',
    gap: CARD_GAP,
  },

  // Card
  card: {
    borderRadius: 18,
    overflow: 'hidden',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  cardSelected: {
    borderColor: '#9B5DEA',
    shadowColor: '#9B5DEA',
    shadowOpacity: 0.6,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 0 },
    elevation: 8,
  },
  cardOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.18)',
  },
  cardContent: {
    flex: 1,
    padding: 16,
    justifyContent: 'flex-end',
  },
  cardEmoji: {
    fontSize: 40,
    marginBottom: 8,
  },
  cardName: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '800',
    letterSpacing: 0.2,
    marginBottom: 4,
    textShadowColor: 'rgba(0,0,0,0.5)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 4,
  },
  cardTagline: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: 11,
    fontWeight: '500',
    lineHeight: 15,
    textShadowColor: 'rgba(0,0,0,0.4)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  checkBadge: {
    position: 'absolute',
    top: 12,
    right: 12,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#9B5DEA',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkBadgeText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '800',
  },

  // Footer
  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 20,
    paddingBottom: 36,
    paddingTop: 12,
    backgroundColor: 'rgba(15,15,35,0.95)',
  },
  applyBtn: {
    borderRadius: 16,
    overflow: 'hidden',
  },
  applyBtnDisabled: {
    opacity: 0.5,
  },
  applyBtnGradient: {
    paddingVertical: 18,
    alignItems: 'center',
  },
  applyBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
});
