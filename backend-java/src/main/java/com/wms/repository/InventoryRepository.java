package com.wms.repository;

import com.wms.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationCode(Long productId, String locationCode);

    // 悲观锁查询：用于入库/出库时锁定库存行，防止并发问题
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.locationCode = :locationCode")
    Optional<Inventory> findByProductIdAndLocationCodeForUpdate(
            @Param("productId") Long productId,
            @Param("locationCode") String locationCode);
}
