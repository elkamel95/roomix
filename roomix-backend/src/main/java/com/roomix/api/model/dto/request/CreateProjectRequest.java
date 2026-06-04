package com.roomix.api.model.dto.request;

import com.roomix.api.model.enums.AiModel;
import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.PromptMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProjectRequest {

    @NotNull
    private DecorationStyle style;

    private String name;

    @Positive
    private BigDecimal budget;

    /** Modèle IA à utiliser. Par défaut : QWEN (Alibaba). */
    private AiModel aiModel = AiModel.QWEN;

    // ── Personnalisation (toutes optionnelles) ──────────────────────────────

    /** Couleur du canapé souhaitée (ex: "beige", "navy blue", "terracotta"). */
    private String sofaColor;

    /** Type de canapé (ex: "3-seat sofa", "L-shape sectional", "sofa bed"). */
    private String sofaType;

    /** Matière du canapé (ex: "velvet", "genuine leather", "boucle"). */
    private String sofaMaterial;

    /** Palette de couleurs globale (ex: "warm earthy tones", "dark moody"). */
    private String colorPalette;

    // ── Personnalisation de la pièce ────────────────────────────────────────

    /** Revêtement de sol (ex: "light oak parquet", "polished concrete"). */
    private String floorMaterial;

    /** Finition des murs (ex: "exposed brick", "white paint"). */
    private String wallFinish;

    /** Matière de la table (ex: "white marble", "tempered glass"). */
    private String tableMaterial;

    /** Accessoires séparés par virgule (ex: "indoor plants,floor lamp,curtains"). */
    private String accessories;

    /** Si true : conserver les matériaux existants et ne changer que ce qui est sélectionné. */
    private Boolean keepExisting = false;

    // ── Type de pièce & objets de référence ────────────────────────────────

    /** Type de pièce choisi par l'utilisateur (ex: "living room", "bedroom"). */
    private String roomType;

    /** Instructions libres de l'utilisateur (max 300 caractères). */
    private String customNote;

    /** Mode de prompt : CREATIVE (artistique) ou PRO (préservation stricte). */
    private PromptMode promptMode = PromptMode.CREATIVE;

    // ── Paramètres de rendu gpt-image-2 (ChatGPT uniquement) ──────────────────

    /** Taille de l'image. Ex : 'auto', '1024x1024', '1536x1024', '2048x2048'. */
    private String imageSize = "auto";

    /** Qualité du rendu. Ex : 'auto', 'low', 'medium', 'high'. */
    private String imageQuality = "auto";

    /** Format de sortie : 'jpeg' (défaut), 'png', 'webp'. */
    private String imageFormat = "jpeg";

    /** Compression 0-100 pour jpeg/webp. Ignoré pour png. */
    private Integer imageCompression = 85;

    /** Fond de l'image : 'auto', 'opaque'. */
    private String imageBackground = "auto";

    // ── Recherche produits en ligne ─────────────────────────────────────────────

    /** Si true : ChatGPT recherche des produits réels en ligne après génération. */
    private Boolean productSearchEnabled = false;

    /** Marques à interroger (IKEA, CONFORAMA). */
    private List<ProductBrand> preferredBrands;

    /** Articles souhaités en JSON : [{category, maxBudget, color}] */
    private String searchItemsJson;

    // objectRefs ne passe pas dans le DTO — les fichiers MultipartFile sont traités
    // directement dans le contrôleur et assemblés dans ProjectService.
}
