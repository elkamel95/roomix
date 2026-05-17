# HomeGPT AI — Documentation Centrale

Application mobile IA de décoration d'intérieur.

## Projets

| Projet | Technologie | Description |
|--------|-------------|-------------|
| `homegpt-docs` | Markdown / OpenAPI | Spécifications, architecture, API docs |
| `homegpt-backend` | Spring Boot 3 / Java 21 | API REST, authentification, orchestration IA |
| `homegpt-mobile` | React Native / Expo | Application iOS & Android |

## Architecture Globale

```
┌─────────────────────────────────────────────────────────┐
│                    MOBILE (React Native)                  │
│  Upload Photo → Choix Style → Résultat Avant/Après       │
└──────────────────────┬──────────────────────────────────-┘
                       │ REST / HTTPS
┌──────────────────────▼──────────────────────────────────-┐
│                  BACKEND (Spring Boot)                     │
│  Auth JWT │ Upload │ IA Orchestration │ Subscriptions     │
└──────┬────────────┬──────────────────────────────────────-┘
       │            │
┌──────▼──┐   ┌─────▼──────────────────────────────────────┐
│PostgreSQL│   │        Services IA Externes                  │
│         │   │  OpenAI Images │ Replicate │ Segment Anything │
└─────────┘   └─────────────────────────────────────────────┘
```

## Pipeline IA

```
1. Upload Photo
      ↓
2. Analyse IA (objets, lumière, profondeur)
      ↓
3. Génération Prompt Automatique
   ex: "Modern Scandinavian living room, beige sofa, warm lights..."
      ↓
4. Replicate API / OpenAI → image-to-image
      ↓
5. Post-processing (upscale, correction couleurs)
      ↓
6. Affichage Avant/Après + Shopping Links
```

## Documents

- [Spécification Produit](specs/product-spec.md)
- [Architecture Technique](architecture/technical-architecture.md)
- [API Reference](api/openapi.yaml)
- [Schéma Base de Données](database/schema.md)

## Stack Technique

### Frontend Mobile
- React Native + Expo SDK 51
- TypeScript
- NativeWind (Tailwind CSS)
- React Navigation v6
- Zustand (state management)
- React Query (data fetching)

### Backend
- Spring Boot 3.2
- Java 21
- PostgreSQL 16
- Redis (cache + queue)
- JWT Authentication
- Supabase Storage / AWS S3

### IA & APIs
- OpenAI API (GPT-4 Vision + DALL-E 3)
- Replicate API (Stable Diffusion XL, ControlNet, Flux)
- Meta Segment Anything Model (SAM)

### Infrastructure
- Docker + Docker Compose
- Railway / Render (backend)
- Expo EAS (mobile)
- Cloudflare CDN

## Roadmap

| Phase | Durée | Contenu |
|-------|-------|---------|
| MVP | 6-8 semaines | Login, Upload, Génération, Avant/Après |
| V1.5 | 4 semaines | Shopping, Budget IA, Optimisations |
| V2 | 4 semaines | Vidéo IA, AR, Multi-pièces |
