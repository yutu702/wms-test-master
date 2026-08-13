package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.dto.InventoryResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    /**
     * 入库单创建
     * - 生成入库单号（格式 IN-YYYYMMDD-XXX）
     * - 校验商品和库位是否存在
     * - 在事务中同时创建入库单和更新库存
     * - 使用悲观锁保证库存累加的并发安全
     */
    @Transactional
    public InboundOrderResponse createInboundOrder(InboundOrderCreateRequest request) {
        // 1. 生成入库单号
        String orderNo = generateOrderNo();

        // 2. 校验所有商品和库位
        for (InboundItemRequest item : request.getItems()) {
            if (!productRepository.existsById(item.getProductId())) {
                throw new BusinessException(404, "商品ID " + item.getProductId() + " 不存在");
            }
            if (!locationRepository.existsByCode(item.getLocationCode())) {
                throw new BusinessException(404, "库位编码 " + item.getLocationCode() + " 不存在");
            }
        }

        // 3. 创建入库单（先保存获取ID）
        InboundOrder order = InboundOrder.builder()
                .orderNo(orderNo)
                .supplierName(request.getSupplierName())
                .status("COMPLETED")
                .build();
        InboundOrder savedOrder = inboundOrderRepository.saveAndFlush(order);

        // 4. 创建入库单明细 + 累加库存
        for (InboundItemRequest item : request.getItems()) {
            // 创建明细（设置orderId）
            InboundOrderItem orderItem = InboundOrderItem.builder()
                    .orderId(savedOrder.getId())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .locationCode(item.getLocationCode())
                    .build();
            inboundOrderItemRepository.save(orderItem);

            // 累加库存（使用悲观锁）
            addInventory(item.getProductId(), item.getLocationCode(), item.getQuantity());
        }

        log.info("入库单创建成功: orderNo={}, items={}", orderNo, request.getItems().size());

        // 5. 重新加载入库单构建响应
        InboundOrder finalOrder = inboundOrderRepository.findById(savedOrder.getId()).orElse(savedOrder);
        return buildOrderResponse(finalOrder);
    }

    /**
     * 累加库存
     * 使用 SELECT ... FOR UPDATE 悲观锁锁定库存行
     * 若库存记录不存在则新建
     */
    private void addInventory(Long productId, String locationCode, int quantity) {
        // 悲观锁查询：锁定库存行防止并发修改
        Inventory inventory = inventoryRepository
                .findByProductIdAndLocationCodeForUpdate(productId, locationCode)
                .orElseGet(() -> {
                    // 库存记录不存在，创建新记录
                    Inventory newInv = Inventory.builder()
                            .productId(productId)
                            .locationCode(locationCode)
                            .quantity(0)
                            .build();
                    return inventoryRepository.save(newInv);
                });

        // 累加数量
        inventory.setQuantity(inventory.getQuantity() + quantity);
        inventoryRepository.save(inventory);
    }

    /**
     * 生成入库单号: IN-YYYYMMDD-XXX
     */
    private synchronized String generateOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "IN-" + dateStr + "-";
        long count = inboundOrderRepository.countTodayOrders(prefix);
        return prefix + String.format("%03d", count + 1);
    }

    /**
     * 构建入库单响应 DTO（手动加载明细）
     */
    private InboundOrderResponse buildOrderResponse(InboundOrder order) {
        List<InboundOrderItem> orderItems = inboundOrderItemRepository.findByOrderId(order.getId());
        List<InboundOrderResponse.InboundOrderItemResponse> itemResponses = orderItems.stream()
                .map(item -> {
                    String productName = productRepository.findById(item.getProductId())
                            .map(Product::getName)
                            .orElse("未知商品");
                    return InboundOrderResponse.InboundOrderItemResponse.builder()
                            .productId(item.getProductId())
                            .productName(productName)
                            .quantity(item.getQuantity())
                            .locationCode(item.getLocationCode())
                            .build();
                })
                .collect(Collectors.toList());

        return InboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .supplierName(order.getSupplierName())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * 获取入库单列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<InboundOrderResponse> getInboundOrders(int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InboundOrder> orderPage = inboundOrderRepository.findAll(pageRequest);
        return orderPage.map(this::buildOrderResponse);
    }

    /**
     * 获取入库单详情
     */
    @Transactional(readOnly = true)
    public InboundOrderResponse getInboundOrderDetail(Long id) {
        InboundOrder order = inboundOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "入库单不存在"));
        return buildOrderResponse(order);
    }

    /**
     * 库存查询 — 任务2实现
     */
    public List<InventoryResponse> queryInventory(String keyword, Long warehouseId,
                                                   int page, int pageSize) {
        // TODO: 任务2实现
        throw new UnsupportedOperationException("请实现库存查询功能（任务2）");
    }
}
