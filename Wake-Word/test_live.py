import sounddevice as sd
import numpy as np
import librosa
import onnxruntime as ort
import warnings
import sys
import time

warnings.filterwarnings("ignore")

MODEL_PATH = "hey_visteon.onnx"
SAMPLE_RATE = 16000
CHUNK_DURATION = 0.25  # Analyze 4 times per second
CHUNK_SAMPLES = int(SAMPLE_RATE * CHUNK_DURATION)

# Create a rolling buffer that holds exactly 1 second of audio
audio_buffer = np.zeros(SAMPLE_RATE, dtype=np.float32)

print(f"Loading ONNX Model from {MODEL_PATH}...")
ort_session = ort.InferenceSession(MODEL_PATH)

def extract_features(signal):
    mfccs = librosa.feature.mfcc(y=signal, sr=SAMPLE_RATE, n_mfcc=40, n_fft=400, hop_length=160)
    return mfccs.T[np.newaxis, ..., np.newaxis].astype(np.float32)

def audio_callback(indata, frames, time_info, status):
    global audio_buffer
    if status:
        print(status, file=sys.stderr)
    
    # Slide the buffer to the left and add the new audio to the right
    audio_buffer = np.roll(audio_buffer, -frames)
    audio_buffer[-frames:] = indata[:, 0]

print("\n" + "="*50)
print("🎤 CONTINUOUS STREAM ACTIVE: Listening for 'Hey Visteon'...")
print("="*50 + "\n")

# Put your specific headphone number here!
# For example, if your headphones were number 2 on the list:
MIC_ID = 1

# Start a continuous, non-blocking audio stream
stream = sd.InputStream(device=MIC_ID, samplerate=SAMPLE_RATE, channels=1, dtype='float32', 
                        blocksize=CHUNK_SAMPLES, callback=audio_callback)
last_trigger_time = 0

with stream:
    try:
        while True:
            time.sleep(CHUNK_DURATION)
            
            # Extract features from the current 1-second rolling buffer
            input_data = extract_features(audio_buffer)
            
            # Run inference
            ort_inputs = {ort_session.get_inputs()[0].name: input_data}
            prediction = ort_session.run(None, ort_inputs)[0][0][0]
            
            current_time = time.time()
            
            # If confidence is high AND we haven't triggered in the last 2 seconds (cooldown)
            if prediction > 0.70 and (current_time - last_trigger_time) > 2.0:
                print(f"\n🟢 WAKE WORD DETECTED! (Confidence: {prediction*100:.1f}%)")
                last_trigger_time = current_time
            else:
                sys.stdout.write(f"\rListening... [AI Confidence: {prediction*100:.1f}%]     ")
                sys.stdout.flush()

    except KeyboardInterrupt:
        print("\n\nMicrophone deactivated. Exiting...")