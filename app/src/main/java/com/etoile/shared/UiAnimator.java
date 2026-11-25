package com.etoile.shared;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class UiAnimator {

    // Apple/iOS-like smooth cubic ease-out curve
    public static final Interpolator EASE_OUT = Interpolator.SPLINE(0.22, 1.0, 0.36, 1.0);

    public static void playStaggeredEntrance(Node node, int index) {
        node.setOpacity(0);
        node.setTranslateY(14);

        FadeTransition ft = new FadeTransition(Duration.millis(260), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.setInterpolator(EASE_OUT);

        TranslateTransition tt = new TranslateTransition(Duration.millis(280), node);
        tt.setFromY(14);
        tt.setToY(0);
        tt.setInterpolator(EASE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setDelay(Duration.millis(Math.min(index * 38L, 260L)));
        pt.play();
    }

    public static void attachHoverLift(Node node) {
        node.setOnMouseEntered(e -> {
            TranslateTransition up = new TranslateTransition(Duration.millis(170), node);
            up.setToY(-3.5);
            up.setInterpolator(EASE_OUT);
            up.play();
        });
        node.setOnMouseExited(e -> {
            TranslateTransition down = new TranslateTransition(Duration.millis(190), node);
            down.setToY(0);
            down.setInterpolator(EASE_OUT);
            down.play();
        });
        node.setOnMousePressed(e -> {
            ScaleTransition press = new ScaleTransition(Duration.millis(90), node);
            press.setToX(0.985);
            press.setToY(0.985);
            press.play();
        });
        node.setOnMouseReleased(e -> {
            ScaleTransition release = new ScaleTransition(Duration.millis(130), node);
            release.setToX(1.0);
            release.setToY(1.0);
            release.play();
        });
    }

    public static void slideDrawerIn(Node backdrop, Node drawer, double drawerWidth) {
        backdrop.setVisible(true);
        backdrop.setOpacity(0);
        FadeTransition bgIn = new FadeTransition(Duration.millis(220), backdrop);
        bgIn.setToValue(1.0);
        bgIn.play();

        drawer.setVisible(true);
        drawer.setTranslateX(drawerWidth);
        TranslateTransition slide = new TranslateTransition(Duration.millis(300), drawer);
        slide.setFromX(drawerWidth);
        slide.setToX(0);
        slide.setInterpolator(EASE_OUT);
        slide.play();
    }

    public static void slideDrawerOut(Node backdrop, Node drawer, double drawerWidth) {
        if (!drawer.isVisible()) return;

        FadeTransition bgOut = new FadeTransition(Duration.millis(200), backdrop);
        bgOut.setToValue(0);
        bgOut.setOnFinished(e -> backdrop.setVisible(false));
        bgOut.play();

        TranslateTransition slide = new TranslateTransition(Duration.millis(240), drawer);
        slide.setToX(drawerWidth);
        slide.setInterpolator(Interpolator.EASE_IN);
        slide.setOnFinished(e -> {
            drawer.setVisible(false);
            drawer.setTranslateX(0);
        });
        slide.play();
    }

    public static void startPulse(Node dot) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(1100), dot);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.45);
        pulse.setToY(1.45);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);

        FadeTransition fade = new FadeTransition(Duration.millis(1100), dot);
        fade.setFromValue(1.0);
        fade.setToValue(0.45);
        fade.setAutoReverse(true);
        fade.setCycleCount(Animation.INDEFINITE);

        new ParallelTransition(pulse, fade).play();
    }

    public static void popHighlight(Node node) {
        ScaleTransition pop = new ScaleTransition(Duration.millis(140), node);
        pop.setFromX(0.95);
        pop.setFromY(0.95);
        pop.setToX(1.0);
        pop.setToY(1.0);
        pop.setInterpolator(EASE_OUT);
        pop.play();
    }

    public static void showToast(StackPane root, String message) {
        HBox toast = new HBox(10);
        toast.setAlignment(Pos.CENTER);
        toast.getStyleClass().add("toast-pill");
        toast.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);

        Label lbl = new Label("✓  " + message);
        lbl.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 13px; -fx-font-weight: bold;");
        toast.getChildren().add(lbl);

        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        toast.setTranslateY(-40);
        toast.setOpacity(0);
        root.getChildren().add(toast);

        TranslateTransition in = new TranslateTransition(Duration.millis(260), toast);
        in.setToY(24);
        in.setInterpolator(EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), toast);
        fadeIn.setToValue(1);

        PauseTransition hold = new PauseTransition(Duration.millis(2200));

        TranslateTransition out = new TranslateTransition(Duration.millis(240), toast);
        out.setToY(-30);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), toast);
        fadeOut.setToValue(0);

        SequentialTransition seq = new SequentialTransition(
                new ParallelTransition(in, fadeIn),
                hold,
                new ParallelTransition(out, fadeOut)
        );
        seq.setOnFinished(e -> root.getChildren().remove(toast));
        seq.play();
    }
}