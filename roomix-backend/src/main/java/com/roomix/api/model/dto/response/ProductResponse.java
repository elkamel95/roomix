package com.roomix.api.model.dto.response;

import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.ProductCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private ProductCategory category;
    private ProductBrand brand;
    private BigDecimal price;
    private String currency;
    private String productUrl;
    private String affiliateUrl;
    private String imageUrl;
    private Boolean inStock;
}
