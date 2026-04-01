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
    boolean frontselected = true;

    // --- BULLETPROOF LOGIC FLAGS ---
    boolean isUpdatingUI = false; // Blocks the "Spinner Loop of Death"
    boolean isSystemOn = false;   // Tracks power state accurately

    // --- STATE CACHE ---
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
                if (isUpdatingUI) return; // Block physical clicks if AI is controlling

                if (!isSystemOn) {
                    // Turn System ON
                    isSystemOn = true;
                    frontselected = true;
                    changeactivationBtnclr.setBackgroundResource(R.drawable.vector_on);
                    seatheatfeatureselection.setImageResource(R.drawable.linetomarkselectedseatregion);
                    frontzoneselection.setImageResource(R.drawable.linetomarkselectedregion);
                    rearzoneselection.setImageDrawable(null);
                    frontzonebtn.setEnabled(true);
                    rearzonebtn.setEnabled(true);
                    leftseatdropdown.setEnabled(true);
                    rightseatdropdown.setEnabled(true);

                    isUpdatingUI = true;
                    leftseatdropdown.setSelection(0);
                    rightseatdropdown.setSelection(0);
                    isUpdatingUI = false;

                    seatright.setImageResource(R.drawable.frontseat_rightplus1temp);
                    seatleft.setImageResource(R.drawable.frontseat_leftplus1temp);
                    leftseatdropdownicon.setImageResource(R.drawable.vector_dropdown);
                    rightseatdropdownicon.setImageResource(R.drawable.vector_dropdown);

                    frontLeftTemp = 1; frontRightTemp = 1;
                    rearLeftTemp = 1; rearRightTemp = 1;

                    sendMessage("activate_seat_heat-->front");
                } else {
                    // Turn System OFF
                    isSystemOn = false;
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

                    frontLeftTemp = 0; frontRightTemp = 0;
                    rearLeftTemp = 0; rearRightTemp = 0;

                    sendMessage("deactivate_seat_heat");
                }
            }
        });

        // --- REAR ZONE BUTTON ---
        rearzonebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                frontselected = false;
                rearzoneselection.setImageResource(R.drawable.linetomarkselectedregion);
                frontzoneselection.setImageDrawable(null);
                seatright.setImageResource(R.drawable.rear_rightseat);
                seatleft.setImageResource(R.drawable.rear_leftseat);

                if (rearLeftTemp != 0) updateUIBasedOnTemperature("rear-->1-->" + rearLeftTemp);
                if (rearRightTemp != 0) updateUIBasedOnTemperature("rear-->2-->" + rearRightTemp);
            }
        });

        // --- FRONT ZONE BUTTON ---
        frontzonebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                frontselected = true;
                frontzoneselection.setImageResource(R.drawable.linetomarkselectedregion);
                rearzoneselection.setImageDrawable(null);
                seatright.setImageResource(R.drawable.img_right);
                seatleft.setImageResource(R.drawable.img_left);

                if (frontLeftTemp != 0) updateUIBasedOnTemperature("front-->1-->" + frontLeftTemp);
                if (frontRightTemp != 0) updateUIBasedOnTemperature("front-->2-->" + frontRightTemp);
            }
        });

        // --- LEFT SEAT SPINNER ---
        leftseatdropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                String selectedTemp = parent.getItemAtPosition(position).toString();
                String zone = frontselected ? "front" : "rear";
                sendMessage(zone + "-->1-->" + selectedTemp);
                updateUIBasedOnTemperature(zone + "-->1-->" + selectedTemp.replace("+", ""));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // --- RIGHT SEAT SPINNER ---
        rightseatdropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingUI) return;
                String selectedTemp = parent.getItemAtPosition(position).toString();
                String zone = frontselected ? "front" : "rear";
                sendMessage(zone + "-->2-->" + selectedTemp);
                updateUIBasedOnTemperature(zone + "-->2-->" + selectedTemp.replace("+", ""));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        startPersistentListener();
    }

    // --- PERSISTENT BACKGROUND LISTENER ---
    private void startPersistentListener() {
        new Thread(() -> {
            while (true) {
                try (Socket socket = new Socket("10.0.2.2", 12345);
                     BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    Log.d("VoiceAI", "Listening for Python ASR Commands...");
                    String incomingData;

                    while ((incomingData = bufferedReader.readLine()) != null) {
                        Log.d("VoiceAI", "Received from Server: " + incomingData);
                        final String finalData = incomingData;

                        runOnUiThread(() -> {
                            textViewResponse.setText("Voice Command Executed!");
                            updateUIBasedOnTemperature(finalData);
                        });
                    }
                } catch (Exception e) {
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { ie.printStackTrace(); }
                }
            }
        }).start();
    }

    public void sendMessage(final String message) {
        new Thread(() -> {
            try (Socket socket = new Socket("10.0.2.2", 12345);
                 OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream())) {
                outputStreamWriter.write(message + "\n");
                outputStreamWriter.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // --- MASTER UI & CACHE UPDATER ---
    private void updateUIBasedOnTemperature(String temp) {
        isUpdatingUI = true; // LOCK THE UI

        try {
            if (temp.equals("deactivate_seat_heat")) {
                if (isSystemOn) changeactivationBtnclr.performClick();
                return;
            }

            if (temp.startsWith("activate_seat_heat")) {
                if (!isSystemOn) changeactivationBtnclr.performClick();
                return;
            }

            // SAFETY SCRUBBER: If Python accidentally sends the 4-part string, fix it!
            if (temp.startsWith("update_seat_temperature-->")) {
                temp = temp.replace("update_seat_temperature-->", "");
            }

            String[] parts = temp.split("-->");
            if (parts.length != 3) {
                Log.e("VoiceAI", "Ignored malformed command: " + temp);
                return;
            }

            String zone = parts[0].trim();
            int seatId = Integer.parseInt(parts[1].trim());
            int temperature = Integer.parseInt(parts[2].replace("+", "").trim());

            if (!isSystemOn) changeactivationBtnclr.performClick();

            if (zone.equals("front") && seatId == 1) frontLeftTemp = temperature;
            if (zone.equals("front") && seatId == 2) frontRightTemp = temperature;
            if (zone.equals("rear") && seatId == 1) rearLeftTemp = temperature;
            if (zone.equals("rear") && seatId == 2) rearRightTemp = temperature;

            if ((zone.equals("front") && frontselected) || (zone.equals("rear") && !frontselected)) {
                if (seatId == 1) {
                    switch (temperature) {
                        case 1: leftseatdropdown.setSelection(0); seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftplus1temp : R.drawable.rearseat_leftplus1temp); break;
                        case 2: leftseatdropdown.setSelection(1); seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftplus2temp : R.drawable.rearseat_leftplus2temp); break;
                        case 3: leftseatdropdown.setSelection(2); seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftplus3temp : R.drawable.rearseat_leftplus3temp); break;
                        case -1: leftseatdropdown.setSelection(3); seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftminus1temp : R.drawable.rearseat_leftminus1temp); break;
                        case -2: leftseatdropdown.setSelection(4); seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftminus2temp : R.drawable.rearseat_leftminus2temp); break;
                        case -3: leftseatdropdown.setSelection(5); seatleft.setImageResource(frontselected ? R.drawable.frontseat_leftminus3temp : R.drawable.rearseat_leftminus3temp); break;
                    }
                } else if (seatId == 2) {
                    switch (temperature) {
                        case 1: rightseatdropdown.setSelection(0); seatright.setImageResource(frontselected ? R.drawable.frontseat_rightplus1temp : R.drawable.rearseat_rightplus1temp); break;
                        case 2: rightseatdropdown.setSelection(1); seatright.setImageResource(frontselected ? R.drawable.frontseat_rightplus2temp : R.drawable.rearseat_rightplus2temp); break;
                        case 3: rightseatdropdown.setSelection(2); seatright.setImageResource(frontselected ? R.drawable.frontseat_rightplus3temp : R.drawable.rearseat_rightplus3temp); break;
                        case -1: rightseatdropdown.setSelection(3); seatright.setImageResource(frontselected ? R.drawable.frontseat_rightminus1temp : R.drawable.rearseat_rightminus1temp); break;
                        case -2: rightseatdropdown.setSelection(4); seatright.setImageResource(frontselected ? R.drawable.frontseat_rightminus2temp : R.drawable.rearseat_rightminus2temp); break;
                        case -3: rightseatdropdown.setSelection(5); seatright.setImageResource(frontselected ? R.drawable.frontseat_rightminus3temp : R.drawable.rearseat_rightminus3temp); break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("UIUpdater", "Error parsing string: " + temp, e);
        } finally {
            isUpdatingUI = false; // UNLOCK THE UI
        }
    }
}