package com.roomix.api.service;

import com.roomix.api.model.enums.AiModel;
import org.springframework.stereotype.Component;

/**
 * Calcule le coût en tokens d'une génération IA.
 *
 * Convention : 1 token = $0.001 (soit $1 = 1 000 tokens).
 *
 * Grille tarifaire gpt-image-2 (OpenAI) :
 * ┌──────────────┬─────┬────────┬──────┐
 * │ Size         │ Low │ Medium │ High │
 * ├──────────────┼─────┼────────┼──────┤
 * │ 1024×1024    │   6 │     53 │  211 │
 * │ 1024×1536    │   5 │     41 │  165 │
 * │ 1536×1024    │   5 │     41 │  165 │
 * │ 2048×2048    │  24 │    212 │  844 │
 * │ 2048×1152    │  12 │    106 │  422 │
 * │ 1152×2048    │  12 │    106 │  422 │
 * │ 3840×2160    │  48 │    424 │ 1688 │
 * │ 2160×3840    │  48 │    424 │ 1688 │
 * └──────────────┴─────┴────────┴──────┘
 * Qwen / Flux  : 5 tokens (tarif fixe)
 * "auto" size  → 1024×1024 ; "auto" quality → medium
 */
@Component
public class TokenCostCalculator {

    /**
     * Tarif fixe pour Qwen (Wan2.7) et Flux.
     * Coût réel Wan2.7 ≈ $0,02–0,04/gen → 30 tokens = $0,030 (marge ~50% vs Starter)
     */
    public static final int QWEN_FLUX_COST = 30;

    private enum SizeTier  { SQ_1024, RECT_1024, SQ_2048, RECT_2048, RECT_4K }
    private enum QualityTier { LOW, MEDIUM, HIGH }

    // [qualityTier.ordinal()][sizeTier.ordinal()]
    private static final int[][] COST_TABLE = {
        //  SQ_1024  RECT_1024  SQ_2048  RECT_2048  RECT_4K
        {       6,        5,      24,       12,        48  },  // LOW
        {      53,       41,     212,      106,       424  },  // MEDIUM
        {     211,      165,     844,      422,      1688  },  // HIGH
    };

    /**
     * Calcule le coût en tokens pour une génération.
     *
     * @param model   Modèle IA sélectionné
     * @param size    Taille d'image ("auto", "1024x1024", "1536x1024", etc.)
     * @param quality Qualité ("auto", "low", "medium", "high")
     * @return Nombre de tokens à déduire
     */
    public int calculateCost(AiModel model, String size, String quality) {
        if (model == null || !AiModel.CHATGPT.equals(model)) {
            return QWEN_FLUX_COST;
        }
        SizeTier    s = resolveSizeTier(size);
        QualityTier q = resolveQualityTier(quality);
        return COST_TABLE[q.ordinal()][s.ordinal()];
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SizeTier resolveSizeTier(String size) {
        if (size == null || size.isBlank() || "auto".equalsIgnoreCase(size))
            return SizeTier.SQ_1024;
        return switch (size) {
            case "1024x1536", "1536x1024"  -> SizeTier.RECT_1024;
            case "2048x2048"               -> SizeTier.SQ_2048;
            case "2048x1152", "1152x2048"  -> SizeTier.RECT_2048;
            case "3840x2160", "2160x3840"  -> SizeTier.RECT_4K;
            default                        -> SizeTier.SQ_1024;
        };
    }

    private QualityTier resolveQualityTier(String quality) {
        if (quality == null || quality.isBlank() || "auto".equalsIgnoreCase(quality))
            return QualityTier.MEDIUM;
        return switch (quality.toLowerCase()) {
            case "low"  -> QualityTier.LOW;
            case "high" -> QualityTier.HIGH;
            default     -> QualityTier.MEDIUM;
        };
    }
}
