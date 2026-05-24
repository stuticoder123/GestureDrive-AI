package com.gesturecargame;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        // =====================================================
        // CREATE CANVAS
        // =====================================================

        Canvas canvas = new Canvas(800, 600);

        // =====================================================
        // ROOT LAYOUT
        // =====================================================

        StackPane root = new StackPane();

        root.getChildren().add(canvas);

        // =====================================================
        // SCENE
        // =====================================================

        Scene scene = new Scene(root, 800, 600);

        // =====================================================
        // GAME ENGINE
        // =====================================================

        GameEngine engine = new GameEngine(canvas);

        engine.start();

        // =====================================================
        // KEYBOARD INPUT
        // =====================================================

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.R && engine.isGameOver()) {
                engine.restartGame();
            }
        });

        // =====================================================
        // WINDOW SETTINGS
        // =====================================================

        stage.setTitle("Gesture Controlled Car Game");

        stage.setScene(scene);

        stage.setResizable(false);

        stage.show();

        root.requestFocus();
    }

    public static void main(String[] args) {

        launch(args);
    }
}