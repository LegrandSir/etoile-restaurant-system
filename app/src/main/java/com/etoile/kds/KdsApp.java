package com.etoile.kds;

import com.etoile.shared.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class KdsApp extends Application {

    private final ApiClient api = new ApiClient();

    // Live State
    private final Map<Long, OrderDTO> ordersById = new LinkedHashMap<>();
    private final Set<Long> prioritizedOrderIds = new HashSet<>();
    private final Map<Long, Label> liveTimerLabels = new HashMap<>();

    private String statusFilter = "ALL";       // ALL, NEW, IN_PREP, READY
    private String stationFilter = "All";      // All, Grill, Fryer, Pass, Bar
    private boolean cardViewMode = true;
    private double boardViewportWidth = 1200;
    private int completedSessionCount = 0;

    // UI References
    private StackPane rootStack;
    private Label clockLabel;
    private Label syncStatusLabel;
    private Circle syncDot;
    private Label histogramSubtitle;
    private HBox histogramBarsBox;
    private HBox statusFilterBox;
    private HBox stationChipBar;
    private FlowPane ticketBoard;
    private Label boardSummaryLabel;
    private Timeline clockAndTimerTimeline;

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void start(Stage stage) {
        rootStack = new StackPane();
        rootStack.setStyle("-fx-background-color: #F8F8F6;");

        BorderPane layout = new BorderPane();
        layout.setTop(buildTopHeaderBar());
        layout.setCenter(buildMainBoardArea());

        rootStack.getChildren().add(layout);

        Scene scene = new Scene(rootStack, 1380, 860);
        var css = getClass().getResource("/etoile-theme.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("L'Étoile Kitchen — Real-Time Kitchen Display System (KDS)");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (clockAndTimerTimeline != null) clockAndTimerTimeline.stop();
            api.shutdown();
        });
        stage.show();

        startLiveTimers();
        loadActiveOrders();
        connectRealTimeSync();
    }

    // =========================================================================
    // TOP NAVIGATION BAR (PURE WHITE, HISTOGRAM, TWO-LINE COUNT BOXES, CLOCK)
    // =========================================================================
    private VBox buildTopHeaderBar() {
        VBox headerWrapper = new VBox(0);
        headerWrapper.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #EAE7E1; -fx-border-width: 0 0 1 0;");

        HBox topRow = new HBox(24);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(20, 32, 18, 32));

        // 1. Left: Brand + Live Sync + Local Clock
        VBox brandCol = new VBox(3);
        Label brand = new Label("L’Étoile Kitchen");
        brand.getStyleClass().add("brand-title");

        HBox liveRow = new HBox(8);
        liveRow.setAlignment(Pos.CENTER_LEFT);
        syncDot = new Circle(4.5, Color.web("#D97706"));
        UiAnimator.startPulse(syncDot);
        syncStatusLabel = new Label("KDS · Connecting...");
        syncStatusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #57534E;");

        Label sep = new Label("·");
        sep.setStyle("-fx-text-fill: #A8A29E;");

        clockLabel = new Label(LocalTime.now().format(CLOCK_FMT));
        clockLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4A2E1B; -fx-font-family: 'Consolas', 'Segoe UI', monospace;");

        liveRow.getChildren().addAll(syncDot, syncStatusLabel, sep, clockLabel);
        brandCol.getChildren().addAll(brand, liveRow);

        // 2. Center: Ticket Prep Time Distribution Mini-Histogram
        VBox histCol = new VBox(4);
        histCol.setAlignment(Pos.CENTER_LEFT);
        histCol.setPadding(new Insets(0, 12, 0, 12));
        Label histTitle = new Label("Ticket Prep Time Distribution");
        histTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        histogramSubtitle = new Label("0 active tickets · median 0m 00s");
        histogramSubtitle.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #78716C;");

        histogramBarsBox = new HBox(4);
        histogramBarsBox.setAlignment(Pos.BOTTOM_LEFT);
        histogramBarsBox.setPrefHeight(30);
        histogramBarsBox.setMinHeight(30);
        histCol.getChildren().addAll(histTitle, histogramSubtitle, histogramBarsBox);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 3. Right: Two-Line Status Filter Boxes (Staff Designer Pattern)
        statusFilterBox = new HBox(10);
        statusFilterBox.setAlignment(Pos.CENTER);

        // 4. Card / Compact Toggle Switch
        VBox toggleCol = new VBox(4);
        toggleCol.setAlignment(Pos.CENTER);
        HBox viewToggle = new HBox(3);
        viewToggle.getStyleClass().add("segment-container");
        Button cardBtn = new Button("Card");
        Button compactBtn = new Button("Compact");
        cardBtn.getStyleClass().add("segment-btn-active");
        compactBtn.getStyleClass().add("segment-btn");

        cardBtn.setOnAction(e -> {
            if (!cardViewMode) {
                cardViewMode = true;
                cardBtn.getStyleClass().setAll("segment-btn-active");
                compactBtn.getStyleClass().setAll("segment-btn");
                renderBoard(true);
            }
        });
        compactBtn.setOnAction(e -> {
            if (cardViewMode) {
                cardViewMode = false;
                compactBtn.getStyleClass().setAll("segment-btn-active");
                cardBtn.getStyleClass().setAll("segment-btn");
                renderBoard(true);
            }
        });
        viewToggle.getChildren().addAll(cardBtn, compactBtn);
        toggleCol.getChildren().add(viewToggle);

        topRow.getChildren().addAll(brandCol, histCol, spacer, statusFilterBox, toggleCol);

        // Sub-bar: Station Filter Pills + Refresh
        HBox subRow = new HBox(10);
        subRow.setAlignment(Pos.CENTER_LEFT);
        subRow.setPadding(new Insets(10, 32, 12, 32));
        subRow.setStyle("-fx-background-color: #FCFBF9; -fx-border-color: #F0EDE7; -fx-border-width: 1 0 0 0;");

        Label stationLbl = new Label("STATION:");
        stationLbl.getStyleClass().add("micro-caps");

        stationChipBar = new HBox(8);
        stationChipBar.setAlignment(Pos.CENTER_LEFT);
        renderStationChips();

        Region subSpacer = new Region();
        HBox.setHgrow(subSpacer, Priority.ALWAYS);

        boardSummaryLabel = new Label("Served this session: 0");
        boardSummaryLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #78716C;");

        Button refreshBtn = new Button("↻ Sync Now");
        refreshBtn.getStyleClass().add("pill-btn");
        refreshBtn.setOnAction(e -> {
            loadActiveOrders();
            UiAnimator.showToast(rootStack, "Synced live kitchen queue");
        });

        subRow.getChildren().addAll(stationLbl, stationChipBar, subSpacer, boardSummaryLabel, refreshBtn);

        headerWrapper.getChildren().addAll(topRow, subRow);
        renderStatusFilterBoxes();
        updateHistogram();
        return headerWrapper;
    }

    private void renderStatusFilterBoxes() {
        statusFilterBox.getChildren().clear();

        List<OrderDTO> active = getActiveOrdersList();
        long newCount = active.stream().filter(o -> o.getStatus() == OrderStatus.NEW).count();
        long prepCount = active.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREP).count();
        long readyCount = active.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();

        String[][] defs = {
                {"ALL", "All", String.valueOf(active.size())},
                {"NEW", "New", String.valueOf(newCount)},
                {"IN_PREP", "In Prep", String.valueOf(prepCount)},
                {"READY", "Ready", String.valueOf(readyCount)}
        };

        for (String[] d : defs) {
            boolean selected = statusFilter.equals(d[0]);
            VBox box = new VBox(2);
            box.setAlignment(Pos.CENTER);
            box.setPrefWidth(78);
            box.getStyleClass().add(selected ? "selector-box-active" : "selector-box");

            Label title = new Label(d[1]);
            title.setStyle(selected
                    ? "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4A2E1B;"
                    : "-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #57534E;");
            Label count = new Label(d[2]);
            count.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #181615;");

            box.getChildren().addAll(title, count);
            box.setOnMouseClicked(e -> {
                statusFilter = d[0];
                renderStatusFilterBoxes();
                renderBoard(true);
            });
            statusFilterBox.getChildren().add(box);
        }
    }

    private void renderStationChips() {
        stationChipBar.getChildren().clear();
        for (String st : new String[]{"All", "Grill", "Fryer", "Pass", "Bar"}) {
            boolean active = stationFilter.equalsIgnoreCase(st);
            Button chip = new Button(st.equals("All") ? "All Stations" : st);
            chip.getStyleClass().add(active ? "pill-btn-active" : "pill-btn");
            chip.setOnAction(e -> {
                stationFilter = st;
                renderStationChips();
                renderBoard(true);
            });
            stationChipBar.getChildren().add(chip);
        }
    }

    private void updateHistogram() {
        histogramBarsBox.getChildren().clear();
        List<OrderDTO> active = getActiveOrdersList();

        // 12 buckets (0-1m, 1-2m, ..., 11m+)
        int[] buckets = new int[12];
        List<Long> elapsedSecsList = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (OrderDTO o : active) {
            long sec = Math.max(0, (now - o.getCreatedAtEpochMs()) / 1000);
            elapsedSecsList.add(sec);
            int idx = (int) Math.min(buckets.length - 1, sec / 90); // 1.5-min buckets
            buckets[idx]++;
        }

        int maxBucket = 1;
        for (int b : buckets) if (b > maxBucket) maxBucket = b;

        for (int i = 0; i < buckets.length; i++) {
            double h = buckets[i] == 0 ? 4 : Math.max(8, (buckets[i] / (double) maxBucket) * 28.0);
            Region bar = new Region();
            bar.setPrefSize(8, h);
            bar.setMinHeight(h);
            bar.setMaxHeight(h);
            String color = buckets[i] == 0 ? "#E7E4DE" : (i >= 8 ? "#B91C1C" : "#4A2E1B");
            bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3 3 0 0;");
            histogramBarsBox.getChildren().add(bar);
        }

        long medianSec = 0;
        if (!elapsedSecsList.isEmpty()) {
            Collections.sort(elapsedSecsList);
            medianSec = elapsedSecsList.get(elapsedSecsList.size() / 2);
        }
        histogramSubtitle.setText(String.format("%d active %s · median %dm %02ds",
                active.size(), active.size() == 1 ? "ticket" : "tickets",
                medianSec / 60, medianSec % 60));
    }

    // =========================================================================
    // MAIN TICKET BOARD AREA
    // =========================================================================
    private ScrollPane buildMainBoardArea() {
        ticketBoard = new FlowPane(22, 22);
        ticketBoard.setPadding(new Insets(26, 32, 32, 32));
        ticketBoard.setStyle("-fx-background-color: #F8F8F6;");

        ScrollPane scroll = new ScrollPane(ticketBoard);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("clean-scroll");
        scroll.setStyle("-fx-background: #F8F8F6; -fx-background-color: #F8F8F6;");

        scroll.viewportBoundsProperty().addListener((obs, oldB, newB) -> {
            double w = newB.getWidth();
            if (w > 400 && Math.abs(w - boardViewportWidth) > 40) {
                boardViewportWidth = w;
                renderBoard(false);
            }
        });
        return scroll;
    }

    private List<OrderDTO> getActiveOrdersList() {
        return ordersById.values().stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED)
                .collect(Collectors.toList());
    }

    private List<OrderDTO> getFilteredOrders() {
        return getActiveOrdersList().stream()
                .filter(o -> statusFilter.equals("ALL") || o.getStatus().name().equals(statusFilter))
                .filter(o -> stationFilter.equals("All") || o.getItems().stream()
                        .anyMatch(i -> i.getStation() != null && i.getStation().equalsIgnoreCase(stationFilter)))
                .sorted((a, b) -> {
                    // Prioritized (Rush) tickets float to top, then oldest ticket first (FIFO)
                    boolean pa = prioritizedOrderIds.contains(a.getId());
                    boolean pb = prioritizedOrderIds.contains(b.getId());
                    if (pa != pb) return pa ? -1 : 1;
                    return Long.compare(a.getCreatedAtEpochMs(), b.getCreatedAtEpochMs());
                })
                .collect(Collectors.toList());
    }

    private double computeTicketCardWidth() {
        double usable = Math.max(boardViewportWidth - 68, 760);
        int cols = usable >= 1650 ? 4 : (usable >= 1060 ? 3 : 2);
        double totalGap = (cols - 1) * 22.0;
        return Math.floor((usable - totalGap) / cols);
    }

    private void renderBoard(boolean animate) {
        ticketBoard.getChildren().clear();
        liveTimerLabels.clear();

        boardSummaryLabel.setText("Served this session: " + completedSessionCount);
        List<OrderDTO> visible = getFilteredOrders();

        if (visible.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPrefWidth(Math.max(boardViewportWidth - 80, 600));
            empty.setPadding(new Insets(90, 24, 90, 24));
            empty.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 20; -fx-border-color: #ECEAE5; -fx-border-radius: 20;");

            Label t = new Label("All caught up — no active tickets in this view");
            t.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #292524;");
            Label s = new Label("Place an order from the Customer Tablet and it will appear here instantaneously.");
            s.setStyle("-fx-font-size: 13.5px; -fx-text-fill: #78716C;");
            empty.getChildren().addAll(t, s);
            ticketBoard.getChildren().add(empty);
            return;
        }

        double cardWidth = computeTicketCardWidth();
        int idx = 0;
        for (OrderDTO order : visible) {
            Pane node = cardViewMode ? buildTicketCard(order, cardWidth) : buildCompactTicketRow(order);
            if (animate) {
                UiAnimator.playStaggeredEntrance(node, idx++);
            }
            ticketBoard.getChildren().add(node);
        }
    }

    private VBox buildTicketCard(OrderDTO order, double width) {
        boolean isRush = prioritizedOrderIds.contains(order.getId());
        boolean isInPrep = order.getStatus() == OrderStatus.IN_PREP;
        boolean isReady = order.getStatus() == OrderStatus.READY;

        VBox card = new VBox(14);
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        card.setPadding(new Insets(18, 20, 18, 20));

        String borderColor = isRush ? "#B91C1C" : (isReady ? "#1E7E45" : (isInPrep ? "#4A2E1B" : "#E5E2DC"));
        String borderWidth = (isRush || isInPrep || isReady) ? "1.6" : "1.0";
        card.setStyle(
                "-fx-background-color: #FFFFFF; " +
                "-fx-background-radius: 18; " +
                "-fx-border-color: " + borderColor + "; " +
                "-fx-border-width: " + borderWidth + "; " +
                "-fx-border-radius: 18; " +
                "-fx-effect: dropshadow(gaussian, rgba(24,22,21,0.06), 14, 0, 0, 4);"
        );

        // 1. Ticket Header: Table & Order # + Rush Pin + Live Ticking Timer
        HBox header = new HBox(10);
        header.setAlignment(Pos.TOP_LEFT);

        VBox titleCol = new VBox(3);
        Label tableTitle = new Label("Table " + order.getTableNumber() + " · Order #" + order.getId());
        tableTitle.setStyle("-fx-font-size: 16.5px; -fx-font-weight: bold; -fx-text-fill: #181615;");

        String statusLabel = switch (order.getStatus()) {
            case NEW -> "New Ticket";
            case IN_PREP -> "In Preparation";
            case READY -> "Ready at Pass";
            case COMPLETED -> "Completed";
        };
        Label guestsAndStatus = new Label(order.getGuestCount() + " Guests · " + statusLabel);
        guestsAndStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #78716C; -fx-font-weight: 600;");
        titleCol.getChildren().addAll(tableTitle, guestsAndStatus);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button rushBtn = new Button(isRush ? "★ Rush" : "Rush");
        rushBtn.setStyle(isRush
                ? "-fx-background-color: #FEF2F2; -fx-border-color: #DC2626; -fx-border-radius: 12; -fx-background-radius: 12; -fx-text-fill: #B91C1C; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 9; -fx-cursor: hand;"
                : "-fx-background-color: #F7F6F3; -fx-border-color: #E5E2DC; -fx-border-radius: 12; -fx-background-radius: 12; -fx-text-fill: #78716C; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 4 9; -fx-cursor: hand;");
        rushBtn.setOnAction(e -> {
            if (prioritizedOrderIds.contains(order.getId())) {
                prioritizedOrderIds.remove(order.getId());
            } else {
                prioritizedOrderIds.add(order.getId());
                UiAnimator.showToast(rootStack, "Prioritized Order #" + order.getId() + " (Table " + order.getTableNumber() + ") to front of queue");
            }
            renderBoard(true);
        });

        Label timerBadge = createTimerBadge(order);
        header.getChildren().addAll(titleCol, sp, rushBtn, timerBadge);

        // 2. Station Count Pills (e.g. Grill · 2 | Fryer · 1 | Pass · 1)
        FlowPane stationPills = new FlowPane(8, 6);
        Map<String, Integer> stationCounts = new LinkedHashMap<>();
        for (OrderItemDTO item : order.getItems()) {
            String st = item.getStation() != null ? item.getStation() : "Pass";
            stationCounts.merge(st, item.getQuantity(), Integer::sum);
        }
        for (var entry : stationCounts.entrySet()) {
            Label pill = new Label(entry.getKey() + " · " + entry.getValue());
            pill.setStyle("-fx-background-color: #F6F5F2; -fx-border-color: #E5E2DC; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 3 10; -fx-font-size: 11.5px; -fx-font-weight: 600; -fx-text-fill: #44403C;");
            stationPills.getChildren().add(pill);
        }

        // 3. Interactive Item Checklist Rows
        VBox itemsContainer = new VBox(10);
        itemsContainer.setPadding(new Insets(10, 0, 10, 0));
        itemsContainer.setStyle("-fx-border-color: #F0EDE7; -fx-border-width: 1 0 1 0;");

        for (OrderItemDTO item : order.getItems()) {
            VBox itemRow = new VBox(5);
            itemRow.setPadding(new Insets(6, 8, 6, 8));
            itemRow.setStyle(item.isPrepared()
                    ? "-fx-background-color: #FAF9F7; -fx-background-radius: 10; -fx-cursor: hand;"
                    : "-fx-background-color: #FFFFFF; -fx-background-radius: 10; -fx-cursor: hand;");

            HBox topLine = new HBox(10);
            topLine.setAlignment(Pos.CENTER_LEFT);

            StackPane checkBoxVisual = new StackPane();
            checkBoxVisual.setPrefSize(22, 22);
            checkBoxVisual.setMinSize(22, 22);
            checkBoxVisual.setStyle(item.isPrepared()
                    ? "-fx-background-color: #4A2E1B; -fx-background-radius: 6;"
                    : "-fx-background-color: #FFFFFF; -fx-border-color: #D6D3CE; -fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6;");
            if (item.isPrepared()) {
                Label tick = new Label("✓");
                tick.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 12px; -fx-font-weight: bold;");
                checkBoxVisual.getChildren().add(tick);
            }

            Label dishTitle = new Label(item.getQuantity() + "× " + item.getItemName());
            dishTitle.setWrapText(true);
            dishTitle.setStyle(item.isPrepared()
                    ? "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #A8A29E;"
                    : "-fx-font-size: 14.5px; -fx-font-weight: bold; -fx-text-fill: #181615;");
            HBox.setHgrow(dishTitle, Priority.ALWAYS);

            Label stTag = new Label(item.getStation());
            stTag.setMinWidth(Region.USE_PREF_SIZE);
            stTag.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #8C857E;");

            topLine.getChildren().addAll(checkBoxVisual, dishTitle, stTag);
            itemRow.getChildren().add(topLine);

            if (item.getCustomizations() != null && !item.getCustomizations().isBlank()) {
                FlowPane mods = new FlowPane(6, 5);
                mods.setPadding(new Insets(0, 0, 0, 32));
                for (String mod : item.getCustomizations().split("\\|")) {
                    Label chip = new Label(mod.trim());
                    boolean isAlert = mod.toLowerCase().contains("allergy");
                    chip.setStyle(isAlert
                            ? "-fx-background-color: #FEF2F2; -fx-border-color: #FCA5A5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 2 8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #B91C1C;"
                            : "-fx-background-color: #FFFFFF; -fx-border-color: #E5E2DC; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 2 8; -fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #57534E;");
                    mods.getChildren().add(chip);
                }
                itemRow.getChildren().add(mods);
            }

            itemRow.setOnMouseClicked(e -> {
                if (item.getId() != null) {
                    api.toggleOrderItem(order.getId(), item.getId())
                            .thenAccept(updated -> Platform.runLater(() -> upsertOrder(updated, false)));
                }
            });
            itemsContainer.getChildren().add(itemRow);
        }

        Region verticalSpacer = new Region();
        VBox.setVgrow(verticalSpacer, Priority.ALWAYS);

        // 4. Primary Action Button
        int totalItemsCount = order.getItems().stream().mapToInt(OrderItemDTO::getQuantity).sum();
        Button actionBtn = buildTicketActionButton(order, totalItemsCount);

        card.getChildren().addAll(header, stationPills, itemsContainer, verticalSpacer, actionBtn);
        return card;
    }

    private HBox buildCompactTicketRow(OrderDTO order) {
        double rowWidth = Math.max(boardViewportWidth - 70, 780);
        boolean isRush = prioritizedOrderIds.contains(order.getId());

        HBox row = new HBox(18);
        row.setPrefWidth(rowWidth);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 20, 14, 20));
        row.setStyle(
                "-fx-background-color: #FFFFFF; -fx-background-radius: 15; " +
                "-fx-border-color: " + (isRush ? "#B91C1C" : "#E5E2DC") + "; " +
                "-fx-border-width: " + (isRush ? "1.6" : "1") + "; -fx-border-radius: 15;"
        );

        VBox left = new VBox(3);
        left.setPrefWidth(195);
        left.setMinWidth(195);
        Label t = new Label((isRush ? "★ " : "") + "Table " + order.getTableNumber() + " · #" + order.getId());
        t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #181615;");
        Label s = new Label(order.getGuestCount() + " Guests · " + order.getStatus().name().replace("_", " "));
        s.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #78716C; -fx-font-weight: 600;");
        left.getChildren().addAll(t, s);

        Label timerBadge = createTimerBadge(order);

        FlowPane summaryItems = new FlowPane(8, 6);
        HBox.setHgrow(summaryItems, Priority.ALWAYS);
        for (OrderItemDTO item : order.getItems()) {
            String text = item.getQuantity() + "× " + item.getItemName()
                    + (item.getCustomizations() != null && !item.getCustomizations().isBlank() ? " (" + item.getCustomizations() + ")" : "");
            Label chip = new Label((item.isPrepared() ? "✓ " : "") + text);
            chip.setStyle(item.isPrepared()
                    ? "-fx-background-color: #F5EFEA; -fx-border-color: #4A2E1B; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 5 10; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4A2E1B; -fx-cursor: hand;"
                    : "-fx-background-color: #F8F7F4; -fx-border-color: #E5E2DC; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 5 10; -fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #181615; -fx-cursor: hand;");
            chip.setOnMouseClicked(e -> {
                if (item.getId() != null) {
                    api.toggleOrderItem(order.getId(), item.getId())
                            .thenAccept(updated -> Platform.runLater(() -> upsertOrder(updated, false)));
                }
            });
            summaryItems.getChildren().add(chip);
        }

        int totalItemsCount = order.getItems().stream().mapToInt(OrderItemDTO::getQuantity).sum();
        Button actionBtn = buildTicketActionButton(order, totalItemsCount);
        actionBtn.setPrefWidth(210);
        actionBtn.setMinWidth(210);

        row.getChildren().addAll(left, timerBadge, summaryItems, actionBtn);
        return row;
    }

    private Button buildTicketActionButton(OrderDTO order, int itemCount) {
        String noun = itemCount == 1 ? "Item" : "Items";
        Button btn = new Button();
        btn.setMaxWidth(Double.MAX_VALUE);

        switch (order.getStatus()) {
            case NEW -> {
                btn.setText("Start Prep (" + itemCount + " " + noun + ")");
                btn.getStyleClass().add("outline-btn");
                btn.setOnAction(e -> transitionOrder(order.getId(), OrderStatus.IN_PREP,
                        "Order #" + order.getId() + " moved to In Prep"));
            }
            case IN_PREP -> {
                btn.setText("Bump " + itemCount + " " + noun + " to Pass");
                btn.getStyleClass().add("espresso-btn");
                btn.setOnAction(e -> transitionOrder(order.getId(), OrderStatus.READY,
                        "Order #" + order.getId() + " marked READY — Table " + order.getTableNumber() + " notified!"));
            }
            case READY -> {
                btn.setText("✓ Mark Served & Clear");
                btn.setStyle("-fx-background-color: #1E7E45; -fx-background-radius: 26; -fx-text-fill: #FFFFFF; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 13 20; -fx-cursor: hand;");
                btn.setOnAction(e -> {
                    completedSessionCount++;
                    transitionOrder(order.getId(), OrderStatus.COMPLETED,
                            "Order #" + order.getId() + " served and cleared from board");
                });
            }
            default -> btn.setDisable(true);
        }
        return btn;
    }

    private Label createTimerBadge(OrderDTO order) {
        Label badge = new Label();
        badge.setMinWidth(Region.USE_PREF_SIZE);
        updateSingleTimerBadge(badge, order.getCreatedAtEpochMs());
        liveTimerLabels.put(order.getId(), badge);
        return badge;
    }

    private void updateSingleTimerBadge(Label badge, long createdAtMs) {
        long elapsedSec = Math.max(0, (System.currentTimeMillis() - createdAtMs) / 1000);
        long mins = elapsedSec / 60;
        long secs = elapsedSec % 60;
        badge.setText(String.format("⏱ %02d:%02d", mins, secs));

        if (mins >= 15) {
            badge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #B91C1C; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
        } else if (mins >= 10) {
            badge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #B45309; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
        } else {
            badge.setStyle("-fx-background-color: #F4F2EE; -fx-text-fill: #44403C; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
        }
    }

    // =========================================================================
    // LIVE 1-SECOND CLOCK + SERVER API + WEBSOCKET SYNC
    // =========================================================================
    private void startLiveTimers() {
        clockAndTimerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(LocalTime.now().format(CLOCK_FMT));
            for (var entry : liveTimerLabels.entrySet()) {
                OrderDTO order = ordersById.get(entry.getKey());
                if (order != null) {
                    updateSingleTimerBadge(entry.getValue(), order.getCreatedAtEpochMs());
                }
            }
            // Refresh histogram every 10 seconds
            if (LocalTime.now().getSecond() % 10 == 0) {
                updateHistogram();
            }
        }));
        clockAndTimerTimeline.setCycleCount(Timeline.INDEFINITE);
        clockAndTimerTimeline.play();
    }

    private void loadActiveOrders() {
        api.fetchActiveOrders().thenAccept(orders -> Platform.runLater(() -> {
            ordersById.clear();
            for (OrderDTO o : orders) {
                ordersById.put(o.getId(), o);
            }
            renderStatusFilterBoxes();
            updateHistogram();
            renderBoard(true);
        })).exceptionally(ex -> {
            Platform.runLater(() -> syncStatusLabel.setText("Server offline — start runServer"));
            return null;
        });
    }

    private void transitionOrder(Long orderId, OrderStatus nextStatus, String toastMessage) {
        api.updateOrderStatus(orderId, nextStatus).thenAccept(updated -> Platform.runLater(() -> {
            upsertOrder(updated, false);
            UiAnimator.showToast(rootStack, toastMessage);
        }));
    }

    private void connectRealTimeSync() {
        api.connectWebSocket(
                order -> upsertOrder(order, true),
                connected -> {
                    syncDot.setFill(Color.web(connected ? "#1E7E45" : "#DC2626"));
                    syncStatusLabel.setText(connected ? "KDS · Live Sync" : "KDS · Reconnecting...");
                }
        );
    }

    private void upsertOrder(OrderDTO order, boolean fromWebSocket) {
        boolean isBrandNew = !ordersById.containsKey(order.getId()) && order.getStatus() != OrderStatus.COMPLETED;

        if (order.getStatus() == OrderStatus.COMPLETED) {
            ordersById.remove(order.getId());
            prioritizedOrderIds.remove(order.getId());
        } else {
            ordersById.put(order.getId(), order);
        }

        renderStatusFilterBoxes();
        updateHistogram();
        renderBoard(isBrandNew);

        if (fromWebSocket && isBrandNew) {
            int qty = order.getItems().stream().mapToInt(OrderItemDTO::getQuantity).sum();
            UiAnimator.showToast(rootStack, "New Ticket: Table " + order.getTableNumber() + " · Order #" + order.getId() + " (" + qty + " items)");
        }
    }
}