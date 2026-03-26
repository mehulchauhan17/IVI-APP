package com.intern.car;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class MainActivity extends Activity {
    Button changeactivationBtnclr, seatheatbtn, frontzonebtn, rearzonebtn, homeiconbtn;
    ImageView seatright, seatleft, rearzoneselection, frontzoneselection, seatheatfeatureselection, leftseatdropdownicon, rightseatdropdownicon;
    private TextView textViewResponse;

    Spinner leftseatdropdown;
    Spinner rightseatdropdown;
    boolean frontselected;

    // --- STATE CACHE: Remembers seat temperatures instantly ---
    int frontLeftTemp = 0;
    int frontRightTemp = 0;
    int rearLeftTemp = 0;
    int rearRightTemp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewResponse = findViewById(R.id.textViewResponse);

        String[] arraySpinner = new String[]{"+1", "+2", "+3", "-1", "-2", "-3"};

        leftseatdropdown = findViewById(R.id.spinner2);
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, arraySpinner);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        leftseatdropdown.setAdapter(adapter2);

        rightseatdropdown = findViewById(R.id.spinner3);
        ArrayAdapter<String> adapter3 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, arraySpinner);
        adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rightseatdropdown.setAdapter(adapter3);

        leftseatdropdown.setEnabled(false);
        rightseatdropdown.setEnabled(false);

        changeactivationBtnclr = findViewById(R.id.activation_btn);
        seatheatbtn = findViewById(R.id.seatheatbtn);
        seatright = findViewById(R.id.imagePower);
        seatleft = findViewById(R.id.imageHome);
        frontzonebtn = findViewById(R.id.frontbutton);
        frontzonebtn.setEnabled(false);
        rearzonebtn = findViewById(R.id.rearbutton);
        rearzonebtn.setEnabled(false);
        rearzoneselection = findViewById(R.id.rearzoneselection);
        frontzoneselection = findViewById(R.id.frontzoneselection);
        seatheatfeatureselection = findViewById(R.id.seatheatselection);
        leftseatdropdownicon = findViewById(R.id.leftseatdropdownicon);
        rightseatdropdownicon = findViewById(R.id.rightseatdropdownicon);

        leftseatdropdownicon.setImageResource(R.drawable.offstatedropdownicon);
        rightseatdropdownicon.setImageResource(R.drawable.offstatedropdownicon);

        // --- MASTER POWER BUTTON ---
        changeactivationBtnclr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Drawable btnBackground = changeactivationBtnclr.getBackground();
                if (btnBackground.getConstantState() == (getResources().getDrawable(R.drawable.vector_onoff).getConstantState())) {
                    frontselected = true;
                    changeactivationBtnclr.setBackgroundResource(R.drawable.vector_on);
                    seatheatfeatureselection.setImageResource(R.drawable.linetomarkselectedseatregion);
                    frontzoneselection.setImageResource(R.drawable.linetomarkselectedregion);
                    rearzoneselection.setImageDrawable(null);
                    frontzonebtn.setEnabled(true);
                    rearzonebtn.setEnabled(true);
                    leftseatdropdown.setEnabled(true);
                    rightseatdropdown.setEnabled(true);
                    leftseatdropdown.setSelection(0);
                    rightseatdropdown.setSelection(0);
                    seatright.setImageResource(R.drawable.frontseat_rightplus1temp);
                    seatleft.setImageResource(R.drawable.frontseat_leftplus1temp);
                    leftseatdropdownicon.setImageResource(R.drawable.vector_dropdown);
                    rightseatdropdownicon.setImageResource(R.drawable.vector_dropdown);

                    // Reset cache to +1 on power up
                    frontLeftTemp = 1; frontRightTemp = 1;
                    rearLeftTemp = 1; rearRightTemp = 1;

                    sendMessage("activate_seat_heat-->front");
                } else if (btnBackground.getConstantState() == (getResources().getDrawable(R.drawable.vector_on).getConstantState())) {
                    changeactivationBtnclr.setBackgroundResource(R.drawable.vector_onoff);
                    frontzonebtn.setEnabled(false);
                    rearzonebtn.setEnabled(false);
                    seatright.setImageResource(R.drawable.img_right);
                    seatleft.setImageResource(R.drawable.img_left);
                    leftseatdropdown.setEnabled(false);
                    rightseatdropdown.setEnabled(false);
                    frontzoneselection.setImageDrawable(null);
                    rearzoneselection.setImageDrawable(null);
                    leftseatdropdownicon.setImageResource(R.drawable.offstatedropdownicon);
                    rightseatdropdownicon.setImageResource(R.drawable.offstatedropdownicon);

                    // Clear cache on power down
                    frontLeftTemp = 0; frontRightTemp = 0;
                    rearLeftTemp = 0; rearRightTemp = 0;

                    sendMessage("deactivate_seat_heat");
                }
            }
        });

        // --- REAR ZONE BUTTON (Instant Cache Load) ---
        rearzonebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                frontselected = false;

                rearzoneselection.setImageResource(R.drawable.linetomarkselectedregion);
                frontzoneselection.setImageDrawable(null);
                seatright.setImageResource(R.drawable.rear_rightseat);
                seatleft.setImageResource(R.drawable.rear_leftseat);

                // Restore from Cache!
                if (rearLeftTemp != 0) updateUIBasedOnTemperature("rear-->1-->" + rearLeftTemp);
                if (rearRightTemp != 0) updateUIBasedOnTemperature("rear-->2-->" + rearRightTemp);
            }
        });

        // --- FRONT ZONE BUTTON (Instant Cache Load) ---
        frontzonebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                frontselected = true;

                frontzoneselection.setImageResource(R.drawable.linetomarkselectedregion);
                rearzoneselection.setImageDrawable(null);
                seatright.setImageResource(R.drawable.img_right);
                seatleft.setImageResource(R.drawable.img_left);

                // Restore from Cache!
                if (frontLeftTemp != 0) updateUIBasedOnTemperature("front-->1-->" + frontLeftTemp);
                if (frontRightTemp != 0) updateUIBasedOnTemperature("front-->2-->" + frontRightTemp);
            }
        });

        // --- LEFT SEAT SPINNER ---
        leftseatdropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedTemp = parent.getItemAtPosition(position).toString();
                String zone = frontselected ? "front" : "rear";
                String command = "update_seat_temperature-->" + zone + "-->1-->" + selectedTemp;

                sendMessage(command);
                updateUIBasedOnTemperature(zone + "-->1-->" + selectedTemp.replace("+", ""));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // --- RIGHT SEAT SPINNER ---
        rightseatdropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedTemp = parent.getItemAtPosition(position).toString();
                String zone = frontselected ? "front" : "rear";
                String command = "update_seat_temperature-->" + zone + "-->2-->" + selectedTemp;

                sendMessage(command);
                updateUIBasedOnTemperature(zone + "-->2-->" + selectedTemp.replace("+", ""));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 🚀 START THE BACKGROUND VOICE LISTENER
        startPersistentListener();
    }

    // --- 📡 THE NEW PERSISTENT BACKGROUND LISTENER ---
    private void startPersistentListener() {
        new Thread(() -> {
            while (true) { // Auto-reconnect loop
                // NOTE: 10.0.2.2 is the special Android Emulator IP that routes to your PC
                try (Socket socket = new Socket("10.0.2.2", 12345);
                     BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    Log.d("VoiceAI", "Listening for Python ASR Commands on 10.0.2.2...");
                    String incomingData;

                    // Runs forever as long as the server sends data
                    while ((incomingData = bufferedReader.readLine()) != null) {
                        Log.d("VoiceAI", "Received from Server: " + incomingData);

                        final String finalData = incomingData;

                        // Jump back to the main UI thread to update images
                        runOnUiThread(() -> {
                            textViewResponse.setText("Voice Command Executed!");
                            updateUIBasedOnTemperature(finalData);
                        });
                    }
                } catch (Exception e) {
                    Log.e("VoiceAI", "Server connection lost. Retrying in 3 seconds...", e);
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { ie.printStackTrace(); }
                }
            }
        }).start();
    }

    // --- 🚀 THE SIMPLIFIED NETWORK SENDER ---
    public void sendMessage(final String message) {
        new Thread(() -> {
            // NOTE: 10.0.2.2 is the special Android Emulator IP that routes to your PC
            try (Socket socket = new Socket("10.0.2.2", 12345);
                 OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream())) {

                // Just send the UI button click and close. The Listener thread catches the reply!
                outputStreamWriter.write(message + "\n");
                outputStreamWriter.flush();
                Log.d("VoiceAI", "App Sent: " + message);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // --- 🧠 MASTER UI & CACHE UPDATER ---
    private void updateUIBasedOnTemperature(String temp) {
        try {
            // 1. Handle Power OFF via Voice
            if (temp.equals("deactivate_seat_heat")) {
                Drawable btnBackground = changeactivationBtnclr.getBackground();
                // If it is currently ON, virtually click it to turn it OFF
                if (btnBackground.getConstantState() == (getResources().getDrawable(R.drawable.vector_on).getConstantState())) {
                    changeactivationBtnclr.performClick();
                }
                return;
            }

            // 2. Handle Power ON via Voice
            if (temp.startsWith("activate_seat_heat")) {
                Drawable btnBackground = changeactivationBtnclr.getBackground();
                // If it is currently OFF, virtually click it to turn it ON
                if (btnBackground.getConstantState() == (getResources().getDrawable(R.drawable.vector_onoff).getConstantState())) {
                    changeactivationBtnclr.performClick();
                }
                return;
            }

            // 3. Handle specific temperature changes
            String[] parts = temp.split("-->");
            if (parts.length != 3) return;

            String zone = parts[0].trim();
            int seatId = Integer.parseInt(parts[1].trim());
            int temperature = Integer.parseInt(parts[2].replace("+", "").trim());

            // Auto-turn ON the system if the user asks for a specific temperature while the power is off
            Drawable btnBackground = changeactivationBtnclr.getBackground();
            if (btnBackground.getConstantState() == (getResources().getDrawable(R.drawable.vector_onoff).getConstantState())) {
                changeactivationBtnclr.performClick();
            }

            // 4. UPDATE THE MEMORY CACHE
            if (zone.equals("front") && seatId == 1) frontLeftTemp = temperature;
            if (zone.equals("front") && seatId == 2) frontRightTemp = temperature;
            if (zone.equals("rear") && seatId == 1) rearLeftTemp = temperature;
            if (zone.equals("rear") && seatId == 2) rearRightTemp = temperature;

            // 5. ONLY UPDATE UI IF WE ARE LOOKING AT THE CORRECT ZONE
            if ((zone.equals("front") && frontselected) || (zone.equals("rear") && !frontselected)) {

                if (seatId == 1) { // LEFT SEAT
                    switch (temperature) {
                        case 1:
                            leftseatdropdown.setSelection(0);
                            seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftplus1temp : R.drawable.rearseat_leftplus1temp);
                            break;
                        case 2:
                            leftseatdropdown.setSelection(1);
                            seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftplus2temp : R.drawable.rearseat_leftplus2temp);
                            break;
                        case 3:
                            leftseatdropdown.setSelection(2);
                            seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftplus3temp : R.drawable.rearseat_leftplus3temp);
                            break;
                        case -1:
                            leftseatdropdown.setSelection(3);
                            seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftminus1temp : R.drawable.rearseat_leftminus1temp);
                            break;
                        case -2:
                            leftseatdropdown.setSelection(4);
                            seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftminus2temp : R.drawable.rearseat_leftminus2temp);
                            break;
                        case -3:
                            leftseatdropdown.setSelection(5);
                            seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftminus3temp : R.drawable.rearseat_leftminus3temp);
                            break;
                    }
                } else if (seatId == 2) { // RIGHT SEAT
                    switch (temperature) {
                        case 1:
                            rightseatdropdown.setSelection(0);
                            seatright.setImageResource(frontselected ? R.drawable.frontseat_rightplus1temp : R.drawable.rearseat_rightplus1temp);
                            break;
                        case 2:
                            rightseatdropdown.setSelection(1);
                            seatright.setImageResource(frontselected ? R.drawable.frontseat_rightplus2temp : R.drawable.rearseat_rightplus2temp);
                            break;
                        case 3:
                            rightseatdropdown.setSelection(2);
                            seatright.setImageResource(frontselected ? R.drawable.frontseat_rightplus3temp : R.drawable.rearseat_rightplus3temp);
                            break;
                        case -1:
                            rightseatdropdown.setSelection(3);
                            seatright.setImageResource(frontselected ? R.drawable.frontseat_rightminus1temp : R.drawable.rearseat_rightminus1temp);
                            break;
                        case -2:
                            rightseatdropdown.setSelection(4);
                            seatright.setImageResource(frontselected ? R.drawable.frontseat_rightminus2temp : R.drawable.rearseat_rightminus2temp);
                            break;
                        case -3:
                            rightseatdropdown.setSelection(5);
                            seatright.setImageResource(frontselected ? R.drawable.frontseat_rightminus3temp : R.drawable.rearseat_rightminus3temp);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("UIUpdater", "Error parsing temperature string: " + temp);
        }
    }
}