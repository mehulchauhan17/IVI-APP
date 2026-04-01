import sounddevice as sd
import numpy as np
import librosa
import onnxruntime as ort
import warnings
import sys
import time
import speech_recognition as sr
import scipy.io.wavfile as wav
import json
import os
import socket
import threading
import requests

warnings.filterwarnings("ignore")

# --- 1. CONFIGURATION ---
WAKE_WORD_MODEL = "hey_visteon.onnx" 
SAMPLE_RATE = 16000
CHUNK_DURATION = 0.25
CHUNK_SAMPLES = int(SAMPLE_RATE * CHUNK_DURATION)
OLLAMA_MODEL = "qwen2.5:0.5b" # Make sure Ollama is running this exact model!

# --- 2. THE BUILT-IN TCP SERVER ---
connected_clients = [] 

def start_tcp_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind(('0.0.0.0', 12345)) 
    server.listen(5)
    print("📡 Built-in TCP Server running! Waiting for Android App to connect...")
    
    while True:
        try:
            conn, addr = server.accept()
            print(f"\n📱 Android App Connected from {addr}!")
            connected_clients.append(conn)
        except Exception as e:
            print(f"Server error: {e}")

server_thread = threading.Thread(target=start_tcp_server, daemon=True)
server_thread.start()

# --- 3. INITIALIZE AI MODELS ---
print("Booting AI Subsystems...")
ort_session = ort.InferenceSession(WAKE_WORD_MODEL)

# FEW-SHOT PROMPTING: We give Qwen exact examples so it never forgets the format
system_instruction = """
You are the voice assistant for an In-Vehicle Infotainment system. 
Map passenger seat climate requests to our strict JSON schema.

Valid Actions: "activate_seat_heat", "deactivate_seat_heat", "update_seat_temperature"
Valid Variables: zone ("front" or "rear"), seat_id ("1" for Left, "2" for Right), temperature ("+1", "+2", "+3", "-1", "-2", "-3")

EXAMPLES:
User: "Turn off all the seats"
Output: [{"action": "deactivate_seat_heat", "tts_response": "Shutting down seat climate."}]

User: "Turn on cooling for the driver"
Output: [{"action": "update_seat_temperature", "zone": "front", "seat_id": "1", "temperature": "-3", "tts_response": "Cooling the driver seat."}]

User: "Turn on heat for all seats"
Output: [
  {"action": "update_seat_temperature", "zone": "front", "seat_id": "1", "temperature": "+3"},
  {"action": "update_seat_temperature", "zone": "front", "seat_id": "2", "temperature": "+3"},
  {"action": "update_seat_temperature", "zone": "rear", "seat_id": "1", "temperature": "+3"},
  {"action": "update_seat_temperature", "zone": "rear", "seat_id": "2", "temperature": "+3", "tts_response": "Heating all seats."}
]

CRITICAL RULE: Output ONLY valid JSON. NEVER invent new actions or zones.
"""

recognizer = sr.Recognizer()

# --- 4. HELPER FUNCTIONS ---
def extract_features(signal):
    mfccs = librosa.feature.mfcc(y=signal, sr=SAMPLE_RATE, n_mfcc=40, n_fft=400, hop_length=160)
    return mfccs.T[np.newaxis, ..., np.newaxis].astype(np.float32)

def transcribe_command():
    print("\n🟢 LISTENING FOR COMMAND... (Speak now!)")
    command_audio = sd.rec(int(4 * SAMPLE_RATE), samplerate=SAMPLE_RATE, channels=1, dtype='int16')
    sd.wait()
    print("Processing audio...")
    
    wav.write("temp_command.wav", SAMPLE_RATE, command_audio)
    
    try:
        with sr.AudioFile("temp_command.wav") as source:
            audio_data = recognizer.record(source)
            text = recognizer.recognize_google(audio_data)
            print(f"🗣️ You said: '{text}'")
            return text
    except sr.UnknownValueError:
        print("⚠️ Could not understand the audio.")
        return None
    finally:
        if os.path.exists("temp_command.wav"):
            os.remove("temp_command.wav")

def get_intent_from_llm(text):
    print(f"🧠 Analyzing intent via Local Ollama ({OLLAMA_MODEL})...")
    
    # USING THE CHAT ENDPOINT INSTEAD OF GENERATE
    url = "http://localhost:11434/api/chat"
    
    payload = {
        "model": OLLAMA_MODEL,
        "messages": [
            {"role": "system", "content": system_instruction},
            {"role": "user", "content": text}
        ],
        "stream": False
    }
    
    try:
        # We added timeout=15 so it doesn't freeze your app forever!
        response = requests.post(url, json=payload, timeout=15) 
        response.raise_for_status()
        
        result = response.json()
        
        # In the chat endpoint, the text lives inside message -> content
        raw_text = result.get("message", {}).get("content", "").strip()
        print(f"🔍 DEBUG - Raw LLM Output:\n{raw_text}\n") 
        
        # --- THE ULTIMATE JSON EXTRACTOR ---
        start_idx_dict = raw_text.find('{')
        start_idx_list = raw_text.find('[')
        
        start_idx = -1
        if start_idx_dict != -1 and start_idx_list != -1:
            start_idx = min(start_idx_dict, start_idx_list)
        else:
            start_idx = max(start_idx_dict, start_idx_list)
            
        end_idx_dict = raw_text.rfind('}')
        end_idx_list = raw_text.rfind(']')
        
        end_idx = max(end_idx_dict, end_idx_list)
        
        if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
            raw_text = raw_text[start_idx:end_idx+1]
        else:
            raw_text = "{}" # Fallback
        # -------------------------------------
            
        intent_data_raw = json.loads(raw_text)
        
        # Normalize the data: if it's a single dict, turn it into a list
        if isinstance(intent_data_raw, dict):
            intent_list = [intent_data_raw]
        elif isinstance(intent_data_raw, list):
            intent_list = intent_data_raw
        else:
            return

        # Loop through every command the AI generated
        for intent_data in intent_list:
            action = intent_data.get("action")
            zone = intent_data.get("zone", "front") # Default to front if hallucinated
            seat_id = intent_data.get("seat_id", "1") # Default to 1 if hallucinated
            temp = intent_data.get("temperature", "+1")
            tts = intent_data.get("tts_response", "")
            
            # --- THE BOUNCER: Reject fake actions ---
            valid_actions = ["activate_seat_heat", "deactivate_seat_heat", "update_seat_temperature"]
            if action not in valid_actions:
                print(f"🛑 Blocked Hallucinated Action from AI: {action}")
                continue
            # ----------------------------------------
                
            if action == "deactivate_seat_heat":
                command = "deactivate_seat_heat"
            elif action == "activate_seat_heat":
                command = "activate_seat_heat-->front"
            else:
                command = f"{zone}-->{seat_id}-->{temp}"
              #  command = f"{action}-->{zone}-->{seat_id}-->{temp}"
                
            if intent_data == intent_list[0] and tts:
                print(f"🗣️ AI Voice Response: {tts}")
            
            # SEND TO ANDROID APP DIRECTLY
            if not connected_clients:
                print("⏳ Command generated, but the Android app is not connected yet!")
            else:
                dead_connections = []
                for conn in connected_clients:
                    try:
                        conn.sendall((command + "\n").encode('utf-8'))
                        print(f"✅ Successfully sent to Android: {command}")
                    except Exception as e:
                        dead_connections.append(conn) 
                
                for dead in dead_connections:
                    connected_clients.remove(dead)
            
            time.sleep(0.1)

    except requests.exceptions.ConnectionError:
        print("⚠️ Error: Could not connect to Ollama. Is the Ollama app running?")
    except json.JSONDecodeError:
        print(f"⚠️ JSON Parsing Error. Extracted text was: {raw_text}")
    except Exception as e:
        print(f"⚠️ Error parsing Ollama output: {e}")
        
# --- 5. MAIN WAKE WORD LOOP ---
audio_buffer = np.zeros(SAMPLE_RATE, dtype=np.float32)

def audio_callback(indata, frames, time_info, status):
    global audio_buffer
    audio_buffer = np.roll(audio_buffer, -frames)
    audio_buffer[-frames:] = indata[:, 0]

print("\n" + "="*50)
print("🎤 SYSTEM ACTIVE: Say 'Hey Visteon'...")
print("="*50 + "\n")

stream = sd.InputStream(device=None, samplerate=SAMPLE_RATE, channels=1, dtype='float32', blocksize=CHUNK_SAMPLES, callback=audio_callback)

with stream:
    try:
        while True:
            time.sleep(CHUNK_DURATION)
            input_data = extract_features(audio_buffer)
            ort_inputs = {ort_session.get_inputs()[0].name: input_data}
            prediction = ort_session.run(None, ort_inputs)[0][0][0]
            
            if prediction > 0.70:
                stream.stop()
                print(f"\n[Wake Word Detected! Confidence: {prediction*100:.1f}%]")
                
                command_text = transcribe_command()
                if command_text:
                    get_intent_from_llm(command_text)
                
                audio_buffer = np.zeros(SAMPLE_RATE, dtype=np.float32)
                print("\n🎤 Resuming background listening for 'Hey Visteon'...")
                stream.start()
            else:
                sys.stdout.write(f"\rListening... [Confidence: {prediction*100:.1f}%]     ")
                sys.stdout.flush()

    except KeyboardInterrupt:
        print("\n\nSystem shutting down.")