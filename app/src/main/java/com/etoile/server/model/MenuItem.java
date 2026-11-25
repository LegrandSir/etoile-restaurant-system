package com.etoile.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(length = 500)
    private String description;

    private double price;
    private int prepTimeMinutes;
    private double rating;
    private int reviewCount;
    private String station;
    private String badge;
    private String dietaryTags;
    private boolean customizableTemp;
    private String imageKey;

    public MenuItem() {}

    public MenuItem(String name, String category, String description, double price,
                    int prepTimeMinutes, double rating, int reviewCount, String station,
                    String badge, String dietaryTags, boolean customizableTemp, String imageKey) {
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
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public int getPrepTimeMinutes() { return prepTimeMinutes; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public String getStation() { return station; }
    public String getBadge() { return badge; }
    public String getDietaryTags() { return dietaryTags; }
    public boolean isCustomizableTemp() { return customizableTemp; }
    public String getImageKey() { return imageKey; }
}