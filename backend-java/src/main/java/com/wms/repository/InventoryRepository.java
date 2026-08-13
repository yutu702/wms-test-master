package com.wms.repository;

import com.wms.dto.InventoryResponse;
import com.wms.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationCode(Long productId, String locationCode);

    // 检查商品是否有关联库存
    boolean existsByProductId(Long productId);

    // 悲观锁查询：用于入库/出库时锁定库存行，防止并发问题
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.locationCode = :locationCode")
    Optional<Inventory> findByProductIdAndLocationCodeForUpdate(
            @Param("productId") Long productId,
            @Param("locationCode") String locationCode);

    // 库存查询：JOIN 关联 Product/Location/Warehouse，避免 N+1
    // 支持关键字（商品名称/SKU）、仓库、库位编码筛选
    @Query("SELECT new com.wms.dto.InventoryResponse(" +
           "  p.id, p.name, p.sku, i.locationCode, w.name, i.quantity, i.updatedAt) " +
           "FROM Inventory i " +
           "JOIN Product p ON i.productId = p.id " +
           "JOIN Location l ON i.locationCode = l.code " +
           "JOIN Warehouse w ON l.warehouseId = w.id " +
           "WHERE (:keyword IS NULL OR p.name LIKE %:keyword% OR p.sku LIKE %:keyword%) " +
           "AND (:warehouseId IS NULL OR w.id = :warehouseId) " +
           "AND (:locationCode IS NULL OR i.locationCode = :locationCode)")
    Page<InventoryResponse> queryInventory(
            @Param("keyword") String keyword,
            @Param("warehouseId") Long warehouseId,
            @Param("locationCode") String locationCode,
            Pageable pageable);
}
