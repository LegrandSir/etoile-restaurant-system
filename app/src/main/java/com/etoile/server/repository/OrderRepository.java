package com.etoile.server.repository;

import com.etoile.server.model.RestaurantOrder;
import com.etoile.shared.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<RestaurantOrder, Long> {
    List<RestaurantOrder> findByStatusNotOrderByCreatedAtEpochMsAsc(OrderStatus status);
    List<RestaurantOrder> findByTableNumberOrderByCreatedAtEpochMsDesc(int tableNumber);
}