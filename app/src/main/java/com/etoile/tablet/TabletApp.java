package com.etoile.tablet;

import com.etoile.shared.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.*;
import java.util.stream.Collectors;

public class TabletApp extends Application {

    private final ApiClient api = new ApiClient();

    // State
    private int currentTable = 12;
    private int guestCount = 4;
    private String selectedCategory = "All";
    private String searchQuery = "";
    private boolean filterUnder15 = false;
    private boolean filterChefSpecial = false;
    private boolean filterGlutenFree = false;
    private boolean filterVegetarian = false;
    private int maxPrepFilter = 25;
    private double maxPriceFilter = 5500.0;
    private boolean cardViewMode = true;
    private double currentViewportWidth = 1000;

    private List<MenuItemDTO> allMenuItems = new ArrayList<>();
    private final List<CartLine> cartLines = new ArrayList<>();
    private List<OrderDTO> activeTableOrders = new ArrayList<>();

    // UI References
    private Stage primaryStage;
    private StackPane rootStack;
    private VBox backdrop;
    private VBox detailDrawer;
    private VBox filterDrawer;
    private HBox categoryBar;
    private HBox filterChipBar;
    private Label dishCountLabel;
    private FlowPane menuGrid;
    private VBox cartItemsContainer;
    private VBox liveTrackerBox;
    private Label cartSubtotalLabel;
    private Label sentTotalLabel;
    private Button sendOrderBtn;
    private Label syncStatusLabel;
    private Circle syncDot;

    private static class CartLine {
        MenuItemDTO dish;
        int quantity;
        double unitPriceWithAddons;
        String customizations;

        CartLine(MenuItemDTO dish, int quantity, double unitPriceWithAddons, String customizations) {
            this.dish = dish;
            this.quantity = quantity;
            this.unitPriceWithAddons = unitPriceWithAddons;
            this.customizations = customizations;
        }
    }

    private static String formatKsh(double amount) {
        return String.format(Locale.US, "KSh %,.0f", amount);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        rootStack = new StackPane();
        rootStack.setStyle("-fx-background-color: #FFFFFF;");

        BorderPane mainLayout = new BorderPane();
        mainLayout.setCenter(buildLeftMenuWorkspace());
        mainLayout.setRight(buildPersistentCartSidebar());

        // Backdrop + Slide-over Drawers
        backdrop = new VBox();
        backdrop.setStyle("-fx-background-color: rgba(24, 22, 21, 0.34);");
        backdrop.setVisible(false);
        backdrop.setOnMouseClicked(e -> closeAllDrawers());

        detailDrawer = new VBox();
        detailDrawer.getStyleClass().add("drawer-sheet");
        detailDrawer.setPrefWidth(520);
        detailDrawer.setMaxWidth(520);
        detailDrawer.setVisible(false);
        StackPane.setAlignment(detailDrawer, Pos.CENTER_RIGHT);

        filterDrawer = buildFilterDrawer();
        filterDrawer.setVisible(false);
        StackPane.setAlignment(filterDrawer, Pos.CENTER_RIGHT);

        rootStack.getChildren().addAll(mainLayout, backdrop, detailDrawer, filterDrawer);

        Scene scene = new Scene(rootStack, 1380, 860);
        var css = getClass().getResource("/etoile-theme.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("L'Étoile Bistro — Table " + currentTable + " Self-Ordering Tablet");
        stage.setScene(scene);
        stage.setFullScreenExitHint("Press ESC or click Windowed to exit full-screen tablet mode");
        stage.setFullScreen(true);
        stage.setOnCloseRequest(e -> api.shutdown());
        stage.show();

        loadMenuFromServer();
        refreshTableOrders();
        connectRealTimeSync();
    }

    // =========================================================================
    // LEFT WORKSPACE: HEADER, SQUIRCLES, PILLS, RESPONSIVE MENU GRID/LIST
    // =========================================================================
    private VBox buildLeftMenuWorkspace() {
        VBox workspace = new VBox(20);
        workspace.setPadding(new Insets(28, 34, 20, 36));
        workspace.setStyle("-fx-background-color: #FFFFFF;");

        // 1. Top Header Bar
        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox brandBox = new VBox(2);
        Label brand = new Label("Table " + currentTable + " · L’Étoile Bistro");
        brand.getStyleClass().add("brand-title");

        HBox syncRow = new HBox(6);
        syncRow.setAlignment(Pos.CENTER_LEFT);
        syncDot = new Circle(4.5, Color.web("#D97706"));
        UiAnimator.startPulse(syncDot);
        syncStatusLabel = new Label("Connecting to kitchen...");
        syncStatusLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #78716C; -fx-font-weight: 600;");
        syncRow.getChildren().addAll(syncDot, syncStatusLabel);
        brandBox.getChildren().addAll(brand, syncRow);

        TextField searchField = new TextField();
        searchField.setPromptText("Search dishes, ingredients, or pairings...");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(420);
        searchField.setMaxWidth(480);
        HBox.setHgrow(searchField, Priority.SOMETIMES);
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            searchQuery = newV == null ? "" : newV.trim().toLowerCase();
            renderMenuItems(false);
        });

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        ComboBox<String> tablePicker = new ComboBox<>();
        for (int t = 1; t <= 20; t++) tablePicker.getItems().add("Table " + t);
        tablePicker.setValue("Table " + currentTable);
        tablePicker.getStyleClass().add("table-picker");
        tablePicker.setOnAction(e -> {
            String val = tablePicker.getValue();
            if (val != null) {
                currentTable = Integer.parseInt(val.replace("Table ", ""));
                brand.setText("Table " + currentTable + " · L’Étoile Bistro");
                primaryStage.setTitle("L'Étoile Bistro — Table " + currentTable + " Self-Ordering Tablet");
                refreshTableOrders();
                UiAnimator.showToast(rootStack, "Switched tablet terminal to Table " + currentTable);
            }
        });

        Button fullscreenBtn = new Button("Windowed");
        fullscreenBtn.getStyleClass().add("pill-btn");
        fullscreenBtn.setOnAction(e -> {
            boolean next = !primaryStage.isFullScreen();
            primaryStage.setFullScreen(next);
            fullscreenBtn.setText(next ? "Windowed" : "Fullscreen");
        });

        topBar.getChildren().addAll(brandBox, searchField, topSpacer, tablePicker, fullscreenBtn);

        // 2. Tactile Category Squircles
        categoryBar = new HBox(18);
        categoryBar.setAlignment(Pos.CENTER_LEFT);
        renderCategoryBar();

        // 3. Filter Pills Row
        filterChipBar = new HBox(10);
        filterChipBar.setAlignment(Pos.CENTER_LEFT);
        renderFilterChips();

        // 4. Section Title + Count + Card/List View Switcher
        HBox sectionHeader = new HBox(12);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);
        Label recTitle = new Label("Recommended for your table");
        recTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        dishCountLabel = new Label("Loading menu...");
        dishCountLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #8C857E;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox viewToggle = new HBox(3);
        viewToggle.getStyleClass().add("segment-container");
        Button cardBtn = new Button("Card");
        Button listBtn = new Button("List");
        cardBtn.getStyleClass().add("segment-btn-active");
        listBtn.getStyleClass().add("segment-btn");

        cardBtn.setOnAction(e -> {
            if (!cardViewMode) {
                cardViewMode = true;
                cardBtn.getStyleClass().setAll("segment-btn-active");
                listBtn.getStyleClass().setAll("segment-btn");
                renderMenuItems(true);
            }
        });
        listBtn.setOnAction(e -> {
            if (cardViewMode) {
                cardViewMode = false;
                listBtn.getStyleClass().setAll("segment-btn-active");
                cardBtn.getStyleClass().setAll("segment-btn");
                renderMenuItems(true);
            }
        });
        viewToggle.getChildren().addAll(cardBtn, listBtn);
        sectionHeader.getChildren().addAll(recTitle, dishCountLabel, spacer, viewToggle);

        // 5. Scrollable Responsive Menu Grid
        menuGrid = new FlowPane(24, 24);
        menuGrid.setPadding(new Insets(4, 8, 28, 2));

        ScrollPane scrollPane = new ScrollPane(menuGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("clean-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        scrollPane.viewportBoundsProperty().addListener((obs, oldB, newB) -> {
            double w = newB.getWidth();
            if (w > 300 && Math.abs(w - currentViewportWidth) > 35) {
                currentViewportWidth = w;
                if (!allMenuItems.isEmpty()) {
                    renderMenuItems(false);
                }
            }
        });

        workspace.getChildren().addAll(topBar, categoryBar, filterChipBar, sectionHeader, scrollPane);
        return workspace;
    }

    private void renderCategoryBar() {
        categoryBar.getChildren().clear();
        String[] cats = {"All", "Starters", "Salads", "Mains", "Pasta", "Desserts", "Wine"};

        for (String cat : cats) {
            boolean active = selectedCategory.equalsIgnoreCase(cat);
            VBox wrapper = new VBox(8);
            wrapper.setAlignment(Pos.CENTER);

            StackPane squircle = new StackPane();
            squircle.setPrefSize(86, 80);
            squircle.getStyleClass().add(active ? "category-tile-active" : "category-tile");
            squircle.getChildren().add(DishImageCache.createCategoryThumb(cat, 50));

            Label name = new Label(cat);
            name.setStyle(active
                    ? "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4A2E1B;"
                    : "-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #57534E;");

            wrapper.getChildren().addAll(squircle, name);
            wrapper.setOnMouseClicked(e -> {
                selectedCategory = cat;
                renderCategoryBar();
                renderMenuItems(true);
            });
            UiAnimator.attachHoverLift(squircle);
            categoryBar.getChildren().add(wrapper);
        }
    }

    private void renderFilterChips() {
        filterChipBar.getChildren().clear();

        Button advancedFilterBtn = new Button("⚙  Filter & Prep Time");
        advancedFilterBtn.getStyleClass().add((maxPrepFilter < 25 || maxPriceFilter < 5500.0) ? "pill-btn-active" : "pill-btn");
        advancedFilterBtn.setOnAction(e -> {
            detailDrawer.setVisible(false);
            UiAnimator.slideDrawerIn(backdrop, filterDrawer, 430);
        });
        filterChipBar.getChildren().add(advancedFilterBtn);

        filterChipBar.getChildren().add(createTogglePill("Under 15 min", filterUnder15, v -> filterUnder15 = v));
        filterChipBar.getChildren().add(createTogglePill("Chef Special", filterChefSpecial, v -> filterChefSpecial = v));
        filterChipBar.getChildren().add(createTogglePill("Gluten-Free", filterGlutenFree, v -> filterGlutenFree = v));
        filterChipBar.getChildren().add(createTogglePill("Vegetarian", filterVegetarian, v -> filterVegetarian = v));

        if (filterUnder15 || filterChefSpecial || filterGlutenFree || filterVegetarian || maxPrepFilter < 25 || maxPriceFilter < 5500.0) {
            Button reset = new Button("Reset");
            reset.setStyle("-fx-background-color: transparent; -fx-text-fill: #4A2E1B; -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-underline: true; -fx-cursor: hand;");
            reset.setOnAction(e -> {
                filterUnder15 = filterChefSpecial = filterGlutenFree = filterVegetarian = false;
                maxPrepFilter = 25;
                maxPriceFilter = 5500.0;
                renderFilterChips();
                renderMenuItems(true);
            });
            filterChipBar.getChildren().add(reset);
        }
    }

    private Button createTogglePill(String text, boolean active, java.util.function.Consumer<Boolean> setter) {
        Button btn = new Button(text);
        btn.getStyleClass().add(active ? "pill-btn-active" : "pill-btn");
        btn.setOnAction(e -> {
            setter.accept(!active);
            renderFilterChips();
            renderMenuItems(true);
        });
        return btn;
    }

    private List<MenuItemDTO> getFilteredMenu() {
        return allMenuItems.stream().filter(item -> {
            if (!selectedCategory.equalsIgnoreCase("All") && !item.getCategory().equalsIgnoreCase(selectedCategory)) {
                return false;
            }
            if (!searchQuery.isEmpty()) {
                String hay = (item.getName() + " " + item.getDescription() + " " +
                        (item.getDietaryTags() != null ? item.getDietaryTags() : "")).toLowerCase();
                if (!hay.contains(searchQuery)) return false;
            }
            if (filterUnder15 && item.getPrepTimeMinutes() > 15) return false;
            if (item.getPrepTimeMinutes() > maxPrepFilter) return false;
            if (item.getPrice() > maxPriceFilter) return false;

            String tags = item.getDietaryTags() != null ? item.getDietaryTags().toLowerCase() : "";
            if (filterChefSpecial && !tags.contains("chef special")) return false;
            if (filterGlutenFree && !tags.contains("gluten-free")) return false;
            if (filterVegetarian && !tags.contains("vegetarian")) return false;
            return true;
        }).collect(Collectors.toList());
    }

    private double computeCardWidth() {
        double usable = Math.max(currentViewportWidth - 20, 760);
        int cols;
        if (usable >= 1700) {
            cols = 4;
        } else if (usable >= 1020) {
            cols = 3;
        } else {
            cols = 2;
        }
        double totalGap = (cols - 1) * 24.0;
        return Math.floor((usable - totalGap) / cols);
    }

    private void renderMenuItems(boolean animate) {
        menuGrid.getChildren().clear();
        List<MenuItemDTO> filtered = getFilteredMenu();
        dishCountLabel.setText(filtered.size() + (filtered.size() == 1 ? " dish" : " dishes"));

        if (filtered.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setPadding(new Insets(40));
            Label title = new Label("No dishes match your active filters");
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #44403C;");
            Label sub = new Label("Try widening your prep time or clearing a dietary pill.");
            sub.getStyleClass().add("muted-text");
            empty.getChildren().addAll(title, sub);
            menuGrid.getChildren().add(empty);
            return;
        }

        double cardWidth = computeCardWidth();
        int idx = 0;
        for (MenuItemDTO item : filtered) {
            Pane card = cardViewMode ? buildDishCard(item, cardWidth) : buildDishListRow(item);
            UiAnimator.attachHoverLift(card);
            if (animate) {
                UiAnimator.playStaggeredEntrance(card, idx++);
            }
            menuGrid.getChildren().add(card);
        }
    }

    private VBox buildDishCard(MenuItemDTO item, double cardWidth) {
        VBox card = new VBox(0);
        card.setPrefWidth(cardWidth);
        card.setMaxWidth(cardWidth);
        card.getStyleClass().add("dish-card");

        double photoHeight = Math.round(cardWidth * 0.56);
        StackPane photo = DishImageCache.createTopRoundedPhoto(item.getImageKey(), item.getName(), cardWidth - 2, photoHeight, 17);

        if (item.getBadge() != null && !item.getBadge().isBlank()) {
            Label badge = new Label(item.getBadge());
            badge.getStyleClass().add("photo-badge");
            StackPane.setAlignment(badge, Pos.TOP_LEFT);
            StackPane.setMargin(badge, new Insets(12));
            photo.getChildren().add(badge);
        }

        HBox infoRow = new HBox(12);
        infoRow.setAlignment(Pos.TOP_LEFT);
        infoRow.setPadding(new Insets(14, 16, 16, 16));

        VBox textCol = new VBox(4);
        Label name = new Label(item.getName());
        name.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        name.setWrapText(true);
        name.setMinHeight(Region.USE_PREF_SIZE);

        Label meta = new Label(String.format("★ %.1f (%d) · %s · %d min",
                item.getRating(), item.getReviewCount(), item.getStation(), item.getPrepTimeMinutes()));
        meta.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #78716C;");
        textCol.getChildren().addAll(name, meta);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Label price = new Label(formatKsh(item.getPrice()));
        price.setStyle("-fx-font-size: 15.5px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        price.setMinWidth(Region.USE_PREF_SIZE);

        infoRow.getChildren().addAll(textCol, price);
        card.getChildren().addAll(photo, infoRow);

        card.setOnMouseClicked(e -> openDishDetailDrawer(item));
        return card;
    }

    private HBox buildDishListRow(MenuItemDTO item) {
        double rowWidth = Math.max(Math.min(currentViewportWidth - 24, 960), 620);
        HBox row = new HBox(18);
        row.setPrefWidth(rowWidth);
        row.setPadding(new Insets(12, 18, 12, 12));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dish-card");

        StackPane thumb = DishImageCache.createRoundedPhoto(item.getImageKey(), item.getName(), 132, 92, 13);

        VBox info = new VBox(5);
        HBox titleLine = new HBox(10);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(item.getName());
        name.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        titleLine.getChildren().add(name);
        if (item.getBadge() != null && !item.getBadge().isBlank()) {
            Label badge = new Label(item.getBadge());
            badge.getStyleClass().add("mod-chip");
            titleLine.getChildren().add(badge);
        }

        Label desc = new Label(item.getDescription());
        desc.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #78716C;");
        desc.setWrapText(true);

        Label meta = new Label(String.format("★ %.1f (%d) · %d min prep · %s Station",
                item.getRating(), item.getReviewCount(), item.getPrepTimeMinutes(), item.getStation()));
        meta.setStyle("-fx-font-size: 12px; -fx-text-fill: #8C857E; -fx-font-weight: 600;");
        info.getChildren().addAll(titleLine, desc, meta);
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox right = new VBox(6);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(Region.USE_PREF_SIZE);
        Label price = new Label(formatKsh(item.getPrice()));
        price.setStyle("-fx-font-size: 16.5px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        price.setMinWidth(Region.USE_PREF_SIZE);
        Label customize = new Label("Customize →");
        customize.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4A2E1B;");
        right.getChildren().addAll(price, customize);

        row.getChildren().addAll(thumb, info, right);
        row.setOnMouseClicked(e -> openDishDetailDrawer(item));
        return row;
    }

    // =========================================================================
    // RIGHT SIDEBAR: PURE WHITE PERSISTENT TABLE ORDER CART + LIVE TRACKER
    // =========================================================================
    private VBox buildPersistentCartSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.setPrefWidth(410);
        sidebar.setMinWidth(385);
        sidebar.setPadding(new Insets(28, 26, 24, 26));
        sidebar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EAE7E1; -fx-border-width: 0 0 0 1;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox titleCol = new VBox(2);
        Label title = new Label("Table Order");
        title.setStyle("-fx-font-size: 21px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        Label sub = new Label("Direct kitchen link active");
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #78716C;");
        titleCol.getChildren().addAll(title, sub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        ComboBox<String> guestPicker = new ComboBox<>();
        for (int g = 1; g <= 10; g++) guestPicker.getItems().add(g + (g == 1 ? " Guest" : " Guests"));
        guestPicker.setValue(guestCount + " Guests");
        guestPicker.getStyleClass().add("table-picker");
        guestPicker.setOnAction(e -> {
            if (guestPicker.getValue() != null) {
                guestCount = Integer.parseInt(guestPicker.getValue().split(" ")[0]);
            }
        });
        header.getChildren().addAll(titleCol, sp, guestPicker);

        liveTrackerBox = new VBox(8);
        renderLiveTracker();

        Label unsentLabel = new Label("NEW ITEMS · READY TO SEND");
        unsentLabel.getStyleClass().add("micro-caps");

        cartItemsContainer = new VBox(11);
        ScrollPane cartScroll = new ScrollPane(cartItemsContainer);
        cartScroll.setFitToWidth(true);
        cartScroll.getStyleClass().add("clean-scroll");
        VBox.setVgrow(cartScroll, Priority.ALWAYS);

        VBox footer = new VBox(10);
        footer.setPadding(new Insets(16, 0, 0, 0));
        footer.setStyle("-fx-border-color: #ECEAE5; -fx-border-width: 1 0 0 0;");

        HBox sentRow = new HBox();
        Label sentLbl = new Label("Active Kitchen Orders");
        sentLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #78716C;");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        sentTotalLabel = new Label("KSh 0");
        sentTotalLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #57534E;");
        sentRow.getChildren().addAll(sentLbl, sp2, sentTotalLabel);

        HBox subRow = new HBox();
        Label subLbl = new Label("New Items Subtotal");
        subLbl.setStyle("-fx-font-size: 14.5px; -fx-font-weight: 600; -fx-text-fill: #44403C;");
        Region sp3 = new Region();
        HBox.setHgrow(sp3, Priority.ALWAYS);
        cartSubtotalLabel = new Label("KSh 0");
        cartSubtotalLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        subRow.getChildren().addAll(subLbl, sp3, cartSubtotalLabel);

        sendOrderBtn = new Button("Select dishes to order");
        sendOrderBtn.setMaxWidth(Double.MAX_VALUE);
        sendOrderBtn.getStyleClass().add("espresso-btn");
        sendOrderBtn.setDisable(true);
        sendOrderBtn.setOnAction(e -> submitCurrentCartToKitchen());

        footer.getChildren().addAll(sentRow, subRow, sendOrderBtn);
        sidebar.getChildren().addAll(header, liveTrackerBox, unsentLabel, cartScroll, footer);

        renderCart();
        return sidebar;
    }

    private void renderLiveTracker() {
        liveTrackerBox.getChildren().clear();

        Optional<OrderDTO> latestOpt = activeTableOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED)
                .findFirst();

        double activeSum = activeTableOrders.stream()
                .mapToDouble(OrderDTO::getTotalPrice).sum();
        if (sentTotalLabel != null) {
            sentTotalLabel.setText(formatKsh(activeSum));
        }

        if (latestOpt.isEmpty()) {
            VBox idle = new VBox(4);
            idle.setStyle("-fx-background-color: #FAF9F7; -fx-border-color: #ECEAE5; -fx-border-radius: 15; -fx-background-radius: 15; -fx-padding: 14;");
            Label t = new Label("Kitchen Ready for Table " + currentTable);
            t.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold; -fx-text-fill: #44403C;");
            Label s = new Label("Orders sent from this tablet appear instantly on the Kitchen Display.");
            s.setWrapText(true);
            s.setStyle("-fx-font-size: 12px; -fx-text-fill: #78716C;");
            idle.getChildren().addAll(t, s);
            liveTrackerBox.getChildren().add(idle);
            return;
        }

        OrderDTO latest = latestOpt.get();
        VBox tracker = new VBox(10);
        tracker.setStyle("-fx-background-color: #FAF6F2; -fx-border-color: #4A2E1B; -fx-border-width: 1.4; -fx-border-radius: 15; -fx-background-radius: 15; -fx-padding: 14;");

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        String statusText = switch (latest.getStatus()) {
            case NEW -> "Received by Kitchen";
            case IN_PREP -> "Chef Preparing";
            case READY -> "Ready — Serving Now!";
            case COMPLETED -> "Served";
        };
        String badgeColor = switch (latest.getStatus()) {
            case NEW -> "#B45309";
            case IN_PREP -> "#4A2E1B";
            case READY, COMPLETED -> "#1E7E45";
        };

        Circle dot = new Circle(4.5, Color.web(badgeColor));
        UiAnimator.startPulse(dot);
        Label header = new Label("Order #" + latest.getId() + " · " + statusText);
        header.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label eta = new Label("~" + latest.getEstimatedPrepMinutes() + "m");
        eta.setMinWidth(Region.USE_PREF_SIZE);
        eta.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: " + badgeColor + ";");
        top.getChildren().addAll(dot, header, sp, eta);

        HBox steps = new HBox(8);
        steps.setAlignment(Pos.CENTER_LEFT);
        boolean s1 = true;
        boolean s2 = latest.getStatus() == OrderStatus.IN_PREP || latest.getStatus() == OrderStatus.READY;
        boolean s3 = latest.getStatus() == OrderStatus.READY;
        steps.getChildren().addAll(
                buildStepPill("1. Received", s1),
                buildStepPill("2. In Prep", s2),
                buildStepPill("3. Serving", s3)
        );

        tracker.getChildren().addAll(top, steps);
        liveTrackerBox.getChildren().add(tracker);
        UiAnimator.popHighlight(tracker);
    }

    private Label buildStepPill(String text, boolean reached) {
        Label l = new Label(text);
        l.setStyle(reached
                ? "-fx-background-color: #4A2E1B; -fx-text-fill: #FFFFFF; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 10; -fx-background-radius: 12;"
                : "-fx-background-color: #FFFFFF; -fx-border-color: #E5E2DC; -fx-border-radius: 12; -fx-text-fill: #8C857E; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 4 9; -fx-background-radius: 12;");
        return l;
    }

    private void renderCart() {
        cartItemsContainer.getChildren().clear();

        if (cartLines.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(48, 20, 48, 20));
            empty.setStyle("-fx-background-color: #FAF9F7; -fx-background-radius: 16; -fx-border-color: #F0EDE7; -fx-border-radius: 16;");
            Label t = new Label("Your table tray is empty");
            t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #44403C;");
            Label s = new Label("Tap any dish on the left to choose cook temperature, accompaniments, and notes.");
            s.setWrapText(true);
            s.setAlignment(Pos.CENTER);
            s.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #8C857E; -fx-text-alignment: center;");
            empty.getChildren().addAll(t, s);
            cartItemsContainer.getChildren().add(empty);

            cartSubtotalLabel.setText("KSh 0");
            sendOrderBtn.setText("Select dishes to order");
            sendOrderBtn.setDisable(true);
            return;
        }

        double subtotal = 0;
        int totalQty = 0;

        for (CartLine line : new ArrayList<>(cartLines)) {
            double lineTotal = line.unitPriceWithAddons * line.quantity;
            subtotal += lineTotal;
            totalQty += line.quantity;

            VBox card = new VBox(8);
            card.getStyleClass().add("cart-card");

            HBox top = new HBox(8);
            Label name = new Label(line.dish.getName());
            name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #181615;");
            name.setWrapText(true);
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label price = new Label(formatKsh(lineTotal));
            price.setMinWidth(Region.USE_PREF_SIZE);
            price.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #181615;");
            top.getChildren().addAll(name, sp, price);

            card.getChildren().add(top);

            if (line.customizations != null && !line.customizations.isBlank()) {
                FlowPane mods = new FlowPane(6, 5);
                for (String m : line.customizations.split("\\|")) {
                    Label chip = new Label(m.trim());
                    chip.getStyleClass().add("mod-chip");
                    mods.getChildren().add(chip);
                }
                card.getChildren().add(mods);
            }

            HBox bottom = new HBox(10);
            bottom.setAlignment(Pos.CENTER_LEFT);
            Button removeBtn = new Button("Remove");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9A3412; -fx-font-size: 12px; -fx-font-weight: 600; -fx-padding: 0; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> {
                cartLines.remove(line);
                renderCart();
            });

            Region sp2 = new Region();
            HBox.setHgrow(sp2, Priority.ALWAYS);

            HBox stepper = new HBox(12);
            stepper.setAlignment(Pos.CENTER);
            stepper.setStyle("-fx-background-color: #F4F3EF; -fx-background-radius: 16; -fx-padding: 3 10;");
            Button minus = new Button("−");
            minus.setStyle("-fx-background-color: transparent; -fx-font-weight: bold; -fx-padding: 0 4; -fx-cursor: hand;");
            minus.setOnAction(e -> {
                if (line.quantity > 1) line.quantity--;
                else cartLines.remove(line);
                renderCart();
            });
            Label qtyLbl = new Label(String.valueOf(line.quantity));
            qtyLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
            Button plus = new Button("+");
            plus.setStyle("-fx-background-color: transparent; -fx-font-weight: bold; -fx-padding: 0 4; -fx-cursor: hand;");
            plus.setOnAction(e -> {
                line.quantity++;
                renderCart();
            });
            stepper.getChildren().addAll(minus, qtyLbl, plus);

            bottom.getChildren().addAll(removeBtn, sp2, stepper);
            card.getChildren().add(bottom);
            cartItemsContainer.getChildren().add(card);
        }

        cartSubtotalLabel.setText(formatKsh(subtotal));
        sendOrderBtn.setText(String.format("Send %d %s to Kitchen · %s",
                totalQty, totalQty == 1 ? "Item" : "Items", formatKsh(subtotal)));
        sendOrderBtn.setDisable(false);
    }

    // =========================================================================
    // SLIDE-OVER DRAWER 1: FOOD DETAIL & CUSTOMIZATION SHEET
    // =========================================================================
    private void openDishDetailDrawer(MenuItemDTO dish) {
        detailDrawer.getChildren().clear();
        detailDrawer.setPadding(new Insets(26, 28, 24, 28));
        detailDrawer.setSpacing(16);

        final String[] selectedPrimaryOpt = { dish.isCustomizableTemp() ? "Med-Rare" : "Standard" };
        final double[] primaryExtraCost = { 0.0 };
        final double[] sidesExtraCost = { 0.0 };
        final int[] qty = { 1 };
        final Set<String> selectedSides = new LinkedHashSet<>();
        final Set<String> selectedNotes = new LinkedHashSet<>();

        Button addBtn = new Button();
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.getStyleClass().add("espresso-btn");

        Runnable updateBtn = () -> {
            double unit = dish.getPrice() + primaryExtraCost[0] + sidesExtraCost[0];
            addBtn.setText(String.format("Add %d to Table Order · %s", qty[0], formatKsh(unit * qty[0])));
        };

        StackPane hero = DishImageCache.createRoundedPhoto(dish.getImageKey(), dish.getName(), 464, 210, 16);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 20; -fx-min-width: 34; -fx-min-height: 34; -fx-font-weight: bold; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> closeAllDrawers());
        StackPane.setAlignment(closeBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(closeBtn, new Insets(12));

        Label statsBadge = new Label(String.format("★ %.1f (%d) · %d min prep · %s",
                dish.getRating(), dish.getReviewCount(), dish.getPrepTimeMinutes(), dish.getStation()));
        statsBadge.getStyleClass().add("photo-badge");
        StackPane.setAlignment(statsBadge, Pos.BOTTOM_LEFT);
        StackPane.setMargin(statsBadge, new Insets(12));
        hero.getChildren().addAll(closeBtn, statsBadge);

        VBox content = new VBox(16);

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.BASELINE_LEFT);
        Label title = new Label(dish.getName());
        title.setStyle("-fx-font-size: 21px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        title.setWrapText(true);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label basePrice = new Label(formatKsh(dish.getPrice()));
        basePrice.setMinWidth(Region.USE_PREF_SIZE);
        basePrice.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #4A2E1B;");
        titleRow.getChildren().addAll(title, sp, basePrice);

        Label desc = new Label(dish.getDescription());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 13.5px; -fx-text-fill: #57534E; -fx-line-spacing: 3px;");

        FlowPane tagPane = new FlowPane(8, 6);
        if (dish.getDietaryTags() != null && !dish.getDietaryTags().isBlank()) {
            for (String tag : dish.getDietaryTags().split(",")) {
                Label t = new Label(tag.trim());
                t.getStyleClass().add("mod-chip");
                tagPane.getChildren().add(t);
            }
        }

        String optTitle = dish.isCustomizableTemp() ? "COOK TEMPERATURE · REQUIRED" : "PORTION SIZE · REQUIRED";
        Label primaryLbl = new Label(optTitle);
        primaryLbl.getStyleClass().add("micro-caps");

        HBox selectorRow = new HBox(10);
        String[][] options = dish.isCustomizableTemp()
                ? new String[][]{{"Rare", "+0m", "0"}, {"Med-Rare", "Popular", "0"}, {"Medium", "+2m", "0"}, {"Well", "+5m", "0"}}
                : new String[][]{{"Standard", "Single", "0"}, {"Chef Share", "+KSh 800", "800"}, {"Tasting", "-KSh 300", "-300"}};

        List<VBox> boxNodes = new ArrayList<>();
        for (String[] opt : options) {
            VBox box = new VBox(3);
            box.setAlignment(Pos.CENTER);
            HBox.setHgrow(box, Priority.ALWAYS);
            boolean initiallyActive = opt[0].equals(selectedPrimaryOpt[0]);
            box.getStyleClass().add(initiallyActive ? "selector-box-active" : "selector-box");

            Label l1 = new Label(opt[0]);
            l1.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold; -fx-text-fill: #181615;");
            Label l2 = new Label(opt[1]);
            l2.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #78716C;");
            box.getChildren().addAll(l1, l2);

            box.setOnMouseClicked(e -> {
                selectedPrimaryOpt[0] = opt[0];
                primaryExtraCost[0] = Double.parseDouble(opt[2]);
                for (VBox b : boxNodes) b.getStyleClass().setAll("selector-box");
                box.getStyleClass().setAll("selector-box-active");
                UiAnimator.popHighlight(box);
                updateBtn.run();
            });
            boxNodes.add(box);
            selectorRow.getChildren().add(box);
        }

        Label sidesLbl = new Label("ACCOMPANIMENTS & ENHANCEMENTS (OPTIONAL)");
        sidesLbl.getStyleClass().add("micro-caps");

        VBox sidesList = new VBox(8);
        String[][] sides = {
                {"Truffle Parmesan Fries", "550"},
                {"Roasted Garlic Herb Butter", "250"},
                {"Black Truffle Jus", "350"}
        };
        for (String[] s : sides) {
            String sideName = s[0];
            double cost = Double.parseDouble(s[1]);
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("selector-box");

            CheckBox cb = new CheckBox(sideName);
            cb.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #181615;");
            Region spS = new Region();
            HBox.setHgrow(spS, Priority.ALWAYS);
            Label priceLbl = new Label("+KSh " + s[1]);
            priceLbl.setMinWidth(Region.USE_PREF_SIZE);
            priceLbl.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: #4A2E1B;");

            Runnable toggleSide = () -> {
                if (cb.isSelected()) {
                    selectedSides.add("+" + sideName);
                    sidesExtraCost[0] += cost;
                    row.getStyleClass().setAll("selector-box-active");
                } else {
                    selectedSides.remove("+" + sideName);
                    sidesExtraCost[0] -= cost;
                    row.getStyleClass().setAll("selector-box");
                }
                updateBtn.run();
            };
            cb.setOnAction(e -> toggleSide.run());
            row.setOnMouseClicked(e -> {
                if (e.getTarget() != cb) {
                    cb.setSelected(!cb.isSelected());
                    toggleSide.run();
                }
            });
            row.getChildren().addAll(cb, spS, priceLbl);
            sidesList.getChildren().add(row);
        }

        Label notesLbl = new Label("QUICK KITCHEN PREFERENCES");
        notesLbl.getStyleClass().add("micro-caps");
        FlowPane notesPane = new FlowPane(8, 8);
        for (String note : new String[]{"Sauce on Side", "Less Salt", "Split Plate", "Allergy Alert", "Extra Crisp"}) {
            Button chip = new Button(note);
            chip.getStyleClass().add("pill-btn");
            chip.setOnAction(e -> {
                if (selectedNotes.contains(note)) {
                    selectedNotes.remove(note);
                    chip.getStyleClass().setAll("pill-btn");
                } else {
                    selectedNotes.add(note);
                    chip.getStyleClass().setAll("pill-btn-active");
                }
            });
            notesPane.getChildren().add(chip);
        }

        content.getChildren().addAll(titleRow, desc, tagPane, primaryLbl, selectorRow, sidesLbl, sidesList, notesLbl, notesPane);
        ScrollPane contentScroll = new ScrollPane(content);
        contentScroll.setFitToWidth(true);
        contentScroll.getStyleClass().add("clean-scroll");
        VBox.setVgrow(contentScroll, Priority.ALWAYS);

        HBox bottomBar = new HBox(14);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(14, 0, 0, 0));
        bottomBar.setStyle("-fx-border-color: #ECEAE5; -fx-border-width: 1 0 0 0;");

        HBox stepper = new HBox(16);
        stepper.setAlignment(Pos.CENTER);
        stepper.setStyle("-fx-background-color: #F4F3EF; -fx-background-radius: 26; -fx-padding: 10 18;");
        Button minus = new Button("−");
        minus.setStyle("-fx-background-color: transparent; -fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand;");
        Label qtyLbl = new Label("1");
        qtyLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Button plus = new Button("+");
        plus.setStyle("-fx-background-color: transparent; -fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand;");

        minus.setOnAction(e -> { if (qty[0] > 1) { qty[0]--; qtyLbl.setText(String.valueOf(qty[0])); updateBtn.run(); } });
        plus.setOnAction(e -> { qty[0]++; qtyLbl.setText(String.valueOf(qty[0])); updateBtn.run(); });
        stepper.getChildren().addAll(minus, qtyLbl, plus);

        HBox.setHgrow(addBtn, Priority.ALWAYS);
        addBtn.setOnAction(e -> {
            List<String> modParts = new ArrayList<>();
            modParts.add(selectedPrimaryOpt[0]);
            modParts.addAll(selectedSides);
            modParts.addAll(selectedNotes);
            String modString = String.join(" | ", modParts);

            double finalUnitPrice = dish.getPrice() + primaryExtraCost[0] + sidesExtraCost[0];
            cartLines.add(new CartLine(dish, qty[0], finalUnitPrice, modString));
            renderCart();
            closeAllDrawers();
            UiAnimator.showToast(rootStack, "Added " + qty[0] + "× " + dish.getName() + " to Table " + currentTable);
        });
        updateBtn.run();

        bottomBar.getChildren().addAll(stepper, addBtn);
        detailDrawer.getChildren().addAll(hero, contentScroll, bottomBar);

        filterDrawer.setVisible(false);
        UiAnimator.slideDrawerIn(backdrop, detailDrawer, 520);
    }

    // =========================================================================
    // SLIDE-OVER DRAWER 2: STAFF DESIGNER FILTER + LIVE PREP-TIME HISTOGRAM
    // =========================================================================
    private VBox buildFilterDrawer() {
        VBox drawer = new VBox(22);
        drawer.getStyleClass().add("drawer-sheet");
        drawer.setPrefWidth(430);
        drawer.setMaxWidth(430);
        drawer.setPadding(new Insets(28));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Button close = new Button("✕");
        close.setStyle("-fx-background-color: #F5F4F0; -fx-background-radius: 20; -fx-min-width: 34; -fx-min-height: 34; -fx-font-weight: bold; -fx-cursor: hand;");
        close.setOnAction(e -> closeAllDrawers());
        Label title = new Label("Customize & Filter");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button reset = new Button("Reset");
        reset.setStyle("-fx-background-color: transparent; -fx-text-fill: #78716C; -fx-font-weight: bold; -fx-cursor: hand;");
        top.getChildren().addAll(close, title, sp, reset);

        Label histHeader = new Label("Prep Time Distribution");
        histHeader.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        Label histSub = new Label("Up to 25 min · Median 12 min");
        histSub.getStyleClass().add("muted-text");

        HBox histogram = new HBox(6);
        histogram.setAlignment(Pos.BOTTOM_CENTER);
        histogram.setPrefHeight(90);
        int[] bucketHeights = {20, 38, 62, 82, 74, 58, 46, 30, 18, 12};
        int[] bucketMins    = {4,  6,  8,  10, 12, 14, 16, 18, 20, 24};
        List<Region> bars = new ArrayList<>();
        for (int h : bucketHeights) {
            Region bar = new Region();
            bar.setPrefSize(26, h);
            bar.setStyle("-fx-background-color: #4A2E1B; -fx-background-radius: 4 4 0 0;");
            bars.add(bar);
            histogram.getChildren().add(bar);
        }

        Slider prepSlider = new Slider(5, 25, 25);

        Label priceHeader = new Label("Maximum Dish Price");
        priceHeader.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        Label priceSub = new Label("Up to KSh 5,500");
        priceSub.getStyleClass().add("muted-text");
        Slider priceSlider = new Slider(1000, 5500, 5500);

        Button applyBtn = new Button("Show Matching Dishes");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.getStyleClass().add("espresso-btn");

        Runnable updateHistogramPreview = () -> {
            maxPrepFilter = (int) Math.round(prepSlider.getValue());
            maxPriceFilter = Math.round(priceSlider.getValue() / 100.0) * 100.0;
            histSub.setText("Up to " + maxPrepFilter + " min · Median 12 min");
            priceSub.setText("Up to " + formatKsh(maxPriceFilter));
            for (int i = 0; i < bars.size(); i++) {
                boolean active = bucketMins[i] <= maxPrepFilter;
                bars.get(i).setStyle(active
                        ? "-fx-background-color: #4A2E1B; -fx-background-radius: 4 4 0 0;"
                        : "-fx-background-color: #E5E2DC; -fx-background-radius: 4 4 0 0;");
            }
            int count = getFilteredMenu().size();
            applyBtn.setText("Show " + count + " Matching Dishes");
        };

        prepSlider.valueProperty().addListener((o, ov, nv) -> updateHistogramPreview.run());
        priceSlider.valueProperty().addListener((o, ov, nv) -> updateHistogramPreview.run());

        reset.setOnAction(e -> {
            prepSlider.setValue(25);
            priceSlider.setValue(5500);
            updateHistogramPreview.run();
        });

        applyBtn.setOnAction(e -> {
            renderFilterChips();
            renderMenuItems(true);
            closeAllDrawers();
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        drawer.getChildren().addAll(top, new Separator(), histHeader, histSub, histogram, prepSlider,
                priceHeader, priceSub, priceSlider, spacer, applyBtn);
        return drawer;
    }

    private void closeAllDrawers() {
        if (detailDrawer.isVisible()) UiAnimator.slideDrawerOut(backdrop, detailDrawer, 520);
        if (filterDrawer.isVisible()) UiAnimator.slideDrawerOut(backdrop, filterDrawer, 430);
    }

    // =========================================================================
    // SERVER API & REAL-TIME WEBSOCKET SYNC
    // =========================================================================
    private void loadMenuFromServer() {
        api.fetchMenu().thenAccept(items -> Platform.runLater(() -> {
            this.allMenuItems = items;
            renderMenuItems(true);
        })).exceptionally(ex -> {
            Platform.runLater(() -> dishCountLabel.setText("Server offline — start runServer first"));
            return null;
        });
    }

    private void refreshTableOrders() {
        api.fetchTableOrders(currentTable).thenAccept(orders -> Platform.runLater(() -> {
            this.activeTableOrders = new ArrayList<>(orders);
            renderLiveTracker();
        }));
    }

    private void submitCurrentCartToKitchen() {
        if (cartLines.isEmpty()) return;
        sendOrderBtn.setDisable(true);
        sendOrderBtn.setText("Sending to kitchen...");

        OrderDTO payload = new OrderDTO();
        payload.setTableNumber(currentTable);
        payload.setGuestCount(guestCount);

        List<OrderItemDTO> items = new ArrayList<>();
        for (CartLine line : cartLines) {
            items.add(new OrderItemDTO(
                    null, line.dish.getId(), line.dish.getName(), line.quantity,
                    line.unitPriceWithAddons, line.dish.getStation(), line.customizations, false
            ));
        }
        payload.setItems(items);

        api.submitOrder(payload).thenAccept(created -> Platform.runLater(() -> {
            cartLines.clear();
            renderCart();
            upsertActiveOrder(created);
            UiAnimator.showToast(rootStack, "Order #" + created.getId() + " sent directly to Kitchen Display!");
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                renderCart();
                UiAnimator.showToast(rootStack, "Failed to reach server — check that runServer is active");
            });
            return null;
        });
    }

    private void connectRealTimeSync() {
        api.connectWebSocket(
                order -> {
                    if (order.getTableNumber() == currentTable) {
                        upsertActiveOrder(order);
                        if (order.getStatus() == OrderStatus.READY) {
                            UiAnimator.showToast(rootStack, "Order #" + order.getId() + " is READY and on its way to Table " + currentTable + "!");
                        }
                    }
                },
                connected -> {
                    syncDot.setFill(Color.web(connected ? "#1E7E45" : "#DC2626"));
                    syncStatusLabel.setText(connected ? "Kitchen Live Sync · Connected" : "Reconnecting to server...");
                }
        );
    }

    private void upsertActiveOrder(OrderDTO updated) {
        activeTableOrders.removeIf(o -> Objects.equals(o.getId(), updated.getId()));
        activeTableOrders.add(0, updated);
        renderLiveTracker();
    }
}