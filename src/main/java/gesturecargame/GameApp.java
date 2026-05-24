package com.gesturecargame;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class GameApp extends Application {

    @Override
    public void start(Stage stage) {

        // ROOT
        Group root = new Group();

        // CANVAS
        Canvas canvas = new Canvas(600, 800);

        root.getChildren().add(canvas);

        // SCENE
        Scene scene = new Scene(
                root,
                600,
                800,
                Color.DARKGRAY
        );

        // GAME ENGINE
        GameEngine engine = new GameEngine(canvas);

        // KEYBOARD INPUT
        scene.setOnKeyPressed(event -> {

            if (event.getCode() == KeyCode.R) {

                if (engine.isGameOver()) {

                    engine.restartGame();
                }
            }
        });

        // WINDOW
        stage.setTitle("Gesture Car Game");

        stage.setScene(scene);

        stage.show();

        // IMPORTANT
        root.requestFocus();
    }
}