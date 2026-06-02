import sys
import os
import time

# ----------------------------
# Basic project setup
# ----------------------------
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.append(BASE_DIR)

# ----------------------------
# Import main system
# (adjust this import based on your actual file structure)
# ----------------------------
try:
    from main import main  # your main entry function
except Exception as e:
    print("❌ Failed to import main module.")
    print("Error:", e)
    sys.exit(1)

# ----------------------------
# Dependency check helper
# ----------------------------
def check_dependencies():
    required_modules = [
        "cv2",
        "mediapipe",
        "numpy",
        "pyautogui"
    ]

    missing = []

    for module in required_modules:
        try:
            __import__(module)
        except ImportError:
            missing.append(module)

    if missing:
        print("❌ Missing dependencies detected:")
        for m in missing:
            print(f" - {m}")
        print("\nRun: pip install -r requirements.txt")
        sys.exit(1)

# ----------------------------
# Camera check
# ----------------------------
def check_camera():
    try:
        import cv2
        cap = cv2.VideoCapture(0)

        if not cap.isOpened():
            print("Camera not accessible. Please check webcam permissions.")
            return False

        ret, frame = cap.read()
        cap.release()

        if not ret:
            print("Failed to read from camera.")
            return False

        print("Camera detected successfully.")
        return True

    except Exception as e:
        print("Camera check failed:", e)
        return False

# ----------------------------
# Startup banner
# ----------------------------
def banner():
    print("\n" + "="*50)
    print("GestureDrive-AI System Launcher")
    print("="*50)
    print("Initializing AI Gesture Control System...")
    print("Version: 1.0.0")
    print("="*50 + "\n")

# ----------------------------
# Main runner
# ----------------------------
def run():
    banner()

    print("Checking dependencies...")
    check_dependencies()
    print("All dependencies satisfied.\n")

    print("Checking camera...")
    if not check_camera():
        print("Camera initialization failed. Exiting safely.")
        sys.exit(1)

    print("\n🚀 Starting GestureDrive-AI...\n")
    time.sleep(1)

    try:
        main()
    except KeyboardInterrupt:
        print("\n System stopped by user (KeyboardInterrupt).")
    except Exception as e:
        print("\n Unexpected error occurred:")
        print(e)
    finally:
        print("\n Shutting down GestureDrive-AI safely...")
        time.sleep(1)
        print("Exit complete.")

# ----------------------------
# Entry point
# ----------------------------
if __name__ == "__main__":
    run()
