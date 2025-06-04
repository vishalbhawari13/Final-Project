package com.example.pulseguard.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataReadResponse;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "PulseGuardDashboard";
    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;
    private static final int ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 1002;

    private TextView tvWelcome, tvSteps, tvCalories, tvHeartRate;
    private ProgressBar pbSteps, pbCalories, pbHeartRate;

    private FitnessOptions fitnessOptions;
    private SensorsClient sensorsClient;
    private OnDataPointListener stepListener, heartRateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        setTitle("PulseGuard Dashboard");

        initViews();
        checkPermissionsAndContinue();
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvSteps = findViewById(R.id.tvSteps);
        tvCalories = findViewById(R.id.tvCalories);
        tvHeartRate = findViewById(R.id.tvHeartRate);

        pbSteps = findViewById(R.id.pbSteps);
        pbCalories = findViewById(R.id.pbCalories);
        pbHeartRate = findViewById(R.id.pbHeartRate);

        tvWelcome.setText("Welcome to PulseGuard Dashboard!");

        pbSteps.setMax(10000);     // Assuming 10k step goal
        pbCalories.setMax(500);    // Assuming 500 cal goal
        pbHeartRate.setMax(200);   // Max healthy heart rate
    }

    private void checkPermissionsAndContinue() {
        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE);
        } else {
            requestGoogleFitPermissions();
        }
    }

    private void requestGoogleFitPermissions() {
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
        if (requestCode == ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestGoogleFitPermissions();
            } else {
                Toast.makeText(this, "Activity recognition permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                fetchGoogleFitData();
            } else {
                Toast.makeText(this, "Google Fit permission denied", Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
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

        subscribeToSensors();
    }

    private void displayData(DataReadResponse response) {
        int totalSteps = 0;
        float totalCalories = 0f;
        Float recentHeartRate = null;
        long latestHeartTimestamp = 0;

        for (DataSet dataSet : response.getDataSets()) {
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
                                recentHeartRate = bpm;
                            }
                            break;
                    }
                }
            }
        }

        final int stepsFinal = totalSteps;
        final float caloriesFinal = totalCalories;
        final float heartRateFinal = recentHeartRate != null ? recentHeartRate : 0f;

        runOnUiThread(() -> {
            tvSteps.setText("🚶 Steps: " + stepsFinal);
            pbSteps.setProgress(Math.min(stepsFinal, pbSteps.getMax()));

            tvCalories.setText("🔥 Calories: " + String.format("%.1f", caloriesFinal));
            pbCalories.setProgress(Math.min((int) caloriesFinal, pbCalories.getMax()));

            if (heartRateFinal > 0) {
                tvHeartRate.setText("💓 Heart Rate: " + String.format("%.1f bpm", heartRateFinal));
                pbHeartRate.setProgress(Math.min((int) heartRateFinal, pbHeartRate.getMax()));
            } else {
                tvHeartRate.setText("💓 Heart Rate: No data");
                pbHeartRate.setProgress(0);
            }
        });
    }

    private void subscribeToSensors() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Please sign in with Google", Toast.LENGTH_SHORT).show();
            return;
        }

        sensorsClient = Fitness.getSensorsClient(this, account);

        // Step Count Listener
        stepListener = new OnDataPointListener() {
            @Override
            public void onDataPoint(DataPoint dataPoint) {
                int steps = 0;
                for (Field field : dataPoint.getDataType().getFields()) {
                    if ("steps".equals(field.getName())) {
                        steps = dataPoint.getValue(field).asInt();
                        break;
                    }
                }
                final int finalSteps = steps;
                runOnUiThread(() -> {
                    tvSteps.setText("🚶 Steps: " + finalSteps);
                    pbSteps.setProgress(Math.min(finalSteps, pbSteps.getMax()));
                });
            }
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
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to register step sensor listener", e));
                        }
                    }
                });

        // Heart Rate Listener
        heartRateListener = new OnDataPointListener() {
            @Override
            public void onDataPoint(DataPoint dataPoint) {
                float heartRate = 0f;
                for (Field field : dataPoint.getDataType().getFields()) {
                    if ("bpm".equals(field.getName())) {
                        heartRate = dataPoint.getValue(field).asFloat();
                        break;
                    }
                }
                final float finalHeartRate = heartRate;
                runOnUiThread(() -> {
                    tvHeartRate.setText("💓 Heart Rate: " + String.format("%.1f bpm", finalHeartRate));
                    pbHeartRate.setProgress(Math.min((int) finalHeartRate, pbHeartRate.getMax()));
                });
            }
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
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorsClient != null) {
            if (stepListener != null) {
                sensorsClient.remove(stepListener);
            }
            if (heartRateListener != null) {
                sensorsClient.remove(heartRateListener);
            }
        }
    }
}
