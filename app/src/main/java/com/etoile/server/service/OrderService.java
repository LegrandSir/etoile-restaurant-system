package com.etoile.server.service;

import com.etoile.server.model.MenuItem;
import com.etoile.server.model.OrderItem;
import com.etoile.server.model.RestaurantOrder;
import com.etoile.server.repository.MenuItemRepository;
import com.etoile.server.repository.OrderRepository;
import com.etoile.server.websocket.OrderWebSocketHandler;
import com.etoile.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final MenuItemRepository menuRepo;
    private final OrderWebSocketHandler wsHandler;

    public OrderService(OrderRepository orderRepo, MenuItemRepository menuRepo, OrderWebSocketHandler wsHandler) {
        this.orderRepo = orderRepo;
        this.menuRepo = menuRepo;
        this.wsHandler = wsHandler;
    }

    public List<MenuItemDTO> getMenu(String category) {
        List<MenuItem> items = (category == null || category.isBlank() || category.equalsIgnoreCase("All"))
                ? menuRepo.findAll()
                : menuRepo.findByCategoryIgnoreCase(category);

        return items.stream().map(m -> new MenuItemDTO(
                m.getId(), m.getName(), m.getCategory(), m.getDescription(),
                m.getPrice(), m.getPrepTimeMinutes(), m.getRating(), m.getReviewCount(),
                m.getStation(), m.getBadge(), m.getDietaryTags(),
                m.isCustomizableTemp(), m.getImageKey()
        )).toList();
    }

    public List<OrderDTO> getActiveOrders() {
        return orderRepo.findByStatusNotOrderByCreatedAtEpochMsAsc(OrderStatus.COMPLETED)
                .stream().map(this::toDTO).toList();
    }

    public List<OrderDTO> getOrdersForTable(int tableNumber) {
        return orderRepo.findByTableNumberOrderByCreatedAtEpochMsDesc(tableNumber)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public OrderDTO createOrder(OrderDTO request) {
        RestaurantOrder order = new RestaurantOrder();
        order.setTableNumber(request.getTableNumber());
        order.setGuestCount(request.getGuestCount() > 0 ? request.getGuestCount() : 2);
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAtEpochMs(System.currentTimeMillis());

        double total = 0.0;
        int maxPrep = 8;

        for (OrderItemDTO itemDto : request.getItems()) {
            OrderItem item = new OrderItem();
            item.setMenuItemId(itemDto.getMenuItemId());
            item.setItemName(itemDto.getItemName());
            item.setQuantity(itemDto.getQuantity());
            item.setUnitPrice(itemDto.getUnitPrice());
            item.setStation(itemDto.getStation() != null ? itemDto.getStation() : "Pass");
            item.setCustomizations(itemDto.getCustomizations());
            item.setPrepared(false);
            order.addItem(item);

            total += itemDto.getUnitPrice() * itemDto.getQuantity();
            if (itemDto.getMenuItemId() != null) {
                maxPrep = menuRepo.findById(itemDto.getMenuItemId())
                        .map(MenuItem::getPrepTimeMinutes)
                        .map(p -> Math.max(p, 8))
                        .orElse(maxPrep);
            }
        }

        order.setTotalPrice(total);
        order.setEstimatedPrepMinutes(maxPrep);

        RestaurantOrder saved = orderRepo.save(order);
        OrderDTO dto = toDTO(saved);
        wsHandler.broadcastOrderUpdate(dto);
        return dto;
    }

    @Transactional
    public OrderDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {
        RestaurantOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.READY || newStatus == OrderStatus.COMPLETED) {
            order.getItems().forEach(i -> i.setPrepared(true));
        }

        RestaurantOrder saved = orderRepo.save(order);
        OrderDTO dto = toDTO(saved);
        wsHandler.broadcastOrderUpdate(dto);
        return dto;
    }

    @Transactional
    public OrderDTO toggleItemPrepared(Long orderId, Long itemId) {
        RestaurantOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        for (OrderItem item : order.getItems()) {
            if (item.getId().equals(itemId)) {
                item.setPrepared(!item.isPrepared());
                break;
            }
        }

        // Automatically transition NEW -> IN_PREP when first item is checked
        boolean anyPrepared = order.getItems().stream().anyMatch(OrderItem::isPrepared);
        if (anyPrepared && order.getStatus() == OrderStatus.NEW) {
            order.setStatus(OrderStatus.IN_PREP);
        }

        RestaurantOrder saved = orderRepo.save(order);
        OrderDTO dto = toDTO(saved);
        wsHandler.broadcastOrderUpdate(dto);
        return dto;
    }

    private OrderDTO toDTO(RestaurantOrder order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setTableNumber(order.getTableNumber());
        dto.setGuestCount(order.getGuestCount());
        dto.setStatus(order.getStatus());
        dto.setTotalPrice(order.getTotalPrice());
        dto.setEstimatedPrepMinutes(order.getEstimatedPrepMinutes());
        dto.setCreatedAtEpochMs(order.getCreatedAtEpochMs());
        dto.setItems(order.getItems().stream().map(i -> new OrderItemDTO(
                i.getId(), i.getMenuItemId(), i.getItemName(), i.getQuantity(),
                i.getUnitPrice(), i.getStation(), i.getCustomizations(), i.isPrepared()
        )).toList());
        return dto;
    }
}