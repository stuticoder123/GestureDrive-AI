"""
=============================================================
  gestures.py — Gesture Controller
=============================================================

CONTROL SYSTEM:

        Hand Left   = LEFT
        Hand Right  = RIGHT
        Open Palm   = ACCELERATE
        Closed Fist = BRAKE

Steering is proportional to hand horizontal position.
=============================================================
"""

from collections import deque

WRIST = 0
INDEX_MCP = 5
MIDDLE_MCP = 9
RING_MCP = 13
PINKY_MCP = 17

# ------------------------------------------------------------
# TUNING
# ------------------------------------------------------------

CENTER_X = 0.50
CENTER_Y = 0.50

DEAD_ZONE_X = 0.10
DEAD_ZONE_Y = 0.10

SMOOTHING_WINDOW = 5


class GestureClassifier:

    def __init__(self):
        self._history = deque(maxlen=SMOOTHING_WINDOW)

    # --------------------------------------------------------

    def classify(self, landmarks):

        if landmarks is None:
            self._history.clear()
            return "IDLE"

        command = self._gesture_command(landmarks)

        self._history.append(command)

        return self._smoothed_command()

    # --------------------------------------------------------

    def _gesture_command(self, landmarks):

        if self.is_closed_fist(landmarks):
            return "BRAKE"

        if self.is_open_palm(landmarks):
            return "ACCELERATE"

        return "IDLE"

    # --------------------------------------------------------

    def _smoothed_command(self):

        if not self._history:
            return "IDLE"

        return max(set(self._history), key=self._history.count)

    def get_steer_value(self, landmarks):
        """Return the normalized horizontal hand position for steering."""
        if landmarks is None:
            return None

        x, _ = self.get_hand_position(landmarks)
        return x

    # --------------------------------------------------------

    def is_open_palm(self, landmarks):
        """Return True when the hand looks like an open palm."""
        extended = 0

        if self.is_finger_extended(landmarks, 8, 6):
            extended += 1
        if self.is_finger_extended(landmarks, 12, 10):
            extended += 1
        if self.is_finger_extended(landmarks, 16, 14):
            extended += 1
        if self.is_finger_extended(landmarks, 20, 18):
            extended += 1

        return extended >= 3

    def is_closed_fist(self, landmarks):
        """Return True when the hand looks like a closed fist."""
        folded = 0

        if not self.is_finger_extended(landmarks, 8, 6):
            folded += 1
        if not self.is_finger_extended(landmarks, 12, 10):
            folded += 1
        if not self.is_finger_extended(landmarks, 16, 14):
            folded += 1
        if not self.is_finger_extended(landmarks, 20, 18):
            folded += 1

        return folded >= 3

    def is_finger_extended(self, landmarks, tip_index, pip_index):
        """A simple test for whether a finger is extended or folded."""
        return landmarks[tip_index].y < landmarks[pip_index].y

    def get_hand_position(self, landmarks):

        ref_points = [
            WRIST,
            INDEX_MCP,
            MIDDLE_MCP,
            RING_MCP,
            PINKY_MCP
        ]

        avg_x = sum(landmarks[i].x for i in ref_points) / len(ref_points)
        avg_y = sum(landmarks[i].y for i in ref_points) / len(ref_points)

        return avg_x, avg_y


# =============================================================
# TEST MODE
# =============================================================

if __name__ == "__main__":

    import cv2
    from detector import HandDetector

    print("GESTURE CONTROL TEST")
    print("----------------------------")
    print("Hand Left      -> LEFT")
    print("Hand Right     -> RIGHT")
    print("Open Palm      -> ACCELERATE")
    print("Closed Fist    -> BRAKE")
    print("CENTER / No Hand -> IDLE")
    print("Press Q to quit")

    cap = cv2.VideoCapture(0)

    detector = HandDetector()

    clf = GestureClassifier()

    while True:

        ret, frame = cap.read()

        if not ret:
            break

        frame = cv2.flip(frame, 1)

        landmarks = detector.process_frame(frame)

        detector.draw_landmarks(frame)

        command = clf.classify(landmarks)

        x, y = clf.get_hand_position(landmarks) if landmarks else (0.5, 0.5)

        h, w = frame.shape[:2]

        # ----------------------------------------------------
        # DRAW JOYSTICK BOX
        # ----------------------------------------------------

        left = int((CENTER_X - DEAD_ZONE_X) * w)
        right = int((CENTER_X + DEAD_ZONE_X) * w)

        top = int((CENTER_Y - DEAD_ZONE_Y) * h)
        bottom = int((CENTER_Y + DEAD_ZONE_Y) * h)

        cv2.rectangle(frame, (left, top), (right, bottom),
                      (255, 255, 255), 2)

        # Hand position dot
        px = int(x * w)
        py = int(y * h)

        cv2.circle(frame, (px, py), 12, (0, 255, 0), -1)

        # ----------------------------------------------------
        # COMMAND TEXT
        # ----------------------------------------------------

        colours = {
            "LEFT": (255, 120, 0),
            "RIGHT": (0, 200, 255),
            "ACCELERATE": (0, 255, 0),
            "BRAKE": (0, 0, 255),
            "IDLE": (180, 180, 180)
        }

        colour = colours.get(command, (255, 255, 255))

        cv2.putText(
            frame,
            f"CMD: {command}",
            (20, 50),
            cv2.FONT_HERSHEY_SIMPLEX,
            1.2,
            colour,
            3
        )

        # ----------------------------------------------------

        cv2.imshow("Air Joystick Controller", frame)

        print(f"\rCOMMAND: {command:12s}", end="", flush=True)

        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

    print("\nExiting...")

    detector.release()

    cap.release()

    cv2.destroyAllWindows()