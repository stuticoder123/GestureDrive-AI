"""
=============================================================
  detector.py — Hand Landmark Detector
=============================================================
  RESPONSIBILITY:
    - Opens the webcam using OpenCV
    - Processes each frame with MediaPipe Hands
    - Extracts 21 hand landmark points per hand
    - Returns landmark data + annotated frame to the caller

  WHY SEPARATE FROM gestures.py?
    Single Responsibility Principle — this file ONLY handles
    raw detection. Gesture logic lives in gestures.py.
    This makes it easy to swap MediaPipe for another model later.

  BEGINNER TIP:
    MediaPipe gives you 21 "landmarks" per hand.
    Each landmark is a point (x, y, z) — x and y are
    normalised 0.0→1.0 relative to the frame size.
    Landmark 0  = wrist
    Landmarks 4,8,12,16,20 = fingertips (thumb→pinky)
=============================================================
"""

import cv2
import mediapipe as mp


class HandDetector:
    """
    Wraps MediaPipe Hands so the rest of the project
    only needs to call two simple methods:
        detector.process_frame(frame)  → landmarks
        detector.draw_landmarks(frame) → annotated frame
    """

    def __init__(
        self,
        max_hands: int = 1,          # Track only 1 hand for speed
        detection_confidence: float = 0.7,   # How sure MP must be before it reports a hand
        tracking_confidence: float  = 0.6,   # How sure MP must be to keep tracking
    ):
        """
        Initialise MediaPipe Hands.

        Args:
            max_hands:            Maximum number of hands to detect simultaneously.
            detection_confidence: Minimum confidence for initial detection (0–1).
            tracking_confidence:  Minimum confidence to continue tracking (0–1).

        BEGINNER TIP:
            Lower confidence values = more detections but more false positives.
            Higher confidence values = fewer false positives but may miss hand.
            0.7 / 0.6 is a good real-world starting balance.
        """
        # MediaPipe solution objects
        self._mp_hands   = mp.solutions.hands
        self._mp_drawing = mp.solutions.drawing_utils
        self._mp_styles  = mp.solutions.drawing_styles

        # Create the Hands processor
        self._hands = self._mp_hands.Hands(
            static_image_mode=False,          # False = optimised for video streams
            max_num_hands=max_hands,
            min_detection_confidence=detection_confidence,
            min_tracking_confidence=tracking_confidence,
        )

        # Will hold the latest results after process_frame()
        self._results = None

    # ------------------------------------------------------------------
    # PUBLIC API
    # ------------------------------------------------------------------

    def process_frame(self, frame):
        """
        Run MediaPipe hand detection on a single BGR frame.

        Args:
            frame: OpenCV BGR image (numpy array from cap.read())

        Returns:
            landmarks (list | None):
                If a hand is found → list of 21 landmark objects.
                Each landmark has  .x  .y  .z  (all normalised 0–1).
                If no hand found   → None.

        HOW IT WORKS:
            1. OpenCV captures in BGR colour order.
            2. MediaPipe expects RGB, so we convert first.
            3. We mark the image as non-writeable before processing —
               this is a MediaPipe performance optimisation.
            4. Results contain a list-of-hands; we return just the first.
        """
        # Convert BGR → RGB  (MediaPipe requirement)
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        # Performance optimisation: tell NumPy the array is read-only
        rgb_frame.flags.writeable = False

        # Run the actual ML inference
        self._results = self._hands.process(rgb_frame)

        # Allow writing again (needed for drawing later)
        rgb_frame.flags.writeable = True

        # Return the landmarks of the FIRST detected hand (or None)
        if self._results.multi_hand_landmarks:
            return self._results.multi_hand_landmarks[0].landmark
        return None

    def draw_landmarks(self, frame):
        """
        Draw the hand skeleton (dots + connections) on the frame.
        Modifies the frame IN-PLACE and also returns it.

        Args:
            frame: BGR OpenCV frame to annotate.

        Returns:
            The same frame, now annotated.

        BEGINNER TIP:
            This is purely visual — it doesn't affect detection.
            You can disable it in production to save a few ms.
        """
        if self._results and self._results.multi_hand_landmarks:
            for hand_landmarks in self._results.multi_hand_landmarks:
                # Draw connection lines (the "skeleton")
                self._mp_drawing.draw_landmarks(
                    frame,
                    hand_landmarks,
                    self._mp_hands.HAND_CONNECTIONS,
                    # Green dots for landmarks
                    self._mp_drawing.DrawingSpec(
                        color=(0, 255, 0), thickness=2, circle_radius=4
                    ),
                    # White lines for connections
                    self._mp_drawing.DrawingSpec(
                        color=(255, 255, 255), thickness=2
                    ),
                )
        return frame

    def release(self):
        """
        Cleanly close the MediaPipe Hands solution.
        Always call this when you're done to free GPU/CPU resources.
        """
        self._hands.close()


# =============================================================
# STANDALONE TEST — run this file directly to verify detection
# python detector.py
# =============================================================
if __name__ == "__main__":
    print("[detector.py] Starting webcam test — press Q to quit")

    cap = cv2.VideoCapture(0)          # 0 = default webcam
    if not cap.isOpened():
        print("[ERROR] Cannot open webcam. Check your camera index.")
        exit(1)

    detector = HandDetector()

    while True:
        ret, frame = cap.read()
        if not ret:
            print("[ERROR] Failed to read frame from webcam.")
            break

        # Flip horizontally → feels like a mirror (more intuitive)
        frame = cv2.flip(frame, 1)

        landmarks = detector.process_frame(frame)
        detector.draw_landmarks(frame)

        # Show simple status overlay
        status = "Hand Detected!" if landmarks else "No Hand — show your palm"
        colour = (0, 255, 0) if landmarks else (0, 0, 255)
        cv2.putText(frame, status, (10, 40),
                    cv2.FONT_HERSHEY_SIMPLEX, 1.2, colour, 2)

        cv2.imshow("Hand Detector Test", frame)

        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

    detector.release()
    cap.release()
    cv2.destroyAllWindows()
    print("[detector.py] Test complete.")