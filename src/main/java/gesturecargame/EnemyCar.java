package com.gesturecargame;

import java.util.ArrayList;
import java.util.List;

// ================================================================
//  EnemyCar.java — Enemy Vehicle (Plain Data Class)
// ================================================================
//
//  WHAT CHANGED FROM YOUR VERSION:
//
//  ❌ OLD: extended Rectangle (JavaFX scene graph node)
//         → wastes memory, adds change listeners, wrong tool for canvas
//  ✅ NEW: plain Java class with double x, y fields
//         → lightweight, no JavaFX overhead, correct for canvas drawing
//
//  ❌ OLD: new Random() inside respawn() — creates a new object every call
//  ✅ NEW: single static Random instance shared across all enemies
//
//  ❌ OLD: fixed speed = 6 forever
//  ✅ NEW: base speed + difficulty multiplier (increases as game progresses)
//
//  ❌ OLD: could pick same lane 3 times in a row
//  ✅ NEW: tracks last lane, avoids immediate repeat
//
//  COORDINATE SYSTEM:
//    x = left edge of enemy car on screen (pixels)
//    y = top  edge of enemy car on screen (pixels)
//    Enemies start ABOVE the screen (negative y) and fall DOWN.
// ================================================================

import java.util.Random;

public class EnemyCar {

    // ── Size constants ─────────────────────────────────────────
    // PUBLIC so GameEngine can use them for collision + drawing
    public static final double WIDTH  =80;
    public static final double HEIGHT = 150;

    // ── Lane layout ────────────────────────────────────────────
    // X-positions for the LEFT edge of each lane's car.
    // These align with the 3-lane road drawn in Road.java.
    // TUNE THESE if your road is at a different position.
    private static final double[] LANE_X = { 170, 300, 430};

    // ── Shared random — ONE instance for ALL EnemyCar objects ──
    // static = shared by every instance of this class
    // (contrast with your old version: new Random() every respawn call)
    private static final Random RANDOM = new Random();

    // ── Position ───────────────────────────────────────────────
    private double x;           // Left edge X on canvas
    private double y;           // Top  edge Y on canvas

    // ── Speed ──────────────────────────────────────────────────
    // BASE_SPEED:   the slowest an enemy will ever move (pixels/sec)
    // speedMultiplier: increased by GameEngine as score goes up
    private static final double BASE_SPEED = 180;   // pixels per second

    // Each enemy has its own slight speed variation (+/- 15%)
    // so the 3 enemies don't all move in perfect lockstep
    private final double speedVariation;

    // Global multiplier — GameEngine calls setSpeedMultiplier() to speed up
    private double speedMultiplier = 1.0;

    // ── Lane tracking ─────────────────────────────────────────
    // Tracks which lane this enemy is CURRENTLY in.
    // Used to avoid spawning in the same lane twice in a row.
    private int currentLane = -1;   // -1 = not yet placed

    // ── Constructor ───────────────────────────────────────────

    /**
     * Create a new enemy car.
     * Does NOT spawn it yet — call respawn() after construction.
     *
     * @param initialSpeedMultiplier  Starting difficulty (1.0 = normal)
     */
    public EnemyCar(double initialSpeedMultiplier) {
        this.speedMultiplier = initialSpeedMultiplier;

        // Give each enemy a slightly different base speed (±15%)
        // 0.85 to 1.15 range
        this.speedVariation = 0.85 + RANDOM.nextDouble() * 0.30;
    }

    // ── Update (called every game frame) ─────────────────────

    /**
     * Move the enemy downward by its speed × elapsed time.
     *
     * WHY USE deltaTime?
     *   Without it, enemies move  speed pixels per FRAME.
     *   If your game runs at 30fps, they move half as fast as 60fps.
     *   Multiplying by deltaTime gives  pixels per SECOND — consistent
     *   at any frame rate.
     *
     * @param dt  Time since last frame in seconds (e.g. 0.016 at 60fps)
     */
    public void update(double dt) {
        y += BASE_SPEED * speedVariation * speedMultiplier * dt;
    }

    // ── Respawn (called when enemy exits bottom of screen) ────

    /**
     * Teleport this enemy back to a random lane above the top of the screen.
     *
     * ANTI-REPEAT LOGIC:
     *   We never pick the same lane twice in a row.
     *   This prevents the frustrating situation where an enemy
     *   immediately re-spawns in the lane you just dodged.
     *
     * SPAWN HEIGHT:
     *   We randomise how far above the screen (-100 to -400 px).
     *   This staggers the enemies so they don't all arrive at once.
     */
public void respawn(List<EnemyCar> enemies) {

    // ----------------------------------------------------
    // PICK A DIFFERENT LANE
    // ----------------------------------------------------

    int newLane;

    do {
        newLane = RANDOM.nextInt(LANE_X.length);
    } while (newLane == currentLane && LANE_X.length > 1);

    currentLane = newLane;

    x = LANE_X[newLane];

    // ----------------------------------------------------
    // SAFE SPAWN HEIGHT
    // ----------------------------------------------------

    boolean safe;

    do {

        safe = true;

        // Random spawn above screen
        y = -100 - RANDOM.nextInt(500);

        // Check distance from OTHER enemies
        for (EnemyCar other : enemies) {

            
            // Don't compare with self
            if (other == this)
                continue;

            // Same lane?
            if (other.currentLane == this.currentLane) {

                // Too close vertically?
                if (Math.abs(other.y - this.y) < 250) {

                    safe = false;

                    break;
                }
            }
        }

    } while (!safe);
}

    // ── Difficulty scaling ────────────────────────────────────

    /**
     * Set the global speed multiplier for this enemy.
     * Called by GameEngine when the player's score increases.
     *
     * Example:
     *   score 0–500   → multiplier = 1.0  (normal)
     *   score 500+    → multiplier = 1.3  (30% faster)
     *   score 1500+   → multiplier = 1.6  (60% faster)
     *   score 3000+   → multiplier = 2.0  (double speed — very hard!)
     *
     * @param multiplier  Speed factor (1.0 = normal, 2.0 = double speed)
     */
    public void setSpeedMultiplier(double multiplier) {
        // Cap at 3.0 so the game stays humanly possible
        this.speedMultiplier = Math.min(multiplier, 3.0);
    }

    // ── Collision helper ──────────────────────────────────────

    /**
     * Check if this enemy overlaps with the player car.
     *
     * AXIS-ALIGNED BOUNDING BOX (AABB) collision.
     * Most beginner 2D games use this — it's fast and good enough.
     *
     * HOW IT WORKS:
     *   Two rectangles overlap ONLY IF:
     *     they overlap on the X axis  AND  on the Y axis.
     *   We check both at once using the "no gap" test.
     *
     * SHRINK FACTOR:
     *   We shrink both hitboxes slightly (8px on each side).
     *   This gives the player a small mercy zone — the VISUAL
     *   sprites can slightly overlap before it counts as a hit.
     *   Feels fairer and less frustrating.
     *
     * @param playerX  Left edge of player car
     * @param playerY  Top  edge of player car
     * @return true if the two cars overlap (collision!)
     */
    public boolean collidesWith(double playerX, double playerY) {
        final double SHRINK = 8;   // Forgiveness margin in pixels

        double myLeft   = x           + SHRINK;
        double myRight  = x + WIDTH   - SHRINK;
        double myTop    = y           + SHRINK;
        double myBottom = y + HEIGHT  - SHRINK;

        double plLeft   = playerX           + SHRINK;
        double plRight  = playerX + Car.WIDTH   - SHRINK;
        double plTop    = playerY           + SHRINK;
        double plBottom = playerY + Car.HEIGHT  - SHRINK;

        // "No gap on either axis" = overlap
        boolean xOverlap = plLeft  < myRight  && plRight  > myLeft;
        boolean yOverlap = plTop   < myBottom && plBottom > myTop;

        return xOverlap && yOverlap;
    }

    // ── Getters ───────────────────────────────────────────────

    /** Left edge X coordinate on the canvas. */
    public double getX() { return x; }

    /** Top edge Y coordinate on the canvas. */
    public double getY() { return y; }

    /**
     * True when this enemy has fully scrolled past the bottom of the screen.
     * GameEngine calls this to know when to respawn the enemy.
     *
     * @param screenHeight  Height of the canvas in pixels
     */
    public boolean isOffScreen(double screenHeight) {
        return y > screenHeight + 10;
    }

    /** Current effective speed (pixels/sec) — useful for debug display. */
    public double getEffectiveSpeed() {
        return BASE_SPEED * speedVariation * speedMultiplier;
    }
}