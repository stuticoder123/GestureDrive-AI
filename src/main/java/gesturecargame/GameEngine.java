package com.gesturecargame;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

public class GameEngine {

    // ============================================================
    // CANVAS
    // ============================================================

    private final Canvas canvas;
    private final GraphicsContext gc;

    private final double W;
    private final double H;

    // ============================================================
    // GAME OBJECTS
    // ============================================================

    private final Road road;
    private final Car car;

    private final List<EnemyCar> enemies = new ArrayList<>();

    // ============================================================
    // FONTS
    // ============================================================

    private final Font fontHUD;
    private final Font fontHUDSmall;
    private final Font fontGameOverTitle;
    private final Font fontGameOverSub;

    // ============================================================
    // IMAGES
    // ============================================================

    private Image playerImage;
    private Image enemyImage;
    private Image roadImage;

    // ============================================================
    // GAME STATE
    // ============================================================

    private volatile boolean accelerating = false;
    private volatile boolean braking = false;
    private volatile boolean gameOver = false;

    private volatile boolean aiConnected = false;
    private volatile boolean gameStarted = false;

    private double score = 0;
    private double difficultyMultiplier = 1.0;

    private AnimationTimer gameTimer;

    private static final double[][] DIFFICULTY_STEPS = {
            {500, 1.2},
            {1000, 1.45},
            {2000, 1.75},
            {4000, 2.10},
            {7000, 2.50}
    };

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public GameEngine(Canvas canvas) {

        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();

        this.W = canvas.getWidth();
        this.H = canvas.getHeight();

        fontHUD = Font.font("Arial", FontWeight.BOLD, 22);
        fontHUDSmall = Font.font("Arial", FontWeight.NORMAL, 15);
        fontGameOverTitle = Font.font("Arial", FontWeight.BOLD, 50);
        fontGameOverSub = Font.font("Arial", FontWeight.BOLD, 28);

        loadImages();

        road = new Road((int) W, (int) H);

        car = new Car(
                W / 2 ,
                H - 150,
                road.getRoadLeft(),
                road.getRoadRight()
        );

        // ENEMIES
        for (int i = 0; i < 3; i++) {

            EnemyCar enemy = new EnemyCar(difficultyMultiplier);

            enemy.respawn(enemies);

            enemies.add(enemy);
        }

        // SOCKET RECEIVER
        SocketReceiver receiver = new SocketReceiver(this);
        receiver.start();

        System.out.println("[GameEngine] Initialised");

        start();
    }

    // ============================================================
    // START GAME LOOP
    // ============================================================

    public void start() {

        if (gameTimer != null) {
            gameTimer.stop();
        }

        gameTimer = new AnimationTimer() {

            long lastTime = System.nanoTime();

            @Override
            public void handle(long now) {

                double deltaTime =
                        (now - lastTime) / 1_000_000_000.0;

                lastTime = now;

                deltaTime = Math.min(deltaTime, 0.05);

                // WAIT FOR PYTHON
                if (!gameStarted) {

                    drawWaitingScreen();

                    return;
                }

                // GAME OVER
                if (gameOver) {

                    drawGameOver();

                    return;
                }

                // NORMAL GAME
                update(deltaTime);

                draw();
            }
        };

        gameTimer.start();
    }

    // ============================================================
    // UPDATE
    // ============================================================

    private void update(double dt) {

        if (braking) {
            car.brake(dt);
        } else if (accelerating) {
            car.accelerate(dt);
        } else {
            car.applyFriction(dt);
        }

        car.update(dt);

        road.update(car.getSpeed(), dt);

        updateEnemies(dt);

        checkCollisions();

        updateScore(dt);
    }

    // ============================================================
    // ENEMIES
    // ============================================================

    private void updateEnemies(double dt) {

        for (EnemyCar enemy : enemies) {

            enemy.update(dt);

            if (enemy.isOffScreen(H)) {

                enemy.respawn(enemies);

                score += 50;
            }
        }
    }

    // ============================================================
    // SCORE
    // ============================================================

    private void updateScore(double dt) {

        double ptsPerSec = car.getSpeed();

        score += ptsPerSec * dt;

        for (double[] step : DIFFICULTY_STEPS) {

            double threshold = step[0];
            double newMult = step[1];

            if (score >= threshold &&
                    difficultyMultiplier < newMult) {

                difficultyMultiplier = newMult;

                for (EnemyCar enemy : enemies) {

                    enemy.setSpeedMultiplier(difficultyMultiplier);
                }
            }
        }
    }

    // ============================================================
    // COLLISION
    // ============================================================

    private void checkCollisions() {

        double playerX = car.getDrawX();
        double playerY = car.getDrawY();

        for (EnemyCar enemy : enemies) {

            if (enemy.collidesWith(playerX, playerY)) {

                gameOver = true;

                System.out.println(
                        "[GameEngine] COLLISION! Score: " + (int) score
                );

                return;
            }
        }
    }

    // ============================================================
    // DRAW
    // ============================================================

    private void draw() {

        gc.clearRect(0, 0, W, H);

        // ROAD
        if (roadImage != null) {

            gc.drawImage(roadImage, 0, 0, W, H);

        } else {

            road.draw(gc);
        }

        // ENEMIES
        for (EnemyCar enemy : enemies) {

            if (enemyImage != null) {

                gc.drawImage(
                        enemyImage,
                        enemy.getX(),
                        enemy.getY(),
                        EnemyCar.WIDTH,
                        EnemyCar.HEIGHT
                );

            } else {

                gc.setFill(Color.RED);

                gc.fillRoundRect(
                        enemy.getX(),
                        enemy.getY(),
                        EnemyCar.WIDTH,
                        EnemyCar.HEIGHT,
                        8,
                        8
                );
            }
        }

        // PLAYER
        if (playerImage != null) {

            gc.drawImage(
                    playerImage,
                    car.getDrawX(),
                    car.getDrawY(),
                    Car.WIDTH,
                    Car.HEIGHT
            );

        } else {

            gc.setFill(Color.BLUE);

            gc.fillRoundRect(
                    car.getDrawX(),
                    car.getDrawY(),
                    Car.WIDTH,
                    Car.HEIGHT,
                    8,
                    8
            );
        }

        drawHUD();
    }

    // ============================================================
    // WAIT SCREEN
    // ============================================================

    private void drawWaitingScreen() {

        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, W, H);

        gc.setFont(fontGameOverTitle);
        gc.setFill(Color.WHITE);

        centerText(
                "GESTURE CAR GAME",
                H * 0.35,
                fontGameOverTitle
        );

        gc.setFont(fontGameOverSub);
        gc.setFill(Color.YELLOW);

        centerText(
                "WAITING FOR PYTHON AI...",
                H * 0.52,
                fontGameOverSub
        );

        gc.setFont(fontHUDSmall);
        gc.setFill(Color.LIGHTGRAY);

        centerText(
                "Run: py sender.py",
                H * 0.65,
                fontHUDSmall
        );

        if (aiConnected) {

            gc.setFill(Color.LIMEGREEN);

            gc.fillOval(
                    W / 2 - 10,
                    H * 0.75,
                    20,
                    20
            );

            gc.setFill(Color.WHITE);

            centerText(
                    "CONNECTED",
                    H * 0.82,
                    fontHUDSmall
            );

        } else {

            gc.setFill(Color.RED);

            gc.fillOval(
                    W / 2 - 10,
                    H * 0.75,
                    20,
                    20
            );

            gc.setFill(Color.WHITE);

            centerText(
                    "NOT CONNECTED",
                    H * 0.82,
                    fontHUDSmall
            );
        }
    }

    // ============================================================
    // HUD
    // ============================================================

    private void drawHUD() {

        gc.setFill(Color.rgb(0, 0, 0, 0.55));

        gc.fillRect(0, 0, W, 75);

        gc.setFont(fontHUD);

        gc.setFill(Color.WHITE);

        gc.fillText(
                "SCORE: " + (int) score,
                18,
                34
        );

        gc.setFont(fontHUDSmall);

        gc.fillText(
                "Speed: " + (int) car.getSpeed(),
                18,
                60
        );
    }

    // ============================================================
    // GAME OVER
    // ============================================================

    private void drawGameOver() {

        gc.setFill(Color.rgb(0, 0, 0, 0.85));

        gc.fillRect(0, 0, W, H);

        // TITLE
        gc.setFont(fontGameOverTitle);

        gc.setFill(Color.RED);

        centerText(
                "GAME OVER",
                H * 0.35,
                fontGameOverTitle
        );

        // SCORE
        gc.setFont(fontGameOverSub);

        gc.setFill(Color.WHITE);

        centerText(
                "SCORE: " + (int) score,
                H * 0.50,
                fontGameOverSub
        );

        // RESTART BUTTON
        double btnW = 220;
        double btnH = 60;

        double btnX = (W - btnW) / 2;
        double btnY = H * 0.62;

        gc.setFill(Color.DARKBLUE);

        gc.fillRoundRect(
                btnX,
                btnY,
                btnW,
                btnH,
                20,
                20
        );

        gc.setStroke(Color.WHITE);

        gc.setLineWidth(3);

        gc.strokeRoundRect(
                btnX,
                btnY,
                btnW,
                btnH,
                20,
                20
        );

        gc.setFont(fontGameOverSub);

        gc.setFill(Color.WHITE);

        gc.fillText(
                "PRESS R",
                btnX + 42,
                btnY + 38
        );

        // HINT
        gc.setFont(fontHUDSmall);

        gc.setFill(Color.LIGHTGRAY);

        centerText(
                "Press R to restart game",
                H * 0.80,
                fontHUDSmall
        );
    }

    // ============================================================
    // RESTART GAME
    // ============================================================

 public void restartGame() {

    // Reset states
    gameOver = false;

    score = 0;

    difficultyMultiplier = 1.0;

    accelerating = false;

    braking = false;

    // Reset AI wait state
    gameStarted = aiConnected;

    // Reset player position and steering
    car.resetPosition(
            W / 2 ,
            H - 150
    );
    car.steerCenter();

    // Reset enemies
    for (EnemyCar enemy : enemies) {

        enemy.setSpeedMultiplier(1.0);

        enemy.respawn(enemies);
    }

    // Restart timer safely (stop old timer first)
    

    System.out.println("[GameEngine] Restarted");
}

    // ============================================================
    // CENTER TEXT
    // ============================================================

    private void centerText(String text, double y, Font font) {

        gc.setFont(font);

        double approxWidth =
                text.length() * font.getSize() * 0.55;

        double x = (W - approxWidth) / 2.0;

        gc.fillText(text, x, y);
    }

    // ============================================================
    // IMAGE LOADING
    // ============================================================

    private void loadImages() {

    playerImage = tryLoadImage("/images/car.png");

    enemyImage = tryLoadImage("/images/enemy.png");

    roadImage = tryLoadImage("/images/road.png");
}
    private Image tryLoadImage(String path) {

        try {

            var stream =
                    getClass().getResourceAsStream(path);

            if (stream == null) {
                return null;
            }

            Image img = new Image(stream);

            if (img.isError()) {
                return null;
            }

            return img;

        } catch (Exception e) {

            return null;
        }
    }

    // ============================================================
    // SETTERS
    // ============================================================

    public void setAccelerating(boolean accelerating) {
        this.accelerating = accelerating;
    }

    public void setBraking(boolean braking) {
        this.braking = braking;
    }

    public void setSteeringPosition(double normalizedX) {
        double clamped = Math.max(0.0, Math.min(1.0, normalizedX));
        car.setTargetXNormalized(clamped);
    }

    public void setAiConnected(boolean connected) {

        this.aiConnected = connected;

        if (connected) {
            this.gameStarted = true;
        }

        System.out.println("AI Connected = " + connected);
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public Car getCar() {
        return car;
    }

    public int getScore() {
        return (int) score;
    }

    public boolean isGameOver() {
        return gameOver;
    }
}