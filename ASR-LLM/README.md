# 🚗 IVI AI Voice Assistant (ASR & LLM)

A full-stack, AI-driven Voice Assistant prototype for an In-Vehicle Infotainment (IVI) system. This project bridges a Python-based AI backend (handling Wake Word, Speech-to-Text, and Intent Parsing) with an Android Java frontend, utilizing a custom TCP socket server for zero-latency, real-time UI synchronization.

## ✨ Key Features
* **Custom Wake Word Detection:** Uses ONNX Runtime and Librosa for continuous, edge-based audio processing to detect the phrase *"Hey Visteon"*.
* **Generative AI Intent Parsing:** Integrates Google's `gemini-2.5-flash` LLM to parse messy, natural human speech into strict, actionable JSON payloads.
* **Real-Time State Sync:** Features a built-in Python TCP Server and a persistent Android background listener to update the vehicle's climate UI instantly without blocking the main thread.
* **Smart Contextual Control:** Capable of controlling individual seats, switching between front/rear zones, or handling multi-seat arrays (e.g., *"turn on the heat for all seats"*).

## 🧠 System Architecture
1. **The Ear:** `sounddevice` continuously listens in 0.25s chunks.
2. **The Trigger:** ONNX model calculates wake-word confidence. Upon breaking the 70% threshold, it triggers the active listener.
3. **The Mouth:** Google Speech Recognition (ASR) converts the audio command into a text transcript.
4. **The Brain:** Gemini 2.5 analyzes the transcript, formats a JSON object (or an array of objects for multiple seats), and extracts variables (`zone`, `seat_id`, `temperature`).
5. **The Bridge:** A background TCP thread broadcasts the parsed command string (`update_seat_temperature-->front-->1-->+3`) to the connected Android client.
6. **The Body:** The Android app receives the string, updates its internal State Cache, triggers virtual UI clicks, and redraws the screen instantly.

---

## 🛠️ Prerequisites
* **Python 3.8+**
* **Android Studio** (with a running Emulator)
* **Google Gemini API Key** (Get one free at [Google AI Studio](https://aistudio.google.com/))

## 🚀 Installation & Setup

### 1. The Python Backend (The Brain)
Clone the repository and set up your virtual environment:
```bash
git clone [https://github.com/mehulchauhan17/IVI-APP.git](https://github.com/mehulchauhan17/IVI-APP.git)
cd IVI-APP
python -m venv venv

Activate the virtual environment:

Windows: .\venv\Scripts\activate

Mac/Linux: source venv/bin/activate

Install the required dependencies:

Bash
pip install -r requirements.txt
⚠️ Important: Open master_assistant.py and paste your Gemini API key into line 20:

Python
GEMINI_API_KEY = "YOUR_API_KEY_HERE"
2. The Android Frontend (The Body)
Open Android Studio.

Select File > Open and navigate to the Seat-App folder inside this repository.

Let Gradle sync and build the project.

Launch your Android Emulator. (Note: The app is pre-configured to connect to 10.0.2.2, which is the standard localhost bridge for Android Emulators. If testing on physical hardware, update the IP in MainActivity.java to your computer's Wi-Fi IPv4 address).

🎮 How to Run the System
Because the Python script hosts the TCP Server, it must be started first.

Start the AI Server:
Ensure your virtual environment is active, then run:

Bash
python master_assistant.py
You should see: 📡 Built-in TCP Server running! Waiting for Android App to connect...

Launch the Android App:
Hit "Run" in Android Studio. Once the app opens on your emulator, check your Python terminal.
You should see: 📱 Android App Connected!

Issue a Voice Command:
Say the wake word, pause for a moment, and speak your command:

🗣️ "Hey Visteon... [pause] ...turn the driver seat heat to maximum."

🗣️ "Hey Visteon... [pause] ...freeze the rear right passenger."

🗣️ "Hey Visteon... [pause] ...kill all the seat climate."

Watch the Android UI update in real-time without ever touching the screen!