import sounddevice as sd
import numpy as np
import librosa
import onnxruntime as ort
import warnings
import sys
import time
import speech_recognition as sr
import scipy.io.wavfile as wav
import google.generativeai as genai
import json
import os
import socket
import threading

warnings.filterwarnings("ignore")

# --- 1. CONFIGURATION ---
WAKE_WORD_MODEL = "hey_visteon.onnx" # Use "../hey_visteon.onnx" if it's one folder up
GEMINI_API_KEY = "AIzaSyCsh_s5eBN-pAQgxXcfyFrobcAKp7UGotI" # <-- PASTE YOUR KEY HERE
SAMPLE_RATE = 16000
CHUNK_DURATION = 0.25
CHUNK_SAMPLES = int(SAMPLE_RATE * CHUNK_DURATION)

# --- 2. THE BUILT-IN TCP SERVER ---
connected_clients = [] # Stores active Android app connections

def start_tcp_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # 0.0.0.0 means it will accept connections on your machine's IP address (10.118.253.54)
    server.bind(('10.118.253.54', 12345)) 
    server.listen(5)
    print("📡 Built-in TCP Server running! Waiting for Android App to connect...")
    
    while True:
        try:
            conn, addr = server.accept()
            print(f"\n📱 Android App Connected from {addr}!")
            connected_clients.append(conn)
        except Exception as e:
            print(f"Server error: {e}")

# Start the server in a background thread so it doesn't block the microphone
server_thread = threading.Thread(target=start_tcp_server, daemon=True)
server_thread.start()

# --- 3. INITIALIZE AI MODELS ---
print("Booting AI Subsystems...")
ort_session = ort.InferenceSession(WAKE_WORD_MODEL)

genai.configure(api_key=GEMINI_API_KEY)
system_instruction = """
You are the voice assistant for an In-Vehicle Infotainment system. 
Your ONLY job is to map passenger seat heating/ventilation requests to our strict JSON schema.
Valid Actions: "activate_seat_heat", "deactivate_seat_heat", "get_seat_temperature", "update_seat_temperature"
Valid Variables:
- zone: "front" or "rear"
- seat_id: "1" (Left) or "2" (Right)
- temperature: "+1", "+2", "+3" OR "-1", "-2", "-3"

If a request targets ONE seat, output a single JSON object: {"action": "...", "zone": "...", "seat_id": "...", "temperature": "...", "tts_response": "..."}
If a request targets MULTIPLE seats (like "all seats"), output a JSON array of objects: [{"action": "..."}, {"action": "..."}]
"""
llm_model = genai.GenerativeModel("gemini-2.5-flash", system_instruction=system_instruction, generation_config={"response_mime_type": "application/json"})

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
    print("🧠 Analyzing intent...")
    
    try:
        response = llm_model.generate_content(text)
        intent_data_raw = json.loads(response.text)
        
        # 1. Normalize the data: if it's a single dict, turn it into a list of one item
        if isinstance(intent_data_raw, dict):
            intent_list = [intent_data_raw]
        elif isinstance(intent_data_raw, list):
            intent_list = intent_data_raw
        else:
            return

        # 2. Loop through every command the AI generated
        for intent_data in intent_list:
            action = intent_data.get("action")
            zone = intent_data.get("zone")
            seat_id = intent_data.get("seat_id")
            temp = intent_data.get("temperature")
            tts = intent_data.get("tts_response")
            
            if not action or str(action).lower() in ["none", "null"]:
                print(f"🗣️ AI Voice Response: {tts}")
                continue
                
            if action == "deactivate_seat_heat":
                command = "deactivate_seat_heat"
            elif action == "activate_seat_heat":
                command = "activate_seat_heat-->front"
            else:
                command = f"{action}-->{zone}-->{seat_id}-->{temp}"
                
            # Only print the TTS response once if it's the first item in the list
            if intent_data == intent_list[0]:
                print(f"🗣️ AI Voice Response: {tts}")
            
            # 3. SEND TO ANDROID APP DIRECTLY
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
            
            # Add a tiny 0.1s delay so the Android app doesn't choke on multiple commands at once
            time.sleep(0.1)

    except Exception as e:
        print("⚠️ Error parsing LLM output or sending to Android.", e)
        
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