-- V12 : Table d'audit des transactions de tokens
CREATE TABLE IF NOT EXISTS token_transactions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount          INTEGER     NOT NULL,           -- positif = crédit, négatif = débit
    type            VARCHAR(20) NOT NULL,           -- PURCHASE | GENERATION | BONUS | REFUND
    pack            VARCHAR(20),                    -- STARTER | STANDARD | PRO (pour PURCHASE)
    reference       VARCHAR(255),                   -- Stripe session ID ou project ID
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_token_tx_user ON token_transactions(user_id);
CREATE INDEX idx_token_tx_type ON token_transactions(type);
