package com.example.pulseguard.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;
    private static final int ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 1002;
    private static final String TAG = "PulseGuardDashboard";

    private TextView tvWelcome, tvSteps, tvCalories, tvHeartRate;

    private FitnessOptions fitnessOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        setTitle("PulseGuard Dashboard");

        tvWelcome = findViewById(R.id.tvWelcome);
        tvSteps = findViewById(R.id.tvSteps);
        tvCalories = findViewById(R.id.tvCalories);
        tvHeartRate = findViewById(R.id.tvHeartRate);

        tvWelcome.setText("Welcome to PulseGuard Dashboard!");

        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
                .build();

        // Check runtime permission for ACTIVITY_RECOGNITION (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                        ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE);
                return; // Wait for permission result
            }
        }

        requestGoogleFitPermissions();
    }

    private void requestGoogleFitPermissions() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);

        if (account == null) {
            Toast.makeText(this, "Please sign in with your Google account first.", Toast.LENGTH_LONG).show();
            // Optionally, launch sign-in intent here
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
                requestGoogleFitPermissions();
            } else {
                Toast.makeText(this, "Permission for activity recognition denied.", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Google Fit permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fetchGoogleFitData() {
        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();

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

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Google account not signed in.", Toast.LENGTH_SHORT).show();
            return;
        }

        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener(this::displayData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error reading Google Fit data: " + e.getMessage());
                    Toast.makeText(this, "Failed to read Google Fit data", Toast.LENGTH_SHORT).show();
                });
    }

    private void displayData(DataReadResponse response) {
        int totalSteps = 0;
        float totalCalories = 0f;
        Float recentHeartRate = null;
        long recentHeartRateTimestamp = 0;

        // Process each dataset returned from Google Fit
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
                            long timestamp = dp.getTimestamp(TimeUnit.MILLISECONDS);
                            float currentHeartRate = dp.getValue(field).asFloat();
                            // Check if this heart rate is the most recent one
                            if (timestamp > recentHeartRateTimestamp) {
                                recentHeartRateTimestamp = timestamp;
                                recentHeartRate = currentHeartRate;
                            }
                            break;
                    }
                }
            }
        }

        // Use 0 if no heart rate found
        final float heartRateToShow = recentHeartRate != null ? recentHeartRate : 0f;
        final int stepsToShow = totalSteps;
        final float caloriesToShow = totalCalories;

        // Update UI on main thread
        runOnUiThread(() -> {
            tvSteps.setText("🚶 Steps Today: " + stepsToShow);
            tvCalories.setText("🔥 Calories Burned: " + String.format("%.1f", caloriesToShow));
            tvHeartRate.setText("💓 Recent Heart Rate: " + (heartRateToShow > 0 ? String.format("%.1f", heartRateToShow) + " bpm" : "No data"));
        });
    }

}
