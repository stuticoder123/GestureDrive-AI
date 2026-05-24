package com.gesturecargame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketReceiver extends Thread {

    private final GameEngine engine;

    public SocketReceiver(GameEngine engine) {

        this.engine = engine;
    }

    @Override
    public void run() {

        try {

            ServerSocket serverSocket =
                    new ServerSocket(5555);

            System.out.println(
                    "[SocketReceiver] Waiting for Python AI..."
            );

            Socket socket = serverSocket.accept();

            System.out.println(
                    "[SocketReceiver] Python connected!"
            );

            // IMPORTANT
            engine.setAiConnected(true);

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream()
                            )
                    );

            String command;

            while ((command = reader.readLine()) != null) {

                command = command.trim();

                handleCommand(command);
            }

        } catch (Exception e) {

            System.out.println(
                    "[SocketReceiver] Connection lost!"
            );

            engine.setAiConnected(false);

            e.printStackTrace();
        }
    }

    // ============================================================
    // HANDLE COMMANDS
    // ============================================================

    private void handleCommand(String cmd) {

        switch (cmd) {

            case "LEFT":

                engine.getCar().steerLeft();

                break;

            case "RIGHT":

                engine.getCar().steerRight();

                break;

            case "ACCELERATE":

                engine.setAccelerating(true);

                engine.setBraking(false);

                break;

            case "BRAKE":

                engine.setBraking(true);

                engine.setAccelerating(false);

                break;

            case "IDLE":

                engine.setAccelerating(false);

                engine.setBraking(false);

                break;

            default:

                if (cmd.startsWith("STEER:")) {
                    try {
                        double normalized =
                                Double.parseDouble(cmd.substring(6));
                        engine.setSteeringPosition(normalized);
                    } catch (NumberFormatException e) {
                        System.out.println(
                                "[SocketReceiver] Invalid STEER value: " + cmd
                        );
                    }
                } else {
                    System.out.println(
                            "[SocketReceiver] Unknown command: " + cmd
                    );
                }
        }
    }
}