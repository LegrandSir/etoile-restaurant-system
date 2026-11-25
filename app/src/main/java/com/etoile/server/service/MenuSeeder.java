package com.etoile.server.service;

import com.etoile.server.model.MenuItem;
import com.etoile.server.repository.MenuItemRepository;
import com.etoile.server.repository.OrderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MenuSeeder implements CommandLineRunner {

    private final MenuItemRepository menuRepo;
    private final OrderRepository orderRepo;

    public MenuSeeder(MenuItemRepository menuRepo, OrderRepository orderRepo) {
        this.menuRepo = menuRepo;
        this.orderRepo = orderRepo;
    }

    @Override
    public void run(String... args) {
        List<MenuItem> existing = menuRepo.findAll();
        boolean hasOldUsdPrices = existing.stream().anyMatch(m -> m.getPrice() < 500);

        if (!existing.isEmpty() && !hasOldUsdPrices) {
            return;
        }

        // Clear old USD menu items and old USD test orders cleanly
        menuRepo.deleteAll();
        orderRepo.deleteAll();

        menuRepo.saveAll(List.of(
            // Mains
            new MenuItem("Truffle Ribeye Steak", "Mains",
                "12oz prime dry-aged ribeye seared in Normandy herb butter, finished with shaved black Perigord truffle.",
                4800, 15, 4.9, 320, "Grill", "BEST MATCH", "Gluten-Free,Chef Special", true, "ribeye"),
            new MenuItem("Pan-Seared King Salmon", "Mains",
                "Wild-caught crispy skin salmon over saffron fennel puree with charred lemon beurre blanc.",
                3400, 12, 4.8, 195, "Grill", "UNDER 15 MIN", "Gluten-Free", false, "salmon"),
            new MenuItem("Herb-Crusted Rack of Lamb", "Mains",
                "Dijon and rosemary crusted lamb rack served with roasted baby heirloom carrots and thyme jus.",
                4200, 18, 4.9, 210, "Grill", "CHEF SPECIAL", "Chef Special", true, "lamb"),
            new MenuItem("Roasted Duck Breast", "Mains",
                "Crispy skin duck breast glazed with spiced blackberry reduction and parsnip mousseline.",
                3600, 16, 4.8, 148, "Grill", null, "Gluten-Free", true, "duck"),

            // Pasta
            new MenuItem("Wild Mushroom Tagliatelle", "Pasta",
                "Fresh handmade egg ribbons tossed with porcini, chanterelles, aged parmesan, and black truffle cream.",
                2200, 10, 4.7, 142, "Pass", "UNDER 15 MIN", "Vegetarian", false, "mushroom_pasta"),
            new MenuItem("Butter-Poached Lobster Pasta", "Pasta",
                "Artisanal linguine folded with lobster tail, cherry confit tomatoes, and bisque emulsion.",
                3900, 14, 4.9, 275, "Pass", "BEST MATCH", "Chef Special", false, "lobster_pasta"),

            // Starters
            new MenuItem("Seared Hokkaido Scallops", "Starters",
                "Caramelized jumbo scallops over cauliflower veloute with crispy pancetta and micro chives.",
                2400, 8, 4.9, 184, "Pass", "UNDER 15 MIN", "Gluten-Free,Chef Special", false, "scallops"),
            new MenuItem("Hand-Cut Rosemary Truffle Fries", "Starters",
                "Twice-fried potatoes tossed in white truffle oil, crisp rosemary, and aged Parmigiano.",
                950, 7, 4.8, 410, "Fryer", "UNDER 15 MIN", "Vegetarian,Gluten-Free", false, "fries"),
            new MenuItem("French Onion Soup Gratinee", "Starters",
                "Slow-caramelized sweet onions in rich beef consomme topped with toasted brioche and melted Gruyere.",
                1400, 9, 4.7, 165, "Pass", null, null, false, "onion_soup"),

            // Salads
            new MenuItem("Heirloom Burrata Salad", "Salads",
                "Creamy Pugliese burrata with vine-ripened heirloom tomatoes, aged balsamic pearls, and basil oil.",
                1850, 6, 4.8, 130, "Pass", "UNDER 15 MIN", "Vegetarian,Gluten-Free", false, "burrata"),
            new MenuItem("Charred Little Gem Caesar", "Salads",
                "Lightly grilled baby romaine hearts with white anchovy dressing, sourdough crisp, and 24-month Parmigiano.",
                1600, 6, 4.6, 112, "Pass", null, null, false, "caesar"),

            // Desserts
            new MenuItem("Valrhona Dark Chocolate Fondant", "Desserts",
                "Warm molten 70% dark chocolate cake served with Tahitian vanilla bean gelato and cocoa nib tuile.",
                1350, 12, 4.9, 290, "Pass", "CHEF SPECIAL", "Vegetarian", false, "fondant"),
            new MenuItem("Madagascar Vanilla Creme Brulee", "Desserts",
                "Silky roasted vanilla bean custard beneath a crackling hand-torched caramelized sugar crust.",
                1200, 5, 4.8, 215, "Pass", "UNDER 15 MIN", "Vegetarian,Gluten-Free", false, "brulee"),

            // Wine & Drinks
            new MenuItem("Chateau Margaux Cabernet (Glass)", "Wine",
                "Full-bodied Bordeaux blend with velvety tannins, dark cassis, cedar, and a lingering espresso finish.",
                2600, 3, 4.9, 98, "Bar", "BEST MATCH", "Gluten-Free,Vegan", false, "red_wine"),
            new MenuItem("Artisanal Smoked Citrus Mocktail", "Wine",
                "Zero-proof blood orange, rosemary smoke, yuzu tonic, and botanical bitters over crystal ice.",
                950, 4, 4.8, 86, "Bar", "UNDER 15 MIN", "Gluten-Free,Vegan", false, "mocktail")
        ));
        System.out.println("[SEEDER] Migrated database to KSh prices (15 items loaded).");
    }
}