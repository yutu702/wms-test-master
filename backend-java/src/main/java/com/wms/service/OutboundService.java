package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.OutboundOrderCreateRequest;
import com.wms.dto.OutboundOrderResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundService {

    private final InventoryRepository inventoryRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    /**
     * 创建出库单 + 扣减库存
     *
     * 并发安全方案：纯数据库原子操作
     * - 使用 UPDATE ... WHERE quantity >= :qty 一条SQL完成扣减
     * - 数据库引擎保证原子性，无需应用层加锁
     * - quantity >= :qty 条件在SQL层面保证不超卖
     */
    @Transactional
    public OutboundOrderResponse createOutboundOrder(OutboundOrderCreateRequest request) {
        // 1. 生成出库单号
        String orderNo = generateOrderNo();

        // 2. 校验所有商品和库位
        for (OutboundOrderCreateRequest.OutboundItemRequest item : request.getItems()) {
            if (!productRepository.existsById(item.getProductId())) {
                throw new BusinessException(404, "商品ID " + item.getProductId() + " 不存在");
            }
            if (!locationRepository.existsByCode(item.getLocationCode())) {
                throw new BusinessException(404, "库位编码 " + item.getLocationCode() + " 不存在");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(400, "出库数量必须大于0");
            }
        }

        // 3. 创建出库单（先保存获取ID）
        OutboundOrder order = OutboundOrder.builder()
                .orderNo(orderNo)
                .customerName(request.getCustomerName())
                .status("COMPLETED")
                .build();
        OutboundOrder savedOrder = outboundOrderRepository.saveAndFlush(order);

        // 4. 按 productId + locationCode 排序后扣减库存（防止多订单并发死锁）
        List<OutboundOrderCreateRequest.OutboundItemRequest> sortedItems = request.getItems().stream()
                .sorted(Comparator.comparing(OutboundOrderCreateRequest.OutboundItemRequest::getProductId)
                        .thenComparing(OutboundOrderCreateRequest.OutboundItemRequest::getLocationCode))
                .collect(Collectors.toList());

        for (OutboundOrderCreateRequest.OutboundItemRequest item : sortedItems) {
            // 原子扣减：UPDATE ... WHERE quantity >= :qty
            // 返回0表示库存不足或记录不存在，由数据库保证不超卖
            int rows = inventoryRepository.deductIfSufficient(
                    item.getProductId(), item.getLocationCode(), item.getQuantity());

            if (rows == 0) {
                // 回滚整个事务（包括已创建的出库单）
                throw new BusinessException(400,
                        "商品ID " + item.getProductId() + " 在库位 " + item.getLocationCode() + " 库存不足");
            }

            // 保存出库单明细
            OutboundOrderItem orderItem = OutboundOrderItem.builder()
                    .orderId(savedOrder.getId())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .locationCode(item.getLocationCode())
                    .build();
            outboundOrderItemRepository.save(orderItem);
        }

        log.info("出库单创建成功: orderNo={}, items={}", orderNo, request.getItems().size());

        // 5. 构建响应
        OutboundOrder finalOrder = outboundOrderRepository.findById(savedOrder.getId()).orElse(savedOrder);
        return buildOrderResponse(finalOrder);
    }

    /**
     * 生成出库单号: OUT-YYYYMMDD-XXX
     */
    private synchronized String generateOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "OUT-" + dateStr + "-";
        long count = outboundOrderRepository.countTodayOrders(prefix);
        return prefix + String.format("%03d", count + 1);
    }

    /**
     * 构建出库单响应 DTO
     */
    private OutboundOrderResponse buildOrderResponse(OutboundOrder order) {
        List<OutboundOrderItem> orderItems = outboundOrderItemRepository.findByOrderId(order.getId());
        List<OutboundOrderResponse.OutboundItemResponse> itemResponses = orderItems.stream()
                .map(item -> {
                    String productName = productRepository.findById(item.getProductId())
                            .map(Product::getName)
                            .orElse("未知商品");
                    return OutboundOrderResponse.OutboundItemResponse.builder()
                            .productId(item.getProductId())
                            .productName(productName)
                            .quantity(item.getQuantity())
                            .locationCode(item.getLocationCode())
                            .build();
                })
                .collect(Collectors.toList());

        return OutboundOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .customerName(order.getCustomerName())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
