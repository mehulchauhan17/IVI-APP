import os

os.environ["TF_USE_LEGACY_KERAS"] = "1"

import numpy as np
import librosa
import tensorflow as tf
from sklearn.model_selection import train_test_split
import tf2onnx

# --- 1. CONFIGURATION ---
# Paths updated to match your exact folder structure
POSITIVE_DIR = r"C:\Users\mchauha1\Desktop\Foundation AI\IVI-APP\Wake-Word\Hey_Visteon_Mix"
NEGATIVE_SPEECH_DIR = r"C:\Users\mchauha1\Desktop\Foundation AI\IVI-APP\Wake-Word\Training_Model\data\negative_speech"
NEGATIVE_CONFUSION_DIR = r"C:\Users\mchauha1\Desktop\Foundation AI\IVI-APP\Wake-Word\Training_Model\data\negative_confusion"
MODEL_SAVE_PATH = "hey_visteon.onnx"

SAMPLE_RATE = 16000
DURATION = 1.0  # 1 second of audio
SAMPLES_PER_TRACK = int(SAMPLE_RATE * DURATION)

# --- 2. FEATURE EXTRACTION (MFCC) ---
def extract_features(file_path):
    # Load audio, ensuring it's exactly 1 second long
    signal, sr = librosa.load(file_path, sr=SAMPLE_RATE)
    
    # Pad or truncate to ensure uniform length
    if len(signal) > SAMPLES_PER_TRACK:
        signal = signal[:SAMPLES_PER_TRACK]
    else:
        signal = np.pad(signal, (0, max(0, SAMPLES_PER_TRACK - len(signal))), "constant")
        
    # Extract Mel-Frequency Cepstral Coefficients (standard for speech AI)
    mfccs = librosa.feature.mfcc(y=signal, sr=sr, n_mfcc=40, n_fft=400, hop_length=160)
    return mfccs.T  # Transpose so time is the first dimension

print("Loading audio files and extracting features. This will take a few minutes...")

X = []
y = []

# Load Positive Data (Label: 1)
for file in os.listdir(POSITIVE_DIR):
    if file.endswith(".wav"):
        X.append(extract_features(os.path.join(POSITIVE_DIR, file)))
        y.append(1)

# Load Negative Speech Data (Label: 0)
for file in os.listdir(NEGATIVE_SPEECH_DIR):
    if file.endswith(".wav"):
        X.append(extract_features(os.path.join(NEGATIVE_SPEECH_DIR, file)))
        y.append(0)

# Load Negative Confusion Data (Label: 0)
for file in os.listdir(NEGATIVE_CONFUSION_DIR):
    if file.endswith(".wav"):
        X.append(extract_features(os.path.join(NEGATIVE_CONFUSION_DIR, file)))
        y.append(0)

X = np.array(X)
y = np.array(y)

# Add a channel dimension for the CNN (Num_Samples, Time_Steps, MFCCs, 1)
X = X[..., np.newaxis]

print(f"Total dataset shape: {X.shape}")

# Split into training and testing sets (80% train, 20% test)
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

# --- 3. BUILD THE NEURAL NETWORK ---
print("Building and training the Convolutional Neural Network...")
input_shape = (X_train.shape[1], X_train.shape[2], 1)

model = tf.keras.models.Sequential([
    tf.keras.layers.Conv2D(16, (3, 3), activation='relu', input_shape=input_shape),
    tf.keras.layers.MaxPooling2D((2, 2)),
    tf.keras.layers.Conv2D(32, (3, 3), activation='relu'),
    tf.keras.layers.MaxPooling2D((2, 2)),
    tf.keras.layers.Flatten(),
    tf.keras.layers.Dense(64, activation='relu'),
    tf.keras.layers.Dropout(0.3),
    tf.keras.layers.Dense(1, activation='sigmoid') # 1 output neuron: 0 or 1
])

model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])

# Train the model!
history = model.fit(X_train, y_train, epochs=15, batch_size=32, validation_data=(X_test, y_test))

# --- 4. EXPORT TO ONNX FOR C++ ---
print("Training complete! Exporting to ONNX format...")
# Save the model temporarily in Keras format
model.save("temp_model.h5")

# Convert to ONNX
spec = (tf.TensorSpec((None, input_shape[0], input_shape[1], 1), tf.float32, name="input"),)
model_proto, _ = tf2onnx.convert.from_keras(model, input_signature=spec, opset=13, output_path=MODEL_SAVE_PATH)

print(f"\nSUCCESS! Your automotive wake word model is saved as: {MODEL_SAVE_PATH}")