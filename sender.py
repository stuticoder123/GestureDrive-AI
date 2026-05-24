"""
=============================================================
  sender.py — Main Runner & TCP Socket Sender
=============================================================
  RESPONSIBILITY:
    - Opens webcam (via OpenCV)
    - Detects hand landmarks (via detector.py)
    - Classifies gesture (via gestures.py)
    - Sends command string over TCP to the Java game
    - Shows live debug window with gesture overlay

  HOW TCP COMMUNICATION WORKS (simple explanation):
    Think of TCP like a phone call:
      • Java game = SERVER  → waits by the phone (port 5555)
      • Python AI = CLIENT  → dials the phone number (connects)
      • Once connected, Python sends short text commands
        like "LEFT\n" or "ACCELERATE\n"
      • Java reads each line and updates the car accordingly

  START ORDER:
    1. Start the Java game FIRST  (it starts the server)
    2. Start this Python script   (it connects as client)

  COMMANDS SENT:
    "LEFT\n"
    "RIGHT\n"
    "ACCELERATE\n"
    "BRAKE\n"
    "IDLE\n"

  PORT: 5555  (must match Java SocketReceiver.java)
=============================================================
"""

import cv2
import socket
import time
import sys
from detector import HandDetector
from gestures import GestureClassifier

# ── Network config ───────────────────────────────────────────
JAVA_HOST = "127.0.0.1"   # localhost — Java runs on the same machine
JAVA_PORT = 5555           # Must match Java's ServerSocket port
RECONNECT_DELAY_SEC = 2.0  # Wait before retrying a failed connection

# ── Performance config ───────────────────────────────────────
TARGET_FPS = 30             # Webcam capture target
SEND_EVERY_N_FRAMES = 1     # Send a command every N frames (1 = every frame)
                            # Increase to 2 or 3 to reduce network load

# ── Display config ───────────────────────────────────────────
SHOW_DEBUG_WINDOW = True    # Set False to run headless (no OpenCV window)
WINDOW_TITLE = "Gesture Car Control — AI Vision"


def connect_to_java(host: str, port: int) -> socket.socket | None:
    """
    Attempt to connect to the Java TCP server.

    Returns:
        socket.socket if connected successfully, or None on failure.

    BEGINNER TIP:
        socket.socket(AF_INET, SOCK_STREAM)
            AF_INET   = use IPv4 addresses
            SOCK_STREAM = use TCP (reliable, ordered delivery)

        .connect((host, port)) = dial the server
    """
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        # Timeout so connect() doesn't hang forever if Java isn't running
        sock.settimeout(3.0)
        sock.connect((host, port))
        # After connect, set a short timeout for sends
        sock.settimeout(1.0)
        print(f"[sender] ✅ Connected to Java game at {host}:{port}")
        return sock
    except ConnectionRefusedError:
        print(f"[sender] ❌ Connection refused — is the Java game running?")
    except socket.timeout:
        print(f"[sender] ❌ Connection timed out — Java may not be ready yet.")
    except Exception as e:
        print(f"[sender] ❌ Unexpected error: {e}")
    return None


def send_command(sock: socket.socket, command: str) -> bool:
    """
    Send a single command string over the socket.

    Args:
        sock:    An open, connected socket.
        command: e.g. "LEFT", "ACCELERATE"

    Returns:
        True  = sent successfully
        False = send failed (connection broken)

    PROTOCOL:
        We append "\\n" so Java's BufferedReader.readLine() knows
        where each command ends.  Simple line-delimited protocol.

    BEGINNER TIP:
        .encode("utf-8") converts the Python string to raw bytes.
        Sockets send bytes, not strings.
    """
    try:
        message = command + "\n"
        sock.sendall(message.encode("utf-8"))
        return True
    except (BrokenPipeError, ConnectionResetError, socket.timeout):
        print("[sender] ⚠️  Connection to Java lost.")
        return False
    except Exception as e:
        print(f"[sender] ⚠️  Send error: {e}")
        return False


def draw_overlay(frame, command: str, steer: float | None, fps: float, connected: bool):
    """
    Draw a clean HUD overlay on the debug window.

    Args:
        frame:     BGR OpenCV frame (modified in place)
        command:   Current gesture action command string
        steer:     Normalized steering value (0.0–1.0) or None
        fps:       Measured frames per second
        connected: Whether the socket is active
    """
    h, w = frame.shape[:2]

    # ── Semi-transparent dark banner at top ─────────────────
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 70), (0, 0, 0), -1)
    cv2.addWeighted(overlay, 0.5, frame, 0.5, 0, frame)

    # ── Command label ────────────────────────────────────────
    cmd_colours = {
        "LEFT":       (255, 165,   0),   # Orange
        "RIGHT":      (0,   200, 255),   # Cyan
        "ACCELERATE": (0,   255,   0),   # Green
        "BRAKE":      (0,   60,  255),   # Red
        "IDLE":       (180, 180, 180),   # Grey
    }
    colour = cmd_colours.get(command, (255, 255, 255))
    label = command
    if steer is not None:
        label = f"{command} / STEER:{steer:.2f}"
    cv2.putText(frame, label, (10, 52),
                cv2.FONT_HERSHEY_DUPLEX, 1.2, colour, 2, cv2.LINE_AA)

    # ── FPS counter ──────────────────────────────────────────
    cv2.putText(frame, f"FPS: {fps:.0f}", (w - 130, 30),
                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 1, cv2.LINE_AA)

    # ── Connection status dot ────────────────────────────────
    dot_colour = (0, 255, 0) if connected else (0, 0, 255)
    dot_label  = "CONNECTED" if connected else "DISCONNECTED"
    cv2.circle(frame, (w - 20, 55), 9, dot_colour, -1)
    cv2.putText(frame, dot_label, (w - 170, 60),
                cv2.FONT_HERSHEY_SIMPLEX, 0.55, dot_colour, 1, cv2.LINE_AA)

    # ── Controls hint at bottom ──────────────────────────────
    hint = "Open palm=ACCEL  Fist=BRAKE  Move hand L/R=STEER  Q=Quit"
    cv2.putText(frame, hint, (8, h - 12),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, (150, 150, 150), 1, cv2.LINE_AA)

    return frame


def main():
    """
    Main application loop.

    Flow:
        1. Open webcam
        2. Try to connect to Java (retry until success)
        3. Each frame:
            a. Read frame from webcam
            b. Detect hand landmarks
            c. Classify gesture
            d. Send command to Java (every SEND_EVERY_N_FRAMES frames)
            e. Display debug overlay
        4. On Q key or window close → cleanup and exit
    """
    print("=" * 60)
    print("  Gesture Car Control — Python AI Sender")
    print("=" * 60)
    print(f"  Target : {JAVA_HOST}:{JAVA_PORT}")
    print(f"  FPS    : {TARGET_FPS}")
    print(f"  Debug  : {'ON' if SHOW_DEBUG_WINDOW else 'OFF'}")
    print("=" * 60)
    print("  1. Make sure the Java game is running FIRST.")
    print("  2. Keep your hand visible in the webcam frame.")
    print("  3. Press Q (in the OpenCV window) to quit.")
    print("=" * 60)

    # ── Open webcam ──────────────────────────────────────────
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("[ERROR] Cannot open webcam (index 0).")
        print("  Try changing VideoCapture(0) to VideoCapture(1) or (2).")
        sys.exit(1)

    # Set desired resolution and FPS
    cap.set(cv2.CAP_PROP_FRAME_WIDTH,  640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    cap.set(cv2.CAP_PROP_FPS, TARGET_FPS)

    # ── Initialise AI modules ────────────────────────────────
    detector = HandDetector(max_hands=1, detection_confidence=0.7)
    clf      = GestureClassifier()

    # ── Try to connect to Java (keep retrying) ───────────────
    sock = None
    while sock is None:
        sock = connect_to_java(JAVA_HOST, JAVA_PORT)
        if sock is None:
            print(f"  Retrying in {RECONNECT_DELAY_SEC}s... (start Java game if not running)")
            time.sleep(RECONNECT_DELAY_SEC)

    # ── Main loop variables ──────────────────────────────────
    frame_count    = 0
    prev_time      = time.time()
    fps            = 0.0
    last_action    = "IDLE"
    last_steer    = None
    connected      = True

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                print("[ERROR] Lost webcam feed.")
                break

            # Mirror the frame so it feels natural
            frame = cv2.flip(frame, 1)
            frame_count += 1

            # ── Detect & classify ────────────────────────────
            landmarks = detector.process_frame(frame)
            command   = clf.classify(landmarks)
            steer     = clf.get_steer_value(landmarks)

            # ── Send command ─────────────────────────────────
            if frame_count % SEND_EVERY_N_FRAMES == 0:
                if steer is not None:
                    if not send_command(sock, f"STEER:{steer:.3f}"):
                        connected = False
                        print("[sender] Attempting to reconnect...")
                        sock.close()
                        time.sleep(RECONNECT_DELAY_SEC)
                        sock = connect_to_java(JAVA_HOST, JAVA_PORT)
                        if sock:
                            connected = True
                        else:
                            sock = socket.socket()
                    else:
                        connected = True

                if not send_command(sock, command):
                    connected = False
                    print("[sender] Attempting to reconnect...")
                    sock.close()
                    time.sleep(RECONNECT_DELAY_SEC)
                    sock = connect_to_java(JAVA_HOST, JAVA_PORT)
                    if sock:
                        connected = True
                    else:
                        sock = socket.socket()
                else:
                    connected = True

            # ── Track command changes (for console log) ──────
            if command != last_action:
                print(f"[gesture] {command}")
                last_action = command

            if steer is not None:
                steer_label = f"STEER:{steer:.3f}"
                if steer_label != last_steer:
                    print(f"[gesture] {steer_label}")
                    last_steer = steer_label

            # ── Calculate FPS ─────────────────────────────────
            now = time.time()
            fps = 1.0 / max(now - prev_time, 1e-9)
            prev_time = now

            # ── Draw overlays ─────────────────────────────────
            if SHOW_DEBUG_WINDOW:
                detector.draw_landmarks(frame)
                draw_overlay(frame, command, steer, fps, connected)
                cv2.imshow(WINDOW_TITLE, frame)

                if cv2.waitKey(1) & 0xFF == ord("q"):
                    print("\n[sender] Q pressed — shutting down.")
                    break

    except KeyboardInterrupt:
        print("\n[sender] Interrupted by user (Ctrl+C).")

    finally:
        # ── Clean shutdown ────────────────────────────────────
        print("[sender] Cleaning up...")
        try:
            send_command(sock, "IDLE")   # Tell Java to stop the car
            sock.close()
        except Exception:
            pass

        detector.release()
        cap.release()
        cv2.destroyAllWindows()
        print("[sender] Goodbye!")


if __name__ == "__main__":
    main()