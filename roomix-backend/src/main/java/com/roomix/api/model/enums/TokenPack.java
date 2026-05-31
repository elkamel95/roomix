package com.roomix.api.model.enums;

/**
 * Packs de tokens disponibles à l'achat.
 * Prix en centimes EUR (Stripe utilise les centimes).
 */
public enum TokenPack {

    STARTER ("Starter",  1_000,  299,  "Idéal pour débuter",          "~18 générations Medium"),
    STANDARD("Standard", 3_500,  799,  "Le plus populaire",           "~66 générations Medium"),
    PRO     ("Pro",      10_000, 1799, "Pour les utilisateurs intensifs", "~188 générations Medium");

    private final String  label;
    private final int     tokens;
    private final int     priceEurCents;   // en centimes
    private final String  tagline;
    private final String  description;

    TokenPack(String label, int tokens, int priceEurCents, String tagline, String description) {
        this.label         = label;
        this.tokens        = tokens;
        this.priceEurCents = priceEurCents;
        this.tagline       = tagline;
        this.description   = description;
    }

    public String getLabel()         { return label;         }
    public int    getTokens()        { return tokens;        }
    public int    getPriceEurCents() { return priceEurCents; }
    public String getTagline()       { return tagline;       }
    public String getDescription()   { return description;   }

    /** Prix formaté (ex: "2,99 €") */
    public String getPriceFormatted() {
        return String.format("%d,%02d €", priceEurCents / 100, priceEurCents % 100);
    }

    /** Valeur par token en centimes (pour afficher l'économie) */
    public double getCentPerToken() {
        return (double) priceEurCents / tokens;
    }
}
