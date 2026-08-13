package com.wms.controller;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 创建入库单 — 任务1
     */
    @PostMapping("/inbound-orders")
    public ResponseEntity<ApiResponse<InboundOrderResponse>> createInboundOrder(
            @Valid @RequestBody InboundOrderCreateRequest request) {
        InboundOrderResponse response = inventoryService.createInboundOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "入库单创建成功", response));
    }

    /**
     * 入库单列表 — 任务1
     */
    @GetMapping("/inbound-orders")
    public ApiResponse<PageResult<InboundOrderResponse>> getInboundOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<InboundOrderResponse> result = inventoryService.getInboundOrders(page, pageSize);
        PageResult<InboundOrderResponse> pageResult = new PageResult<>(
                result.getContent(),
                result.getTotalElements(),
                page,
                pageSize
        );
        return ApiResponse.success(pageResult);
    }

    /**
     * 入库单详情 — 任务1
     */
    @GetMapping("/inbound-orders/{id}")
    public ApiResponse<InboundOrderResponse> getInboundOrderDetail(@PathVariable Long id) {
        InboundOrderResponse response = inventoryService.getInboundOrderDetail(id);
        return ApiResponse.success(response);
    }

    /**
     * 库存查询 — 任务2
     * 支持按商品名称/SKU关键字、仓库、库位编码筛选
     */
    @GetMapping("/inventory")
    public ApiResponse<PageResult<InventoryResponse>> queryInventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String locationCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<InventoryResponse> result = inventoryService.queryInventory(keyword, warehouseId, locationCode, page, pageSize);
        PageResult<InventoryResponse> pageResult = new PageResult<>(
                result.getContent(),
                result.getTotalElements(),
                page,
                pageSize
        );
        return ApiResponse.success(pageResult);
    }
}
