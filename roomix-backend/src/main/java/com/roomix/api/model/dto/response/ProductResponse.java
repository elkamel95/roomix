package com.roomix.api.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Réponse produit retournée par GET /projects/{id}/products
 *
 * Champs retournés :
 *  - id          : identifiant unique
 *  - name        : nom complet du produit (ex: "EKTORP Canapé 3 places")
 *  - color       : couleur principale (ex: "Hakebo beige") — null si non renseigné
 *  - category    : catégorie (SOFA, TABLE, LAMP, CARPET…)
 *  - brand       : marque (IKEA, CONFORAMA, AMAZON…)
 *  - price       : prix en euros
 *  - currency    : devise (EUR)
 *  - productUrl  : lien vers la page produit sur le site officiel
 *  - imageUrl    : URL directe de l'image produit (CDN marchand)
 *  - inStock     : disponibilité
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {

    private UUID   id;

    /** Nom complet du produit */
    private String name;

    /** Couleur principale du produit (ex : "Hakebo beige", "Chêne blanc") */
    private String color;

    /** Catégorie fonctionnelle */
    private ProductCategory category;

    /** Marque du produit */
    private ProductBrand brand;

    /** Prix en devise locale */
    private BigDecimal price;

    /** Devise (toujours "EUR") */
    private String currency;

    /** URL complète de la page produit sur le site officiel */
    private String productUrl;

    /** URL directe de l'image principale du produit (CDN marchand) */
    private String imageUrl;

    /** Produit actuellement disponible à l'achat */
    private Boolean inStock;
}
