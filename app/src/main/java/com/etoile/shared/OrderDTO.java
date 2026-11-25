package com.etoile.shared;

import java.util.ArrayList;
import java.util.List;

public class OrderDTO {
    private Long id;
    private int tableNumber;
    private int guestCount;
    private OrderStatus status;
    private double totalPrice;
    private int estimatedPrepMinutes;
    private long createdAtEpochMs;
    private List<OrderItemDTO> items = new ArrayList<>();

    public OrderDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getTableNumber() { return tableNumber; }
    public void setTableNumber(int tableNumber) { this.tableNumber = tableNumber; }
    public int getGuestCount() { return guestCount; }
    public void setGuestCount(int guestCount) { this.guestCount = guestCount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
    public int getEstimatedPrepMinutes() { return estimatedPrepMinutes; }
    public void setEstimatedPrepMinutes(int estimatedPrepMinutes) { this.estimatedPrepMinutes = estimatedPrepMinutes; }
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
    public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }
    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }
}