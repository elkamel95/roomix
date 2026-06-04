package com.roomix.api.model.dto;

import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Produit trouvé en ligne avec ses bytes d'image téléchargés.
 * Utilisé pour injecter les vrais produits comme références visuelles
 * dans le pipeline de génération IA.
 */
@Data
@Builder
public class ProductWithImage {
    private String          name;
    private String          color;
    private ProductBrand    brand;
    private ProductCategory category;
    private BigDecimal      price;
    private String          productUrl;
    private String          imageUrl;
    /** Bytes de l'image produit (fond blanc) — passés comme objectRef à l'IA */
    private byte[]          imageBytes;
}
