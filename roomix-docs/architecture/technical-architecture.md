# Architecture Technique — ROOMIX

## Vue d'Ensemble

```
┌──────────────────────────────────────────────────────────────┐
│                     CLIENT MOBILE                             │
│              React Native + Expo (iOS/Android)                │
└───────────────────────┬──────────────────────────────────────┘
                        │ HTTPS / REST
                        │
┌───────────────────────▼──────────────────────────────────────┐
│                   SPRING BOOT API                             │
│                                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐ │
│  │Auth/JWT  │  │ Upload   │  │Generation│  │Subscription │ │
│  │Controller│  │Controller│  │Controller│  │Controller   │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬──────┘ │
│       └─────────────┴──────────────┴────────────────┘        │
│                            │                                  │
│  ┌─────────────────────────▼────────────────────────────┐   │
│  │                    Service Layer                        │   │
│  │  UserService │ ImageService │ AIService │ ShopService  │   │
│  └─────────────────────────┬────────────────────────────-┘   │
│                             │                                  │
│  ┌──────────────┐  ┌────────▼──────────────────────────────┐ │
│  │  PostgreSQL  │  │           Redis                        │ │
│  │  (données)   │  │  (cache + job queue IA)                │ │
│  └──────────────┘  └───────────────────────────────────────┘ │
└───────────────────────┬──────────────────────────────────────┘
                        │
         ┌──────────────┼──────────────────┐
         │              │                  │
┌────────▼──┐  ┌────────▼──────┐  ┌───────▼──────────┐
│  OpenAI   │  │   Replicate   │  │  Supabase/S3     │
│  GPT-4V   │  │  SDXL/Flux   │  │  Image Storage   │
│  DALL-E 3 │  │  ControlNet  │  │                  │
└───────────┘  └───────────────┘  └──────────────────┘
```

## Flux de Génération IA

```
Mobile                    Backend                      IA Services
  │                          │                              │
  │──POST /generate──────────►                              │
  │  {imageUrl, style, budget}│                             │
  │                          │──analyze image──────────────►│ GPT-4 Vision
  │                          │◄─{objects, room type, light}─│
  │                          │                              │
  │                          │ build prompt                 │
  │                          │ "Scandinavian living room,   │
  │                          │  beige sofa, warm lights..." │
  │                          │                              │
  │                          │──image-to-image──────────────►│ Replicate SDXL
  │                          │◄─{generatedImageUrl}──────────│
  │                          │                              │
  │◄─{jobId: "abc123"}───────│                              │
  │                          │                              │
  │──GET /generate/abc123────►                              │
  │◄─{status: "processing"}──│                              │
  │                          │                              │
  │──GET /generate/abc123────►                              │
  │◄─{status: "done",        │                              │
  │   resultUrl: "...",       │                              │
  │   products: [...]}────────│                              │
```

## Modèles de Données

### User
```
id (UUID)
email (unique)
passwordHash
firstName, lastName
plan (FREE | PREMIUM | PRO)
planExpiry (timestamp)
dailyGenerations (int)
lastGenerationReset (date)
createdAt, updatedAt
```

### Project
```
id (UUID)
userId (FK)
name
originalImageUrl
status (PENDING | PROCESSING | DONE | FAILED)
style (enum)
budget (decimal, nullable)
createdAt, updatedAt
```

### Generation
```
id (UUID)
projectId (FK)
resultImageUrl
prompt (text)
model (SDXL | FLUX | DALLE3)
processingTime (ms)
tokens (int)
cost (decimal)
createdAt
```

### Product (Shopping)
```
id (UUID)
generationId (FK)
name
category (SOFA | TABLE | LAMP | CARPET | PLANT | ...)
brand (IKEA | AMAZON | LEROY_MERLIN | ACTION)
price (decimal)
currency
productUrl
imageUrl
affiliateUrl
```

## Sécurité

| Couche | Mécanisme |
|--------|-----------|
| Auth | JWT (access 15min + refresh 7j) |
| Upload | Validation type MIME + antivirus |
| API | Rate limiting (Redis) : 10 req/min |
| Storage | URLs signées (expiry 1h) |
| Transport | HTTPS + HSTS |
| Data | Chiffrement au repos (PostgreSQL) |
| AI Keys | Variables d'environnement, jamais exposées |

## Environnements

| Env | Backend | Mobile | DB |
|-----|---------|--------|----|
| Dev | localhost:8080 | Expo Go | PostgreSQL local |
| Staging | staging.api.roomix.ai | Expo EAS | PostgreSQL cloud |
| Prod | api.roomix.ai | App Store / Play Store | PostgreSQL managed |
