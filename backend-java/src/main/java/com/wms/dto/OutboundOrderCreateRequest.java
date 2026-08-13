package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OutboundOrderCreateRequest {

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @NotEmpty(message = "出库明细不能为空")
    @Valid
    private List<OutboundItemRequest> items;

    @Data
    public static class OutboundItemRequest {
        private Long productId;
        private Integer quantity;
        private String locationCode;
    }
}
