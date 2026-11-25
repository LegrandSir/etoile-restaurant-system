package com.etoile.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long menuItemId;
    private String itemName;
    private int quantity;
    private double unitPrice;
    private String station;
    private String customizations;
    private boolean prepared = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private RestaurantOrder order;

    public OrderItem() {}

    public Long getId() { return id; }
    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
    public String getCustomizations() { return customizations; }
    public void setCustomizations(String customizations) { this.customizations = customizations; }
    public boolean isPrepared() { return prepared; }
    public void setPrepared(boolean prepared) { this.prepared = prepared; }
    public RestaurantOrder getOrder() { return order; }
    public void setOrder(RestaurantOrder order) { this.order = order; }
}