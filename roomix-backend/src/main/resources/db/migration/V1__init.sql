-- ============================================================
-- ROOMIX — Migration Flyway V1
-- Création du schéma initial
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- ENUMS
-- ============================================================
CREATE TYPE plan_type AS ENUM ('FREE', 'PREMIUM', 'PRO');
CREATE TYPE project_status AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');
CREATE TYPE decoration_style AS ENUM (
  'SCANDINAVIAN', 'MODERN_LUXURY', 'MINIMALIST', 'JAPANESE_ZEN',
  'ARABIC_MODERN', 'GAMER_SETUP', 'COZY', 'INDUSTRIAL',
  'SMART_OFFICE', 'DEVELOPER_SETUP'
);
CREATE TYPE ai_model AS ENUM ('SDXL', 'FLUX', 'DALLE3', 'CONTROLNET');
CREATE TYPE product_brand AS ENUM ('IKEA', 'AMAZON', 'LEROY_MERLIN', 'ACTION', 'OTHER');
CREATE TYPE product_category AS ENUM (
  'SOFA', 'TABLE', 'CHAIR', 'LAMP', 'CARPET', 'PLANT',
  'CURTAIN', 'SHELF', 'DESK', 'BED', 'DECORATION', 'OTHER'
);

-- ============================================================
-- TABLE : users
-- ============================================================
CREATE TABLE users (
  id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email                   VARCHAR(255) UNIQUE NOT NULL,
  password_hash           VARCHAR(255),
  first_name              VARCHAR(100),
  last_name               VARCHAR(100),
  avatar_url              TEXT,
  google_id               VARCHAR(255) UNIQUE,
  plan                    plan_type NOT NULL DEFAULT 'FREE',
  plan_expiry             TIMESTAMP,
  daily_generations       INTEGER NOT NULL DEFAULT 0,
  last_generation_reset   DATE NOT NULL DEFAULT CURRENT_DATE,
  is_active               BOOLEAN NOT NULL DEFAULT TRUE,
  email_verified          BOOLEAN NOT NULL DEFAULT FALSE,
  created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE : subscriptions
-- ============================================================
CREATE TABLE subscriptions (
  id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  stripe_subscription_id  VARCHAR(255) UNIQUE,
  stripe_customer_id      VARCHAR(255),
  plan                    plan_type NOT NULL,
  status                  VARCHAR(50) NOT NULL DEFAULT 'active',
  current_period_start    TIMESTAMP,
  current_period_end      TIMESTAMP,
  cancel_at_period_end    BOOLEAN DEFAULT FALSE,
  created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE : projects
-- ============================================================
CREATE TABLE projects (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name                VARCHAR(255) NOT NULL DEFAULT 'Mon projet',
  original_image_url  TEXT NOT NULL,
  original_image_key  VARCHAR(500),
  status              project_status NOT NULL DEFAULT 'PENDING',
  style               decoration_style NOT NULL,
  budget              DECIMAL(10, 2),
  room_analysis       JSONB,
  created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE : generations
-- ============================================================
CREATE TABLE generations (
  id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  project_id                UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  result_image_url          TEXT,
  result_image_key          VARCHAR(500),
  prompt                    TEXT NOT NULL,
  negative_prompt           TEXT,
  model                     ai_model NOT NULL DEFAULT 'SDXL',
  processing_time_ms        INTEGER,
  tokens_used               INTEGER,
  cost_usd                  DECIMAL(10, 6),
  replicate_prediction_id   VARCHAR(255),
  error_message             TEXT,
  created_at                TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE : products
-- ============================================================
CREATE TABLE products (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  generation_id   UUID NOT NULL REFERENCES generations(id) ON DELETE CASCADE,
  name            VARCHAR(255) NOT NULL,
  description     TEXT,
  category        product_category NOT NULL DEFAULT 'OTHER',
  brand           product_brand NOT NULL DEFAULT 'OTHER',
  price           DECIMAL(10, 2),
  currency        VARCHAR(3) DEFAULT 'EUR',
  product_url     TEXT,
  affiliate_url   TEXT,
  image_url       TEXT,
  in_stock        BOOLEAN DEFAULT TRUE,
  created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE : refresh_tokens
-- ============================================================
CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token       VARCHAR(500) UNIQUE NOT NULL,
  expires_at  TIMESTAMP NOT NULL,
  is_revoked  BOOLEAN DEFAULT FALSE,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE : generation_logs (audit)
-- ============================================================
CREATE TABLE generation_logs (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
  project_id  UUID REFERENCES projects(id) ON DELETE SET NULL,
  event       VARCHAR(100) NOT NULL,
  metadata    JSONB,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEX
-- ============================================================
CREATE INDEX idx_users_email                ON users(email);
CREATE INDEX idx_projects_user_id           ON projects(user_id);
CREATE INDEX idx_projects_status            ON projects(status);
CREATE INDEX idx_projects_created_at        ON projects(created_at DESC);
CREATE INDEX idx_generations_project_id     ON generations(project_id);
CREATE INDEX idx_products_generation_id     ON products(generation_id);
CREATE INDEX idx_refresh_tokens_user_id     ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token       ON refresh_tokens(token);
CREATE INDEX idx_subscriptions_user_id      ON subscriptions(user_id);
CREATE INDEX idx_generation_logs_user_id    ON generation_logs(user_id);
CREATE INDEX idx_generation_logs_project_id ON generation_logs(project_id);

-- ============================================================
-- TRIGGER : updated_at automatique
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_projects_updated_at
  BEFORE UPDATE ON projects
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_subscriptions_updated_at
  BEFORE UPDATE ON subscriptions
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
