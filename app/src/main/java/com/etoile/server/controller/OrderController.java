package com.etoile.server.controller;

import com.etoile.server.service.OrderService;
import com.etoile.shared.OrderDTO;
import com.etoile.shared.OrderStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderDTO> getActiveOrders() {
        return orderService.getActiveOrders();
    }

    @GetMapping("/table/{tableNumber}")
    public List<OrderDTO> getTableOrders(@PathVariable int tableNumber) {
        return orderService.getOrdersForTable(tableNumber);
    }

    @PostMapping
    public OrderDTO createOrder(@RequestBody OrderDTO request) {
        return orderService.createOrder(request);
    }

    @PatchMapping("/{id}/status")
    public OrderDTO updateStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return orderService.updateOrderStatus(id, status);
    }

    @PatchMapping("/{orderId}/items/{itemId}/toggle")
    public OrderDTO toggleItem(@PathVariable Long orderId, @PathVariable Long itemId) {
        return orderService.toggleItemPrepared(orderId, itemId);
    }
}