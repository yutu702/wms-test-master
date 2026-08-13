package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.dto.OutboundOrderCreateRequest;
import com.wms.dto.OutboundOrderResponse;
import com.wms.service.OutboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    /**
     * 创建出库单 — 选做A
     * 库存扣减使用纯数据库原子操作，防止超卖
     */
    @PostMapping("/outbound-orders")
    public ResponseEntity<ApiResponse<OutboundOrderResponse>> createOutboundOrder(
            @Valid @RequestBody OutboundOrderCreateRequest request) {
        OutboundOrderResponse response = outboundService.createOutboundOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "出库单创建成功", response));
    }
}
