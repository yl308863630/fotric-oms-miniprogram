package com.oms.repository;

import com.oms.entity.OrderShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderShipmentRepository extends JpaRepository<OrderShipment, Long> {
    Optional<OrderShipment> findBySalesOrderId(Long salesOrderId);
}
