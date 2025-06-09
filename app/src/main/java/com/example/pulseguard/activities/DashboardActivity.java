package com.example.pulseguard.activities;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.pulseguard.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.SensorsClient;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "PulseGuardDashboard";

    private Location currentLocation;
    private FusedLocationProviderClient fusedLocationProviderClient;



    // Google Fit permissions
    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;
    private static final int ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 1002;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1003;

    // SOS permissions
    private static final int SOS_PERMISSION_REQUEST_CODE = 1004;
    private EditText etSOSContact;
    private SharedPreferences sharedPreferences;

    private float currentHeartRate = 0f;
    private int currentSteps = 0;
    private float currentCalories = 0f;

    private static final String PREF_NAME = "PulseGuardPrefs";
    private static final String KEY_SOS_CONTACT = "sos_contact";
    // Replace with actual emergency contact

    private TextView tvWelcome, tvSteps, tvCalories, tvHeartRate;
    private ProgressBar pbSteps, pbCalories, pbHeartRate;
    private Button btnExportPdf, btnSOS;

    private FitnessOptions fitnessOptions;
    private SensorsClient sensorsClient;
    private FusedLocationProviderClient fusedLocationClient;

    private OnDataPointListener stepListener;
    private OnDataPointListener heartRateListener;



    @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_dashboard);
            setTitle("PulseGuard Dashboard");

            initViews();

            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account == null) {
                Toast.makeText(this, "Please sign in with your Google account first.", Toast.LENGTH_LONG).show();
                // TODO: Redirect to sign-in activity if needed
                return;
            }

            checkPermissionsAndProceed();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnExportPdf.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                generatePdfReport();
            } else {
                requestStoragePermission();
            }
        });

        btnSOS.setOnClickListener(v -> {
            if (checkSOSPermissions()) {
                sendSOS();
            } else {
                requestSOSPermissions();
            }
        });
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvSteps = findViewById(R.id.tvSteps);
        tvCalories = findViewById(R.id.tvCalories);
        tvHeartRate = findViewById(R.id.tvHeartRate);

        pbSteps = findViewById(R.id.pbSteps);
        pbCalories = findViewById(R.id.pbCalories);
        pbHeartRate = findViewById(R.id.pbHeartRate);

        btnExportPdf = findViewById(R.id.btnExportPdf);
        btnSOS = findViewById(R.id.btn_sos);
        etSOSContact = findViewById(R.id.et_sos_contact);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Load previously saved contact if any
        String savedContact = sharedPreferences.getString(KEY_SOS_CONTACT, "");
        etSOSContact.setText(savedContact);

        // Set welcome message and max progress
        tvWelcome.setText("Welcome to PulseGuard Dashboard!");

        pbSteps.setMax(10000);     // 10,000 steps typical daily goal
        pbCalories.setMax(500);    // Approx calories burned typical goal
        pbHeartRate.setMax(200);   // Max heart rate typical upper limit
    }


    private void checkPermissionsAndProceed() {
        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE);
        } else {
            requestGoogleFitPermission();
        }
    }

    private void requestGoogleFitPermission() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Please sign in with your Google account first.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    account,
                    fitnessOptions
            );
        } else {
            fetchGoogleFitData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestGoogleFitPermission();
            } else {
                Toast.makeText(this, "Activity recognition permission denied.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                generatePdfReport();
            } else {
                Toast.makeText(this, "Storage permission denied. Cannot export PDF.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == SOS_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                sendSOS();
            } else {
                Toast.makeText(this, "Permissions are required to send SOS.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                fetchGoogleFitData();
            } else {
                Toast.makeText(this, "Google Fit permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fetchGoogleFitData() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Google account not signed in.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();

        // Set to start of day
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .read(DataType.TYPE_CALORIES_EXPENDED)
                .read(DataType.TYPE_HEART_RATE_BPM)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener(this::displayData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read Google Fit data", e);
                    Toast.makeText(this, "Failed to read Google Fit data", Toast.LENGTH_SHORT).show();
                });

        subscribeToLiveSensorData();
    }

    private void displayData(DataReadResponse response) {
        int totalSteps = 0;
        float totalCalories = 0f;
        Float latestHeartRate = null;
        long latestHeartTimestamp = 0;

        List<DataSet> dataSets = response.getDataSets();
        for (DataSet dataSet : dataSets) {
            for (DataPoint dp : dataSet.getDataPoints()) {
                for (Field field : dp.getDataType().getFields()) {
                    switch (field.getName()) {
                        case "steps":
                            totalSteps += dp.getValue(field).asInt();
                            break;
                        case "calories":
                            totalCalories += dp.getValue(field).asFloat();
                            break;
                        case "bpm":
                            float bpm = dp.getValue(field).asFloat();
                            long timestamp = dp.getTimestamp(TimeUnit.MILLISECONDS);
                            if (timestamp > latestHeartTimestamp) {
                                latestHeartTimestamp = timestamp;
                                latestHeartRate = bpm;
                            }
                            break;
                    }
                }
            }
        }

        currentSteps = totalSteps;
        currentCalories = totalCalories;
        currentHeartRate = latestHeartRate != null ? latestHeartRate : 0f;

        runOnUiThread(() -> {
            tvSteps.setText("🚶 Steps: " + currentSteps);
            pbSteps.setProgress(Math.min(currentSteps, pbSteps.getMax()));

            if (currentCalories > 0) {
                tvCalories.setText("🔥 Calories: " + String.format("%.1f", currentCalories));
                pbCalories.setProgress(Math.min((int) currentCalories, pbCalories.getMax()));
            } else {
                tvCalories.setText("🔥 Calories: No data");
                pbCalories.setProgress(0);
            }

            if (currentHeartRate > 0) {
                tvHeartRate.setText("💓 Heart Rate: " + String.format("%.1f bpm", currentHeartRate));
                pbHeartRate.setProgress(Math.min((int) currentHeartRate, pbHeartRate.getMax()));
            } else {
                tvHeartRate.setText("💓 Heart Rate: No data");
                pbHeartRate.setProgress(0);
            }
        });
    }

    private void subscribeToLiveSensorData() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Please sign in with Google", Toast.LENGTH_SHORT).show();
            return;
        }

        sensorsClient = Fitness.getSensorsClient(this, account);

        // Remove existing listeners if any
        if (stepListener != null) {
            sensorsClient.remove(stepListener);
            stepListener = null;
        }
        if (heartRateListener != null) {
            sensorsClient.remove(heartRateListener);
            heartRateListener = null;
        }

        // Step Listener - Accumulate steps live
        stepListener = dataPoint -> {
            int stepsDelta = 0;
            for (Field field : dataPoint.getDataType().getFields()) {
                if ("steps".equals(field.getName())) {
                    stepsDelta = dataPoint.getValue(field).asInt();
                    break;
                }
            }
            currentSteps += stepsDelta; // accumulate
            runOnUiThread(() -> {
                tvSteps.setText("🚶 Steps: " + currentSteps);
                pbSteps.setProgress(Math.min(currentSteps, pbSteps.getMax()));
            });
        };

        sensorsClient.findDataSources(
                        new DataSourcesRequest.Builder()
                                .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                                .setDataSourceTypes(com.google.android.gms.fitness.data.DataSource.TYPE_DERIVED)
                                .build())
                .addOnSuccessListener(dataSources -> {
                    for (com.google.android.gms.fitness.data.DataSource dataSource : dataSources) {
                        if (dataSource.getDataType().equals(DataType.TYPE_STEP_COUNT_DELTA)) {
                            sensorsClient.add(
                                            new SensorRequest.Builder()
                                                    .setDataSource(dataSource)
                                                    .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                                                    .setSamplingRate(1, TimeUnit.SECONDS)
                                                    .build(),
                                            stepListener)
                                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Step sensor listener registered"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to register step listener", e));
                            break;
                        }
                    }
                });

        // Heart Rate Listener
        heartRateListener = dataPoint -> {
            float bpm = 0f;
            for (Field field : dataPoint.getDataType().getFields()) {
                if ("bpm".equals(field.getName())) {
                    bpm = dataPoint.getValue(field).asFloat();
                    break;
                }
            }
            currentHeartRate = bpm;
            runOnUiThread(() -> {
                tvHeartRate.setText("💓 Heart Rate: " + String.format("%.1f bpm", currentHeartRate));
                pbHeartRate.setProgress(Math.min((int) currentHeartRate, pbHeartRate.getMax()));
            });
        };

        sensorsClient.findDataSources(
                        new DataSourcesRequest.Builder()
                                .setDataTypes(DataType.TYPE_HEART_RATE_BPM)
                                .setDataSourceTypes(com.google.android.gms.fitness.data.DataSource.TYPE_DERIVED)
                                .build())
                .addOnSuccessListener(dataSources -> {
                    for (com.google.android.gms.fitness.data.DataSource dataSource : dataSources) {
                        if (dataSource.getDataType().equals(DataType.TYPE_HEART_RATE_BPM)) {
                            sensorsClient.add(
                                            new SensorRequest.Builder()
                                                    .setDataSource(dataSource)
                                                    .setDataType(DataType.TYPE_HEART_RATE_BPM)
                                                    .setSamplingRate(1, TimeUnit.SECONDS)
                                                    .build(),
                                            heartRateListener)
                                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Heart rate sensor listener registered"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to register heart rate listener", e));
                            break;
                        }
                    }
                });
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage: no need to request WRITE_EXTERNAL_STORAGE for app-specific dirs
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST_CODE);
        }
    }

    private boolean checkSOSPermissions() {
        boolean smsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean fineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return smsPermission && fineLocationPermission;
    }

    private void requestSOSPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION
        }, SOS_PERMISSION_REQUEST_CODE);
    }

    private void sendSOS() {
        // Get emergency contact number entered by user
        String contactNumber = etSOSContact.getText().toString().trim();

        if (contactNumber.isEmpty()) {
            Toast.makeText(this, "Please enter an emergency contact number", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save the number to SharedPreferences for later use
        sharedPreferences.edit().putString(KEY_SOS_CONTACT, contactNumber).apply();

        // Check for location permission before fetching location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestSOSPermissions();
            return;
        }

        // Fetch the last known location
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            String message;
            if (location != null) {
                String locationUrl = "https://maps.google.com/?q="
                        + location.getLatitude() + "," + location.getLongitude();
                message = "Emergency! I need help. My location: " + locationUrl;
            } else {
                message = "Emergency! I need help. Location not available.";
            }
            // Send an SMS with the constructed message
            sendSMS(contactNumber, message);
        }).addOnFailureListener(e ->
                Toast.makeText(DashboardActivity.this, "Failed to get location", Toast.LENGTH_SHORT).show());
    }

    private void sendSMS(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "SOS message sent to " + phoneNumber, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }


    private void fetchLocationAndGeneratePDF() {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    currentLocation = location;
                    generatePdfReport();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show();
                    currentLocation = null;
                    generatePdfReport();
                });
    }


    private void generatePdfReport() {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();

        final int PAGE_WIDTH = 595;
        final int PAGE_HEIGHT = 842;
        final int MARGIN_LEFT = 60;
        final int MARGIN_TOP = 60;
        final int LINE_SPACING = 25;
        final int SECTION_SPACING = 40;

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int y = MARGIN_TOP;

        // Header background
        Paint headerBgPaint = new Paint();
        headerBgPaint.setColor(Color.parseColor("#E3F2FD"));
        canvas.drawRect(0, 0, PAGE_WIDTH, MARGIN_TOP + 50, headerBgPaint);

        // Title
        paint.setColor(Color.parseColor("#1A237E"));
        paint.setTextSize(24f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("PulseGuard Health Report", MARGIN_LEFT, y, paint);
        y += LINE_SPACING + 10;

        // Timestamp
        paint.setColor(Color.DKGRAY);
        paint.setTextSize(12f);
        paint.setTypeface(Typeface.DEFAULT);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        canvas.drawText("Generated on: " + timestamp, MARGIN_LEFT, y, paint);
        y += SECTION_SPACING;

        // ==== Fetch user info ====
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userName = "Unknown";
        String userEmail = "Unknown";
        String dobString = "2000-01-01";

        if (currentUser != null) {
            userName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User";
            userEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "N/A";

            SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
            dobString = prefs.getString("dob", "2000-01-01");
        }

        int userAge = calculateAgeFromDob(dobString);

        // ==== User Info Section ====
        paint.setColor(Color.BLACK);
        paint.setTextSize(16f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("User Information", MARGIN_LEFT, y, paint);
        y += LINE_SPACING;

        paint.setStrokeWidth(1);
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_LEFT, y, paint);
        y += LINE_SPACING;

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(14f);
        canvas.drawText("Name: " + userName, MARGIN_LEFT, y, paint); y += LINE_SPACING;
        canvas.drawText("Age: " + userAge + " years", MARGIN_LEFT, y, paint); y += LINE_SPACING;
        canvas.drawText("Email: " + userEmail, MARGIN_LEFT, y, paint); y += SECTION_SPACING;

        // ==== Health Metrics ====
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(16f);
        canvas.drawText("Health Metrics", MARGIN_LEFT, y, paint);
        y += LINE_SPACING;

        paint.setStrokeWidth(1);
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_LEFT, y, paint);
        y += LINE_SPACING;

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(14f);
        canvas.drawText("Steps: " + currentSteps, MARGIN_LEFT, y, paint); y += LINE_SPACING;
        canvas.drawText(String.format(Locale.getDefault(), "Calories: %.1f kcal", currentCalories), MARGIN_LEFT, y, paint); y += LINE_SPACING;
        canvas.drawText(String.format(Locale.getDefault(), "Heart Rate: %.1f bpm", currentHeartRate), MARGIN_LEFT, y, paint); y += SECTION_SPACING;

        // ==== Location Section ====
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(16f);
        canvas.drawText("Location Data", MARGIN_LEFT, y, paint);
        y += LINE_SPACING;

        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_LEFT, y, paint);
        y += LINE_SPACING;

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(14f);

        if (currentLocation != null) {
            double lat = currentLocation.getLatitude();
            double lon = currentLocation.getLongitude();
            float acc = currentLocation.getAccuracy();

            canvas.drawText(String.format(Locale.getDefault(), "Coordinates: %.6f, %.6f", lat, lon), MARGIN_LEFT, y, paint); y += LINE_SPACING;
            canvas.drawText(String.format(Locale.getDefault(), "Accuracy: %.1f meters", acc), MARGIN_LEFT, y, paint); y += LINE_SPACING;

            String mapsLink = "https://maps.google.com/?q=" + lat + "," + lon;
            canvas.drawText("Google Maps: " + mapsLink, MARGIN_LEFT, y, paint); y += LINE_SPACING;

            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String address = addresses.get(0).getAddressLine(0);
                    int maxCharsPerLine = 70;
                    if (address.length() > maxCharsPerLine) {
                        String[] split = splitLongText(address, maxCharsPerLine);
                        canvas.drawText("Address: " + split[0], MARGIN_LEFT, y, paint);
                        y += LINE_SPACING;
                        canvas.drawText(split[1], MARGIN_LEFT + 50, y, paint);
                    } else {
                        canvas.drawText("Address: " + address, MARGIN_LEFT, y, paint);
                    }
                    y += LINE_SPACING;
                } else {
                    canvas.drawText("Address: Not found", MARGIN_LEFT, y, paint); y += LINE_SPACING;
                }
            } catch (IOException e) {
                Log.e("PDF", "Geocoder failed: " + e.getMessage());
                canvas.drawText("Address: Unable to retrieve", MARGIN_LEFT, y, paint); y += LINE_SPACING;
            }
        } else {
            canvas.drawText("Location: Not available (please enable location)", MARGIN_LEFT, y, paint); y += LINE_SPACING;
        }

        // ==== Footer ====
        paint.setColor(Color.DKGRAY);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10f);
        canvas.drawText("Generated by PulseGuard - Confidential", PAGE_WIDTH / 2, PAGE_HEIGHT - 30, paint);

        pdfDocument.finishPage(page);

        // ==== Save PDF ====
        String fileName = "PulseGuard_Report_" + System.currentTimeMillis() + ".pdf";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PulseGuard");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);

                if (uri != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                        pdfDocument.writeTo(outputStream);
                        Toast.makeText(this, "PDF saved to Documents/PulseGuard", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Failed to create file URI", Toast.LENGTH_SHORT).show();
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PulseGuard");
                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    pdfDocument.writeTo(fos);
                    Toast.makeText(this, "PDF saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                }
            }
        } catch (IOException e) {
            Log.e("PDF", "Error saving PDF: ", e);
            Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            pdfDocument.close();
        }
    }

    // Optional helper to split long address lines
    private String[] splitLongText(String text, int maxChars) {
        if (text.length() <= maxChars) return new String[]{text, ""};
        return new String[]{text.substring(0, maxChars), text.substring(maxChars)};
    }


    // Helper method to calculate age from DOB string in format yyyy-MM-dd
    private int calculateAgeFromDob(String dobString) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date dob = sdf.parse(dobString);
            Calendar dobCal = Calendar.getInstance();
            dobCal.setTime(dob);

            Calendar today = Calendar.getInstance();

            int age = today.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }
            return age;
        } catch (ParseException e) {
            e.printStackTrace();
            return 0; // fallback age
        }
    }





    @Override
    protected void onPause() {
        super.onPause();
        // Remove listeners to avoid memory leaks
        if (sensorsClient != null) {
            if (stepListener != null) {
                sensorsClient.remove(stepListener);
                stepListener = null;
            }
            if (heartRateListener != null) {
                sensorsClient.remove(heartRateListener);
                heartRateListener = null;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-subscribe listeners on resume if Google Fit permission granted
        if (GoogleSignIn.getLastSignedInAccount(this) != null && fitnessOptions != null) {
            subscribeToLiveSensorData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorsClient != null) {
            if (stepListener != null) {
                sensorsClient.remove(stepListener);
                stepListener = null;
            }
            if (heartRateListener != null) {
                sensorsClient.remove(heartRateListener);
                heartRateListener = null;
            }
        }
    }
}