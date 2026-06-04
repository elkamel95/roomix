package com.roomix.api.model.entity;

import com.roomix.api.model.enums.AiModel;
import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.PromptMode;
import com.roomix.api.model.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private String name = "Mon projet";

    @Column(name = "original_image_url", nullable = false)
    private String originalImageUrl;

    @Column(name = "original_image_key")
    private String originalImageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecorationStyle style;

    private BigDecimal budget;

    /** Couleur du canapé choisie par l'utilisateur (ex: "beige", "navy blue"). */
    @Column(name = "sofa_color")
    private String sofaColor;

    /** Type de canapé (ex: "3-seat sofa", "L-shape sectional"). */
    @Column(name = "sofa_type")
    private String sofaType;

    /** Matière du canapé (ex: "velvet", "genuine leather"). */
    @Column(name = "sofa_material")
    private String sofaMaterial;

    /** Palette de couleurs globale (ex: "warm earthy tones"). */
    @Column(name = "color_palette")
    private String colorPalette;

    /** Revêtement de sol souhaité (ex: "light oak parquet", "white marble tiles"). */
    @Column(name = "floor_material")
    private String floorMaterial;

    /** Finition des murs (ex: "exposed brick", "white paint"). */
    @Column(name = "wall_finish")
    private String wallFinish;

    /** Matière de la table basse (ex: "white marble", "tempered glass"). */
    @Column(name = "table_material")
    private String tableMaterial;

    /** Accessoires à ajouter, séparés par virgule (ex: "indoor plants,curtains,floor lamp"). */
    @Column(name = "accessories")
    private String accessories;

    /** Si true : conserver les matériaux/meubles existants et ne compléter que les éléments choisis. */
    @Column(name = "keep_existing", nullable = false)
    @Builder.Default
    private Boolean keepExisting = false;

    /** Type de pièce forcé par l'utilisateur (ex: "bedroom", "kitchen"). Null = auto-détecté. */
    @Column(name = "room_type")
    private String roomType;

    /** Instructions libres de l'utilisateur (ex: "je veux des plantes et des coussins colorés"). */
    @Column(name = "custom_note", columnDefinition = "TEXT")
    private String customNote;

    /** Mode de construction du prompt : CREATIVE (liberté artistique) ou PRO (préservation stricte). */
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_mode", nullable = false)
    @Builder.Default
    private PromptMode promptMode = PromptMode.CREATIVE;

    // ── Paramètres de rendu gpt-image-2 (ChatGPT uniquement) ─────────────────

    /**
     * Taille de l'image gpt-image-2.
     * 'auto', '1024x1024', '1536x1024', '1024x1536', '2048x2048',
     * '2048x1152', '3840x2160', '2160x3840'.
     */
    @Column(name = "image_size")
    @Builder.Default
    private String imageSize = "auto";

    /** Qualité du rendu : 'auto', 'low', 'medium', 'high'. */
    @Column(name = "image_quality")
    @Builder.Default
    private String imageQuality = "auto";

    /** Format de sortie : 'jpeg' (défaut, plus rapide), 'png', 'webp'. */
    @Column(name = "image_format")
    @Builder.Default
    private String imageFormat = "jpeg";

    /** Compression 0-100 pour jpeg/webp. Ignoré pour png. Défaut : 85.
     *  columnDefinition = "smallint" pour aligner la validation Hibernate sur le type SQL réel (int2). */
    @Column(name = "image_compression", columnDefinition = "smallint")
    @Builder.Default
    private Integer imageCompression = 85;

    /** Fond de l'image : 'auto', 'opaque'. ('transparent' non supporté sur gpt-image-2.) */
    @Column(name = "image_background")
    @Builder.Default
    private String imageBackground = "auto";

    /**
     * Objets de référence uploadés par l'utilisateur.
     * Format JSON : [{"title":"Mon canapé","imageKey":"...","imageUrl":"..."}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "object_refs", columnDefinition = "jsonb")
    private List<Map<String, String>> objectRefs;

    /** Active la recherche en ligne de produits réels (IKEA, Conforama…) pour ce projet. */
    @Column(name = "product_search_enabled", nullable = false)
    @Builder.Default
    private Boolean productSearchEnabled = false;

    /**
     * Marques préférées pour la recherche produits (IKEA, CONFORAMA).
     * Si null : utilise la config globale app.product-search.brands.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_brands", columnDefinition = "jsonb")
    private List<ProductBrand> preferredBrands;

    /** Articles souhaités JSON : [{category, maxBudget, color}] */
    @Column(name = "search_items_json", columnDefinition = "TEXT")
    private String searchItemsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_model", nullable = false)
    @Builder.Default
    private AiModel aiModel = AiModel.QWEN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "room_analysis", columnDefinition = "jsonb")
    private Map<String, Object> roomAnalysis;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Generation> generations;
}
