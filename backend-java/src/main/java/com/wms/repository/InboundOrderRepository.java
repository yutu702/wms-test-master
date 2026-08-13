package com.wms.repository;

import com.wms.entity.InboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {

    // 用于生成入库单号：查询当天已创建的订单数
    @Query("SELECT COUNT(o) FROM InboundOrder o WHERE o.orderNo LIKE :prefix%")
    long countTodayOrders(@Param("prefix") String prefix);
}
