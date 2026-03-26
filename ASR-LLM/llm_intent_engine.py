import google.generativeai as genai
import json

# 1. Initialize the LLM (Get a free key from aistudio.google.com)
API_KEY = "AIzaSyAo7r7R_s71F5Xh2PsiIBS9HtFckm2zW10"
genai.configure(api_key=API_KEY)

# 2. The Strict Automotive System Prompt
system_instruction = """
You are the voice assistant for an In-Vehicle Infotainment system. 
Your ONLY job is to map passenger seat heating/ventilation requests to our strict JSON schema.
If a user asks about massage or seat adjustment, politely decline in the tts_response.

Valid Actions:
- "activate_seat_heat" 
- "deactivate_seat_heat" 
- "get_seat_temperature" 
- "update_seat_temperature"

Valid Variables (Use null if not applicable):
- zone: "front" or "rear"
- seat_id: "1" (Left/Driver) or "2" (Right/Passenger)
- temperature: "+1", "+2", "+3" (Heating) OR "-1", "-2", "-3" (Cooling)

Output ONLY valid JSON matching this structure:
{
  "action": "...",
  "zone": "...",
  "seat_id": "...",
  "temperature": "...",
  "tts_response": "..."
}
"""

# Initialize the model with the system instructions
model = genai.GenerativeModel(
    model_name="gemini-2.5-flash",
    system_instruction=system_instruction,
    generation_config={"response_mime_type": "application/json"}
)

def process_voice_command(transcript):
    print(f"\nUser said: '{transcript}'")
    print("Thinking...")
    
    # Query the LLM
    response = model.generate_content(transcript)
    
    try:
        # Parse the output to ensure it's valid JSON
        intent_data = json.loads(response.text)
        print("\n--- ANDROID JSON PAYLOAD ---")
        print(json.dumps(intent_data, indent=2))
        return intent_data
    except json.JSONDecodeError:
        print("Error: LLM did not return valid JSON.")
        return None

# --- TEST THE GAUNTLET ---
if __name__ == "__main__":
    print("Testing SOP 1 Intent Engine...\n")
    
    # Test 1: Complex specific request
    process_voice_command("My back is freezing, turn the driver seat heat up to max.")
    
    # Test 2: Cooling request for passenger
    process_voice_command("The passenger is sweating, cool their seat down a little bit.")
    
    # Test 3: System shutdown
    process_voice_command("Actually, turn off all the seat heaters.")
    
    # Test 4: Out of bounds (Testing the SOP 1 constraint)
    process_voice_command("Turn on the seat massage for the driver.")