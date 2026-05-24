package com.gesturecargame;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Road.java — Scrolling Road Renderer
 * ======================================
 * This class draws the road on a JavaFX Canvas each frame.
 * It creates the illusion of forward movement by scrolling
 * dashed lane dividers downward.
 *
 * BEGINNER: WHY REDRAW EVERY FRAME?
 * JavaFX Canvas is like an Etch A Sketch — to "animate" it,
 * we clear it and redraw everything ~60 times per second.
 * The GraphicsContext (gc) is the "pen" we draw with.
 *
 * SCROLLING ILLUSION:
 * We maintain a list of lane-dash Y positions.
 * Each frame, we move them downward by (speed * deltaTime).
 * When a dash goes off-screen, we recycle it to the top.
 * Result: infinite scrolling road.
 */
public class Road {

    // ── ROAD DIMENSIONS ──────────────────────────────────────────────────────
    private final double sceneWidth;
    private final double sceneHeight;

    /** X coordinate of road's left edge */
    private double roadLeft;

    /** X coordinate of road's right edge */
    private double roadRight;

    /** Width of the road */
    private double roadWidth;

    // ── ROAD APPEARANCE ───────────────────────────────────────────────────────
    private static final Color BACKGROUND_COLOUR = Color.rgb(30, 30, 30);   // dark grey
    private static final Color ROAD_COLOUR       = Color.rgb(60, 60, 60);   // medium grey
    private static final Color KERB_COLOUR       = Color.rgb(200, 80, 80);  // red kerb strip
    private static final Color DASH_COLOUR       = Color.WHITE;
    private static final Color GRASS_COLOUR      = Color.rgb(34, 85, 34);   // dark green

    // Road takes up 60% of the screen width, centred
    private static final double ROAD_WIDTH_FRACTION = 0.60;

    // ── LANE DIVIDER DASHES ───────────────────────────────────────────────────
    private static final double DASH_HEIGHT  = 40.0;  // height of each white dash
    private static final double DASH_GAP     = 60.0;  // space between dashes
    private static final double DASH_WIDTH   = 8.0;   // width of each dash

    /** Y positions of all dashes for the centre lane divider */
    private final List<Double> dashPositions = new ArrayList<>();

    /** How far down the dashes have scrolled (fractional, for smooth movement) */
    private double scrollOffset = 0.0;

    // ── OBSTACLE TRACKER (for future use) ─────────────────────────────────────
    // We expose roadLeft/roadRight so GameEngine can place obstacles on the road

    // ─────────────────────────────────────────────────────────────────────────

    public Road(double sceneWidth, double sceneHeight) {
        this.sceneWidth  = sceneWidth;
        this.sceneHeight = sceneHeight;

        // Calculate road boundaries
        roadWidth = sceneWidth * ROAD_WIDTH_FRACTION;
        roadLeft  = (sceneWidth - roadWidth) / 2.0;
        roadRight = roadLeft + roadWidth;

        initialiseDashes();
    }

    /**
     * Initialise the starting Y positions of all lane dashes.
     * We need enough dashes to fill the screen height, plus a couple extra
     * so there's no gap when recycling from bottom to top.
     */
    private void initialiseDashes() {
        double period = DASH_HEIGHT + DASH_GAP;
        // Start from above the screen so dashes scroll in smoothly
        for (double y = -DASH_HEIGHT; y < sceneHeight + period; y += period) {
            dashPositions.add(y);
        }
    }

    // ── UPDATE (call every frame) ──────────────────────────────────────────────

    /**
     * Scroll the dashes downward to simulate forward movement.
     *
     * @param speed     car's current forward speed (px/sec)
     * @param deltaTime seconds since last frame
     */
    public void update(double speed, double deltaTime) {
        double scrollAmount = speed * deltaTime;

        // Move every dash down
        for (int i = 0; i < dashPositions.size(); i++) {
            dashPositions.set(i, dashPositions.get(i) + scrollAmount);
        }

        // Recycle any dash that has scrolled off the bottom of the screen
        // by moving it back to just above the top
        double period = DASH_HEIGHT + DASH_GAP;
        for (int i = 0; i < dashPositions.size(); i++) {
            if (dashPositions.get(i) > sceneHeight) {
                dashPositions.set(i, dashPositions.get(i) - period * dashPositions.size());
            }
        }
    }

    // ── DRAW (call every frame AFTER update) ──────────────────────────────────

    /**
     * Draw the entire road scene: background, grass, road, kerbs, dashes.
     *
     * @param gc GraphicsContext from the Canvas (the "pen" for drawing)
     *
     * DRAWING ORDER MATTERS (painter's algorithm — back to front):
     *   1. Background (dark grey) — fills everything
     *   2. Grass (green sides)
     *   3. Road surface (grey strip)
     *   4. Kerb strips (red edge lines)
     *   5. Centre lane dashes (white)
     */
    public void draw(GraphicsContext gc) {
        // ── 1. Background ─────────────────────────────────────────────────────
        gc.setFill(BACKGROUND_COLOUR);
        gc.fillRect(0, 0, sceneWidth, sceneHeight);

        // ── 2. Grass ──────────────────────────────────────────────────────────
        gc.setFill(GRASS_COLOUR);
        gc.fillRect(0, 0, roadLeft, sceneHeight);                          // left grass
        gc.fillRect(roadRight, 0, sceneWidth - roadRight, sceneHeight);    // right grass

        // ── 3. Road surface ───────────────────────────────────────────────────
        gc.setFill(ROAD_COLOUR);
        gc.fillRect(roadLeft, 0, roadWidth, sceneHeight);

        // ── 4. Kerb strips (1.5% of scene width) ─────────────────────────────
        double kerbW = sceneWidth * 0.015;
        gc.setFill(KERB_COLOUR);
        gc.fillRect(roadLeft,        0, kerbW, sceneHeight);   // left kerb
        gc.fillRect(roadRight - kerbW, 0, kerbW, sceneHeight); // right kerb

        // ── 5. Lane divider dashes ────────────────────────────────────────────
        double centreX = (roadLeft + roadRight) / 2.0 - DASH_WIDTH / 2.0;
        gc.setFill(DASH_COLOUR);
        for (double dashY : dashPositions) {
            gc.fillRect(centreX, dashY, DASH_WIDTH, DASH_HEIGHT);
        }
    }

    // ── GETTERS ───────────────────────────────────────────────────────────────

    public double getRoadLeft()  { return roadLeft; }
    public double getRoadRight() { return roadRight; }
    public double getRoadWidth() { return roadWidth; }
}