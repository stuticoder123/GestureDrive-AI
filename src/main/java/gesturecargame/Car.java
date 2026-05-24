package com.gesturecargame;

/**
 * Car.java — Car Model (Physics + State)
 * ========================================
 * This class represents the player's car.
 * It holds position, speed, and handles SMOOTH MOVEMENT via interpolation.
 *
 * DESIGN PRINCIPLE: MODEL vs VIEW
 * Car.java is the MODEL — it stores data and has movement logic.
 * GameEngine.java is the VIEW/CONTROLLER — it draws the car and receives input.
 * Keeping them separate means you can change the drawing style without
 * touching movement physics, and vice versa.
 *
 * SMOOTH MOVEMENT EXPLANATION:
 * We use "lerp" (linear interpolation) to smooth position changes.
 * Instead of jumping x=100 → x=200 instantly, we glide gradually:
 *   x = x + (target_x - x) * factor
 * Each frame, x gets factor% closer to target_x.  Factor=0.12 → 12% per frame.
 * This feels natural because movement starts fast then slows as it nears target.
 */
public class Car {

    // ── SCREEN POSITION ───────────────────────────────────────────────────────
    /** Current rendered X position (centre of car) — updated by lerp every frame */
    private double x;

    /** Current rendered Y position (centre of car) — car stays mostly fixed vertically */
    private double y;

    /** Target X we're smoothly moving toward */
    private double targetX;

  public static final double WIDTH  = 60.0;
public static final double HEIGHT = 110.0;  // pixels tall

    // ── MOVEMENT PHYSICS ─────────────────────────────────────────────────────
    /** Current forward speed (pixels per second on the "road") */
    private double speed = 0.0;

    /** Maximum speed cap */
    public static final double MAX_SPEED  = 500.0;  // px/sec

    /** How fast speed increases when accelerating */
    public static final double ACCEL_RATE = 300.0;  // px/sec²

    /** How fast speed decreases when braking */
    public static final double BRAKE_RATE = 600.0;  // px/sec²

    /** How fast speed naturally decreases when no input (friction) */
    public static final double FRICTION   = 80.0;   // px/sec²

    /**
     * Horizontal lerp factor per frame.
     * Higher = snappier steering, lower = more floaty.
     * 0.12 is a good starting value — adjust to taste.
     */
    private static final double STEER_LERP = 0.12;

    // ── LANE BOUNDARIES ──────────────────────────────────────────────────────
    /** Left boundary X (car cannot go past this) */
    private double leftBound;

    /** Right boundary X */
    private double rightBound;

    // ── STEERING STATE ────────────────────────────────────────────────────────
    /** How many pixels to move per LEFT/RIGHT command per second */
    private static final double STEER_SPEED = 320.0;

    /** Current steering direction: -1 = left, 0 = none, +1 = right */
    private int steerDirection = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructor — create a new car at the given starting position.
     *
     * @param startX     initial centre X position
     * @param startY     initial centre Y position
     * @param leftBound  left edge the car cannot pass
     * @param rightBound right edge the car cannot pass
     */
    public Car(double startX, double startY, double leftBound, double rightBound) {
        this.x          = startX;
        this.y          = startY;
        this.targetX    = startX;
        this.leftBound  = leftBound;
        this.rightBound = rightBound;
    }

    // ── PUBLIC COMMAND METHODS (called by game engine with gesture input) ─────

    /**
     * Tell the car to steer LEFT.
     * Sets steerDirection; actual position update happens in update().
     */
    public void steerLeft() {
        steerDirection = -1;
    }

    /**
     * Tell the car to steer RIGHT.
     */
    public void steerRight() {
        steerDirection = +1;
    }

    /**
     * No lateral steering input — car stays centred.
     */
    public void steerCenter() {
        steerDirection = 0;
    }

    /**
     * Apply acceleration — increase speed toward MAX_SPEED.
     *
     * @param deltaTime seconds since last frame (e.g. 0.016 for 60fps)
     */
    public void accelerate(double deltaTime) {
        speed = Math.min(speed + ACCEL_RATE * deltaTime, MAX_SPEED);
    }

    /**
     * Apply braking — stop the car immediately.
     */
    public void brake(double deltaTime) {
        speed = 0.0;
    }

    /**
     * Apply natural friction (no input) — gently slow down.
     * This makes the car feel physical rather than binary on/off.
     */
    public void applyFriction(double deltaTime) {
        if (speed > 0) {
            speed = Math.max(speed - FRICTION * deltaTime, 0.0);
        }
    }

    // ── UPDATE METHOD (called every frame by game loop) ───────────────────────

    /**
     * Update car position and physics for this frame.
     *
     * This is called ~60 times per second by the JavaFX AnimationTimer.
     *
     * @param deltaTime seconds elapsed since last frame
     *
     * HOW LERP WORKS:
     *   targetX moves by STEER_SPEED * direction * deltaTime (e.g. 320 px/s)
     *   actual x = x + (targetX - x) * STEER_LERP  (12% closer each frame)
     *   Result: smooth glide to target rather than instant snap.
     */
    public void update(double deltaTime) {
        // Move targetX based on steering direction
        if (steerDirection != 0) {
            targetX += steerDirection * STEER_SPEED * deltaTime;
        }

        // Clamp targetX within road boundaries
        // Math.max/min ensure we don't go past the walls
        double halfW = WIDTH / 2.0;
        targetX = Math.max(leftBound  + halfW, targetX);
        targetX = Math.min(rightBound - halfW, targetX);

        // Smooth x toward targetX using lerp
        // LERP FORMULA: value = value + (target - value) * factor
        x = x + (targetX - x) * STEER_LERP;
    }

    // ── GETTERS ───────────────────────────────────────────────────────────────

    /** Get the current rendered X position (centre of car) */
    public double getX() { return x; }

    /** Get the current rendered Y position (centre of car) */
    public double getY() { return y; }

    /** Get the top-left X for drawing (x is centre, so subtract half width) */
    public double getDrawX() { return x - WIDTH / 2.0; }

    /** Get the top-left Y for drawing */
    public double getDrawY() { return y - HEIGHT / 2.0; }

    /** Get current forward speed (px/s) */
    public double getSpeed() { return speed; }

    /** Get speed as a 0.0–1.0 normalised value (useful for UI) */
    public double getSpeedFraction() { return speed / MAX_SPEED; }

    // ── SETTERS ───────────────────────────────────────────────────────────────

    // ── SETTERS ───────────────────────────────────────────────────────────────

/** Update lane bounds (e.g. if window is resized) */
public void setBounds(double left, double right) {
    this.leftBound  = left;
    this.rightBound = right;
}

/** Snap car to new Y (e.g. on game reset) */
public void setY(double y) { this.y = y; }

public void setX(double x) {

    this.x = x;

    this.targetX = x;
}

public void setTargetXNormalized(double normalizedX) {

    double halfW = WIDTH / 2.0;

    double usableWidth = rightBound - leftBound - WIDTH;

    double rawX = leftBound + halfW + normalizedX * usableWidth;

    this.targetX = Math.max(leftBound + halfW,
            Math.min(rightBound - halfW, rawX));
}

public void resetPosition(double x, double y) {

    this.x = x;

    this.y = y;

    this.targetX = x;

    this.speed = 0;
}
}
