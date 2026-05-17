# ROOMIX — Mobile

Application React Native + Expo pour la décoration d'intérieur par IA.

## Installation

```bash
npm install
```

## Démarrage

```bash
# Dev
npx expo start

# Android
npx expo start --android

# iOS
npx expo start --ios
```

## Configuration

Copier `.env.example` en `.env` et renseigner :
```
EXPO_PUBLIC_API_URL=https://api.roomix.ai/api/v1
```

## Structure

```
src/
├── screens/
│   ├── auth/          # LoginScreen, RegisterScreen
│   ├── home/          # HomeScreen (liste projets)
│   ├── generation/    # UploadScreen, ResultScreen
│   └── profile/       # ProfileScreen
├── components/
│   ├── common/        # Composants réutilisables
│   └── generation/    # ProjectCard, StyleSelector
├── services/          # api.ts, authService, projectService
├── store/slices/      # Zustand : authStore, projectStore
├── hooks/             # useGenerationPolling
├── types/             # Types TypeScript
└── utils/             # formatters
```

## Build Production

```bash
# Installer EAS CLI
npm install -g eas-cli

# Configurer EAS
eas build:configure

# Build Android
eas build --platform android

# Build iOS
eas build --platform ios
```
