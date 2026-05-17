package com.homegpt.api.model.dto.request;

import com.homegpt.api.model.enums.DecorationStyle;
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
}
