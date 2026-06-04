package com.roomix.api.model.dto.request;

import com.roomix.api.model.enums.AiModel;
import com.roomix.api.model.enums.DecorationStyle;
import com.roomix.api.model.enums.ProductBrand;
import com.roomix.api.model.enums.PromptMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateProjectRequest {

    @NotNull
    private DecorationStyle style;

    private String name;

    @Positive
    private BigDecimal budget;

    private AiModel aiModel = AiModel.QWEN;

    private String sofaColor;
    private String sofaType;
    private String sofaMaterial;
    private String colorPalette;
    private String floorMaterial;
    private String wallFinish;
    private String tableMaterial;
    private String accessories;
    private Boolean keepExisting = false;
    private String roomType;
    private String customNote;
    private PromptMode promptMode = PromptMode.CREATIVE;

    // Parametres de rendu gpt-image-2
    private String  imageSize        = "auto";
    private String  imageQuality     = "auto";
    private String  imageFormat      = "jpeg";
    private Integer imageCompression = 85;
    private String  imageBackground  = "auto";

    // Recherche produits en ligne
    private Boolean          productSearchEnabled = false;
    private List<ProductBrand> preferredBrands;
    private String           searchItemsJson;
}
