package com.etoile.shared;

public class MenuItemDTO {
    private Long id;
    private String name;
    private String category;      // Starters, Salads, Mains, Pasta, Desserts, Wine
    private String description;
    private double price;
    private int prepTimeMinutes;
    private double rating;
    private int reviewCount;
    private String station;       // Grill, Fryer, Pass, Bar
    private String badge;         // BEST MATCH, UNDER 15 MIN, CHEF SIGNATURE, or null
    private String dietaryTags;   // Comma-separated: "Gluten-Free,Chef Signature"
    private boolean customizableTemp;
    private String imageKey;      // Maps to local food image or fallback color

    public MenuItemDTO() {}

    public MenuItemDTO(Long id, String name, String category, String description,
                       double price, int prepTimeMinutes, double rating, int reviewCount,
                       String station, String badge, String dietaryTags,
                       boolean customizableTemp, String imageKey) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.prepTimeMinutes = prepTimeMinutes;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.station = station;
        this.badge = badge;
        this.dietaryTags = dietaryTags;
        this.customizableTemp = customizableTemp;
        this.imageKey = imageKey;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getPrepTimeMinutes() { return prepTimeMinutes; }
    public void setPrepTimeMinutes(int prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }
    public String getDietaryTags() { return dietaryTags; }
    public void setDietaryTags(String dietaryTags) { this.dietaryTags = dietaryTags; }
    public boolean isCustomizableTemp() { return customizableTemp; }
    public void setCustomizableTemp(boolean customizableTemp) { this.customizableTemp = customizableTemp; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }
}