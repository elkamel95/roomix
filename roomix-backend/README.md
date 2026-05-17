# ROOMIX — Backend

API REST Spring Boot 3 pour ROOMIX.

## Prérequis

- Java 21
- Maven 3.9+
- Docker + Docker Compose

## Démarrage rapide

```bash
# 1. Copier les variables d'environnement
cp .env.example .env
# Renseigner OPENAI_API_KEY, REPLICATE_API_KEY, etc.

# 2. Démarrer PostgreSQL + Redis
docker-compose up postgres redis -d

# 3. Lancer le backend
./mvnw spring-boot:run
```

## Avec Docker complet

```bash
docker-compose up --build
```

## API

- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- Health: http://localhost:8080/api/v1/actuator/health

## Endpoints principaux

| Méthode | URL | Description |
|---------|-----|-------------|
| POST | /auth/register | Inscription |
| POST | /auth/login | Connexion |
| POST | /auth/refresh | Rafraîchir token |
| POST | /projects | Créer un projet (upload) |
| GET | /projects | Lister les projets |
| GET | /projects/{id}/status | Statut génération |
| GET | /projects/{id}/products | Liens shopping |
| GET | /users/me | Profil |
| GET | /users/me/quota | Quota |

## Structure

```
src/main/java/com/ROOMIX/api/
├── config/          # SecurityConfig, S3Config, AppProperties
├── controller/      # AuthController, ProjectController, UserController
├── service/         # AuthService, ProjectService, AiOrchestrationService
│                      ReplicateService, OpenAiService, StorageService
├── repository/      # JPA Repositories
├── model/
│   ├── entity/      # User, Project, Generation, Product, RefreshToken
│   ├── dto/         # Request/Response DTOs
│   └── enums/       # PlanType, ProjectStatus, DecorationStyle, etc.
├── security/        # JWT Filter, UserDetailsService
└── exception/       # GlobalExceptionHandler + exceptions
```

## Variables d'environnement

| Variable | Requis | Description |
|----------|--------|-------------|
| JWT_SECRET | ✅ | Clé secrète JWT (256 bits min) |
| OPENAI_API_KEY | ✅ | Clé API OpenAI (GPT-4 Vision) |
| REPLICATE_API_KEY | ✅ | Clé API Replicate (SDXL/Flux) |
| REPLICATE_SDXL_VERSION | ✅ | Hash version du modèle SDXL |
| STORAGE_BUCKET | ✅ | Nom du bucket S3/Supabase |
| STORAGE_ENDPOINT | ✅ | URL endpoint S3/Supabase |
| SUPABASE_ACCESS_KEY | ✅ | Clé d'accès stockage |
| SUPABASE_SECRET_KEY | ✅ | Clé secrète stockage |
| STRIPE_SECRET_KEY | ⚙️ | Pour la monétisation |
