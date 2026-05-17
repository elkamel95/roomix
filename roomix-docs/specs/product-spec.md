# Spécification Produit — HomeGPT AI

**Version:** 1.0  
**Date:** 2026-05-17  
**Statut:** MVP

---

## 1. Vision Produit

HomeGPT AI permet à tout utilisateur de **transformer visuellement une pièce** via IA générative :
prendre une photo → choisir un style → obtenir un rendu réaliste + liste d'achats.

**Proposition de valeur unique :**
> "Voir votre maison transformée en 30 secondes, avec les prix et les liens pour acheter."

---

## 2. Personas Cibles

| Persona | Besoin | Fréquence |
|---------|--------|-----------|
| Particulier (25-45 ans) | Redécorer sans archi | 1-2x/mois |
| Créateur TikTok/Instagram | Contenus avant/après viraux | 3-5x/semaine |
| Agent immobilier | Valoriser des biens | 5-10x/semaine |
| Décorateur freelance | Proposer des rendus rapides | Quotidien |
| Architecte junior | Maquettes rapides clients | Quotidien |

---

## 3. Fonctionnalités MVP (Version 1)

### 3.1 Authentification
- Inscription email + mot de passe
- Connexion
- OAuth Google
- Réinitialisation mot de passe

### 3.2 Upload Media
- Photo depuis appareil photo
- Photo depuis galerie
- Formats acceptés : JPG, PNG, WEBP
- Taille max : 10 MB
- Compression automatique côté client

### 3.3 Analyse IA de la Pièce
L'IA détecte automatiquement :
- Type de pièce (salon, chambre, bureau, cuisine)
- Objets présents (canapé, table, télé, bureau, fenêtre)
- Luminosité et ambiance
- Zones vides exploitables

### 3.4 Choix du Style Décoratif

| Style | Description |
|-------|-------------|
| **Scandinavian** | Bois clair, blanc, plantes, minimalisme |
| **Modern Luxury** | Marbre, or, velours, sophistiqué |
| **Minimalist** | Épuré, neutre, fonctionnel |
| **Japanese Zen** | Bois naturel, bambou, harmonie |
| **Arabic Modern** | Géométrie, dorures, riche |
| **Gamer Setup** | RGB, noir, écrans multiples, LED |
| **Cozy** | Chaud, textiles doux, lumière tamisée |
| **Developer Setup** | Bureau ergonomique, monitoring, sobre |

### 3.5 Génération IA
- Transformation complète de la pièce
- Conservation de la structure (murs, fenêtres, proportions)
- Durée estimée : 15-45 secondes
- Queue asynchrone avec statut temps réel
- 3 générations/jour en version gratuite

### 3.6 Affichage Avant/Après
- Slider comparatif interactif
- Image originale vs image générée
- Partage direct (Instagram, WhatsApp, TikTok)

### 3.7 Sauvegarde Projets
- Historique des générations
- Renommer un projet
- Supprimer
- Export image HD

---

## 4. Fonctionnalités V2 (Post-MVP)

### 4.1 Shopping Intégré
- Détection des produits utilisés dans la génération
- Correspondance produits réels : IKEA, Amazon, Leroy Merlin, Action
- Prix estimé total
- Liens d'achat affiliés
- Mode budget : "Redécore pour 300€ max"

### 4.2 Génération Vidéo IA
- Input : vidéo courte (5-15 sec)
- Output : vidéo décorée IA
- Viral pour Reels/TikTok

### 4.3 Réalité Augmentée (AR)
- Placement de meubles en temps réel
- ARKit (iOS) / ARCore (Android)
- Visualisation sans générer

### 4.4 Multi-pièces
- Décorer plusieurs pièces d'un coup
- Cohérence stylistique

---

## 5. Modèle de Monétisation

### Plan Gratuit
- 3 générations par jour
- Qualité standard (512x512)
- Watermark HomeGPT

### Plan Premium — 9,99 €/mois
- Générations illimitées
- Qualité HD (1024x1024)
- Export 4K
- Sans watermark
- Shopping intégré
- Historique illimité
- Accès styles exclusifs

### Plan Pro — 24,99 €/mois (V2)
- Tout Premium
- Génération vidéo IA
- AR avancée
- API access
- Support prioritaire

---

## 6. Métriques de Succès

| Métrique | Objectif M3 | Objectif M6 |
|----------|-------------|-------------|
| Utilisateurs inscrits | 1 000 | 10 000 |
| Générations/jour | 500 | 5 000 |
| Taux conversion gratuit→premium | 5% | 8% |
| Note App Store/Play Store | 4.5+ | 4.7+ |
| Partages sociaux | 200/semaine | 2000/semaine |

---

## 7. Contraintes Techniques

- Temps de génération < 45 secondes
- Disponibilité API : 99.5%
- Compression image avant upload (< 2MB envoyé)
- RGPD : photos supprimées après 30 jours (gratuit) / 1 an (premium)
- Rate limiting : 10 req/min par utilisateur
