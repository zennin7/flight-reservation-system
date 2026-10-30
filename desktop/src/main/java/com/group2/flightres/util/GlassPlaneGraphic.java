package com.group2.flightres.util;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.ArrayList;
import java.util.List;

/**
 * Side-view vector aircraft representation for the SkyVoyage Glass Seat Map screen.
 * Exactly renders the fuselage, wing, engine, wheels, cabin windows, and dynamic section
 * highlight box with pin badge matching design-glass/seat-map.html.
 */
public class GlassPlaneGraphic extends Pane {

    private final Rectangle hlBox = new Rectangle();
    private final Circle pinCircle = new Circle();
    private final SVGPath pinCube = new SVGPath();
    private final Label sectionLabel = new Label("SECTION 1");
    private final List<Rectangle> windowRects = new ArrayList<>();

    public GlassPlaneGraphic() {
        setPrefSize(900, 190);
        setMinSize(900, 190);
        setMaxSize(900, 190);

        // 1. Shadow beneath fuselage
        Ellipse shadow = new Ellipse(450, 176, 380, 7);
        shadow.setFill(Color.web("rgba(0,0,0,0.35)"));

        // 2. Wing behind/under fuselage
        SVGPath wing = new SVGPath();
        wing.setContent("M440 112 L640 112 L700 160 L560 160 Z");
        LinearGradient wingGrad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#e8ecf4", 0.90)),
                new Stop(1, Color.web("#9aa5bb", 0.70)));
        wing.setFill(wingGrad);

        // 3. Main fuselage
        SVGPath fus = new SVGPath();
        fus.setContent("M30 108 C30 84 70 66 130 64 L780 64 C820 64 850 56 872 24 L892 24 L874 96 C862 124 822 134 772 134 L110 134 C60 134 30 124 30 108 Z");
        LinearGradient fusGrad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffffff", 0.95)),
                new Stop(1, Color.web("#cfd6e4", 0.78)));
        fus.setFill(fusGrad);

        // 4. Cockpit windshield
        SVGPath cockpit = new SVGPath();
        cockpit.setContent("M58 88 L96 84 L102 96 L64 98 Z");
        cockpit.setFill(Color.web("#1a2233"));
        cockpit.setOpacity(0.85);

        // 5. Highlight section bounding box
        hlBox.setY(70);
        hlBox.setHeight(58);
        hlBox.setArcWidth(28);
        hlBox.setArcHeight(28);
        hlBox.setFill(Color.web("rgba(212,166,60,0.28)"));
        hlBox.setStroke(Color.web("#e8c46a"));
        hlBox.setStrokeWidth(2);

        // 6. Windows (38 total windows from x=128 to 744 every 16px)
        for (int x = 128; x < 745; x += 16) {
            Rectangle win = new Rectangle(x, 84, 9, 12);
            win.setArcWidth(8);
            win.setArcHeight(8);
            windowRects.add(win);
        }

        // 7. Engine nacelle
        Ellipse engOuter = new Ellipse(410, 150, 46, 17);
        engOuter.setFill(Color.web("#e4e8f1"));
        Ellipse engInner = new Ellipse(368, 150, 8, 14);
        engInner.setFill(Color.web("#8793aa"));

        // 8. Landing gear wheels
        Circle wheelFront = new Circle(125, 152, 9);
        wheelFront.setFill(Color.web("#1a2233"));
        Circle wheelRear = new Circle(520, 156, 10);
        wheelRear.setFill(Color.web("#1a2233"));

        // 9. Section Pin / Badge (circle + cube icon + section label)
        pinCircle.setRadius(19);
        pinCircle.setCenterY(99);
        pinCircle.setFill(Color.web("#0b1220"));
        pinCircle.setStroke(Color.web("rgba(255,255,255,0.55)"));
        pinCircle.setStrokeWidth(2);

        pinCube.setContent("M0 0l9 5v10l-9 5l-9 -5v-10z M0 10l9 -5 M0 10v10 M0 10l-9 -5");
        pinCube.setFill(Color.TRANSPARENT);
        pinCube.setStroke(Color.web("#e8c46a"));
        pinCube.setStrokeWidth(1.8);
        pinCube.setStrokeLineCap(StrokeLineCap.ROUND);
        pinCube.setStrokeLineJoin(StrokeLineJoin.ROUND);

        sectionLabel.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #f3d98b; -fx-alignment: center;");
        sectionLabel.setLayoutY(48);
        sectionLabel.setPrefWidth(120);
        sectionLabel.setAlignment(Pos.CENTER);

        // Assemble all shapes
        getChildren().addAll(shadow, wing, fus, cockpit, hlBox);
        getChildren().addAll(windowRects);
        getChildren().addAll(engOuter, engInner, wheelFront, wheelRear, pinCircle, pinCube, sectionLabel);

        // Default to section 1
        setSection(1);
    }

    public void setSection(int section) {
        double hlX;
        double hlW;
        double cx;
        double winStart;
        double winEnd;

        switch (section) {
            case 2 -> {
                hlX = 338;
                hlW = 220;
                cx = 448;
                winStart = 338;
                winEnd = 558;
                sectionLabel.setText("SECTION 2");
            }
            case 3 -> {
                hlX = 558;
                hlW = 190;
                cx = 653;
                winStart = 558;
                winEnd = 748;
                sectionLabel.setText("SECTION 3");
            }
            default -> { // Section 1
                hlX = 118;
                hlW = 220;
                cx = 228;
                winStart = 126;
                winEnd = 330;
                sectionLabel.setText("SECTION 1");
            }
        }

        hlBox.setX(hlX);
        hlBox.setWidth(hlW);

        pinCircle.setCenterX(cx);
        pinCube.setLayoutX(cx);
        pinCube.setLayoutY(90);
        sectionLabel.setLayoutX(cx - 60);

        for (Rectangle win : windowRects) {
            double wx = win.getX();
            if (wx >= winStart && wx <= winEnd) {
                win.setFill(Color.web("#e8c46a"));
            } else {
                win.setFill(Color.web("#8793aa"));
            }
        }
    }
}
