# Schéma Base de Données — ROOMIX

## Diagramme ERD

```
users ──────────< projects ──────────< generations ──────────< products
  │                                        │
  └──< subscriptions                       └──< generation_logs
```

## Scripts SQL

```sql
-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enum types
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

-- Table users
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255),
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  avatar_url TEXT,
  google_id VARCHAR(255) UNIQUE,
  plan plan_type NOT NULL DEFAULT 'FREE',
  plan_expiry TIMESTAMP,
  daily_generations INTEGER NOT NULL DEFAULT 0,
  last_generation_reset DATE DEFAULT CURRENT_DATE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Table subscriptions
CREATE TABLE subscriptions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  stripe_subscription_id VARCHAR(255) UNIQUE,
  stripe_customer_id VARCHAR(255),
  plan plan_type NOT NULL,
  status VARCHAR(50) NOT NULL DEFAULT 'active',
  current_period_start TIMESTAMP,
  current_period_end TIMESTAMP,
  cancel_at_period_end BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Table projects
CREATE TABLE projects (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL DEFAULT 'Mon projet',
  original_image_url TEXT NOT NULL,
  original_image_key VARCHAR(500),
  status project_status NOT NULL DEFAULT 'PENDING',
  style decoration_style NOT NULL,
  budget DECIMAL(10,2),
  room_analysis JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Table generations
CREATE TABLE generations (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  result_image_url TEXT,
  result_image_key VARCHAR(500),
  prompt TEXT NOT NULL,
  negative_prompt TEXT,
  model ai_model NOT NULL DEFAULT 'SDXL',
  processing_time_ms INTEGER,
  tokens_used INTEGER,
  cost_usd DECIMAL(10,6),
  replicate_prediction_id VARCHAR(255),
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Table products (shopping links)
CREATE TABLE products (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  generation_id UUID NOT NULL REFERENCES generations(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  category product_category NOT NULL DEFAULT 'OTHER',
  brand product_brand NOT NULL DEFAULT 'OTHER',
  price DECIMAL(10,2),
  currency VARCHAR(3) DEFAULT 'EUR',
  product_url TEXT,
  affiliate_url TEXT,
  image_url TEXT,
  in_stock BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Table generation_logs (audit)
CREATE TABLE generation_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id),
  project_id UUID REFERENCES projects(id),
  event VARCHAR(100) NOT NULL,
  metadata JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Table refresh_tokens
CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token VARCHAR(500) UNIQUE NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  is_revoked BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index
CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_generations_project_id ON generations(project_id);
CREATE INDEX idx_products_generation_id ON products(generation_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);

-- Trigger updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_projects_updated_at BEFORE UPDATE ON projects FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```
