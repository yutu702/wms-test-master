package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.dto.InboundItemRequest;
import com.wms.dto.InboundOrderCreateRequest;
import com.wms.dto.InboundOrderResponse;
import com.wms.entity.InboundOrder;
import com.wms.entity.InboundOrderItem;
import com.wms.entity.Inventory;
import com.wms.entity.Product;
import com.wms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService 单元测试")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InboundOrderRepository inboundOrderRepository;
    @Mock
    private InboundOrderItemRepository inboundOrderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private InboundOrderCreateRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new InboundOrderCreateRequest();
        validRequest.setSupplierName("测试供应商");

        InboundItemRequest item1 = new InboundItemRequest();
        item1.setProductId(1L);
        item1.setQuantity(100);
        item1.setLocationCode("WH-A-01-01");

        InboundItemRequest item2 = new InboundItemRequest();
        item2.setProductId(2L);
        item2.setQuantity(50);
        item2.setLocationCode("WH-A-01-02");

        validRequest.setItems(List.of(item1, item2));
    }

    @Test
    @DisplayName("创建入库单 - 正常流程：应返回201响应含完整信息")
    void createInboundOrder_success() {
        // Given: 所有商品和库位都存在
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productRepository.existsById(2L)).thenReturn(true);
        when(locationRepository.existsByCode("WH-A-01-01")).thenReturn(true);
        when(locationRepository.existsByCode("WH-A-01-02")).thenReturn(true);

        // 入库单保存
        InboundOrder savedOrder = InboundOrder.builder()
                .id(1L).orderNo("IN-20260813-001").supplierName("测试供应商").status("COMPLETED")
                .createdAt(LocalDateTime.now()).build();
        when(inboundOrderRepository.saveAndFlush(any(InboundOrder.class))).thenReturn(savedOrder);
        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(savedOrder));
        when(inboundOrderRepository.countTodayOrders(anyString())).thenReturn(0L);

        // 明细保存
        when(inboundOrderItemRepository.save(any(InboundOrderItem.class))).thenAnswer(i -> i.getArgument(0));
        when(inboundOrderItemRepository.findByOrderId(1L)).thenReturn(List.of(
                InboundOrderItem.builder().orderId(1L).productId(1L).quantity(100).locationCode("WH-A-01-01").build(),
                InboundOrderItem.builder().orderId(1L).productId(2L).quantity(50).locationCode("WH-A-01-02").build()
        ));

        // 库存：悲观锁查询返回已有库存
        Inventory inv1 = Inventory.builder().id(1L).productId(1L).locationCode("WH-A-01-01").quantity(50).build();
        Inventory inv2 = Inventory.builder().id(2L).productId(2L).locationCode("WH-A-01-02").quantity(200).build();
        when(inventoryRepository.findByProductIdAndLocationCodeForUpdate(1L, "WH-A-01-01")).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByProductIdAndLocationCodeForUpdate(2L, "WH-A-01-02")).thenReturn(Optional.of(inv2));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        // 商品名称查询
        when(productRepository.findById(1L)).thenReturn(Optional.of(Product.builder().id(1L).name("蓝牙耳机").sku("SKU-001").build()));
        when(productRepository.findById(2L)).thenReturn(Optional.of(Product.builder().id(2L).name("数据线").sku("SKU-002").build()));

        // When
        InboundOrderResponse response = inventoryService.createInboundOrder(validRequest);

        // Then
        assertNotNull(response);
        assertEquals("IN-20260813-001", response.getOrderNo());
        assertEquals("测试供应商", response.getSupplierName());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(2, response.getItems().size());
        assertEquals("蓝牙耳机", response.getItems().get(0).getProductName());

        // 验证库存被累加
        verify(inventoryRepository).save(argThat(inv -> inv.getQuantity() == 150)); // 50+100
        verify(inventoryRepository).save(argThat(inv -> inv.getQuantity() == 250)); // 200+50
    }

    @Test
    @DisplayName("创建入库单 - 商品不存在：应抛出404异常")
    void createInboundOrder_productNotFound() {
        // Given: 商品ID 999 不存在
        when(productRepository.existsById(999L)).thenReturn(false);

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(999L);
        item.setQuantity(10);
        item.setLocationCode("WH-A-01-01");
        request.setItems(List.of(item));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.createInboundOrder(request));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("商品ID 999 不存在"));

        // 验证不会调用保存操作
        verify(inboundOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("创建入库单 - 库位不存在：应抛出404异常")
    void createInboundOrder_locationNotFound() {
        // Given: 商品存在但库位不存在
        when(productRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsByCode("NOT-EXIST")).thenReturn(false);

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("测试");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(1L);
        item.setQuantity(10);
        item.setLocationCode("NOT-EXIST");
        request.setItems(List.of(item));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.createInboundOrder(request));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("库位编码 NOT-EXIST 不存在"));

        verify(inboundOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("创建入库单 - 库存记录不存在时应新建库存")
    void createInboundOrder_newInventoryRecord() {
        // Given: 商品和库位存在，但库存记录不存在
        when(productRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsByCode("WH-A-01-01")).thenReturn(true);

        InboundOrder savedOrder = InboundOrder.builder()
                .id(2L).orderNo("IN-20260813-002").supplierName("供应商").status("COMPLETED")
                .createdAt(LocalDateTime.now()).build();
        when(inboundOrderRepository.saveAndFlush(any())).thenReturn(savedOrder);
        when(inboundOrderRepository.findById(2L)).thenReturn(Optional.of(savedOrder));
        when(inboundOrderRepository.countTodayOrders(anyString())).thenReturn(1L);

        when(inboundOrderItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inboundOrderItemRepository.findByOrderId(2L)).thenReturn(List.of(
                InboundOrderItem.builder().orderId(2L).productId(1L).quantity(30).locationCode("WH-A-01-01").build()
        ));

        // 库存记录不存在 → 新建
        when(inventoryRepository.findByProductIdAndLocationCodeForUpdate(1L, "WH-A-01-01"))
                .thenReturn(Optional.empty());
        Inventory newInv = Inventory.builder().id(10L).productId(1L).locationCode("WH-A-01-01").quantity(0).build();
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        when(productRepository.findById(1L)).thenReturn(Optional.of(Product.builder().id(1L).name("商品A").sku("SKU-001").build()));

        InboundOrderCreateRequest request = new InboundOrderCreateRequest();
        request.setSupplierName("供应商");
        InboundItemRequest item = new InboundItemRequest();
        item.setProductId(1L);
        item.setQuantity(30);
        item.setLocationCode("WH-A-01-01");
        request.setItems(List.of(item));

        // When
        InboundOrderResponse response = inventoryService.createInboundOrder(request);

        // Then
        assertNotNull(response);
        // 验证 save 被调用2次：第1次新建库存记录(quantity=0)，第2次累加后保存(quantity=30)
        verify(inventoryRepository, times(2)).save(any(Inventory.class));
    }
}
