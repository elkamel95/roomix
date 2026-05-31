package com.roomix.api.model.enums;

/**
 * Mode de construction du prompt envoyé aux modèles IA.
 *
 * CREATIVE : prompt riche et détaillé par style — liberté artistique maximale.
 * PRO      : system prompt "interior designer" strict — architecture préservée, résultat réaliste.
 * CHAIN    : chaîne de pensée — le modèle vision analyse la pièce, élabore une stratégie
 *            de rénovation, puis génère un prompt optimisé utilisé pour la génération finale.
 */
public enum PromptMode {
    CREATIVE,
    PRO,
    CHAIN
}
