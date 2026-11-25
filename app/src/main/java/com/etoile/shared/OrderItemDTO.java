package com.etoile.shared;

public class OrderItemDTO {
    private Long id;
    private Long menuItemId;
    private String itemName;
    private int quantity;
    private double unitPrice;
    private String station;
    private String customizations; // e.g., "Med-Rare | +Truffle Fries | Sauce on Side"
    private boolean prepared;

    public OrderItemDTO() {}

    public OrderItemDTO(Long id, Long menuItemId, String itemName, int quantity,
                        double unitPrice, String station, String customizations, boolean prepared) {
        this.id = id;
        this.menuItemId = menuItemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.station = station;
        this.customizations = customizations;
        this.prepared = prepared;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
}