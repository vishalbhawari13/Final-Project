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
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
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

    private OnDataPointListener stepListener;
    private OnDataPointListener heartRateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        setTitle("PulseGuard Dashboard");

        initViews();
        checkPermissionsAndProceed();
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

        // Set progress bar max values for goals
        pbSteps.setMax(10000);    // Example: 10k steps goal
        pbCalories.setMax(500);   // Example: 500 calories goal
        pbHeartRate.setMax(200);  // Max heart rate expected
    }

    /**
     * Check for Activity Recognition and Google Fit permissions, request if needed.
     */
    private void checkPermissionsAndProceed() {
        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            // Request Activity Recognition permission for API 29+
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE);
        } else {
            requestGoogleFitPermission();
        }
    }

    /**
     * Request Google Fit permission for the signed-in Google account.
     */
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
        if (requestCode == ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestGoogleFitPermission();
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
                Toast.makeText(this, "Google Fit permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Fetch aggregated Google Fit data for the current day.
     */
    private void fetchGoogleFitData() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Google account not signed in.", Toast.LENGTH_SHORT).show();
            return;
        }

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

        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener(this::displayData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read Google Fit data", e);
                    Toast.makeText(this, "Failed to read Google Fit data", Toast.LENGTH_SHORT).show();
                });

        subscribeToLiveSensorData();
    }

    /**
     * Parse data from Google Fit and update UI.
     */
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

        final int stepsToShow = totalSteps;
        final float caloriesToShow = totalCalories;
        final float heartRateToShow = latestHeartRate != null ? latestHeartRate : 0f;

        runOnUiThread(() -> {
            tvSteps.setText("🚶 Steps: " + stepsToShow);
            pbSteps.setProgress(Math.min(stepsToShow, pbSteps.getMax()));

            if (caloriesToShow > 0) {
                tvCalories.setText("🔥 Calories: " + String.format("%.1f", caloriesToShow));
                pbCalories.setProgress(Math.min((int) caloriesToShow, pbCalories.getMax()));
            } else {
                tvCalories.setText("🔥 Calories: No data");
                pbCalories.setProgress(0);
            }

            if (heartRateToShow > 0) {
                tvHeartRate.setText("💓 Heart Rate: " + String.format("%.1f bpm", heartRateToShow));
                pbHeartRate.setProgress(Math.min((int) heartRateToShow, pbHeartRate.getMax()));
            } else {
                tvHeartRate.setText("💓 Heart Rate: No data");
                pbHeartRate.setProgress(0);
            }
        });
    }

    /**
     * Subscribe to live step count and heart rate sensor updates.
     */
    private void subscribeToLiveSensorData() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Please sign in with Google", Toast.LENGTH_SHORT).show();
            return;
        }

        sensorsClient = Fitness.getSensorsClient(this, account);

        // Unregister previous listeners if any
        if (stepListener != null) {
            sensorsClient.remove(stepListener);
            stepListener = null;
        }
        if (heartRateListener != null) {
            sensorsClient.remove(heartRateListener);
            heartRateListener = null;
        }

        // Step listener
        stepListener = dataPoint -> {
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
        };

        // Register step count sensor listener
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
                            break; // register only once
                        }
                    }
                });

        // Heart rate listener
        heartRateListener = dataPoint -> {
            float bpm = 0f;
            for (Field field : dataPoint.getDataType().getFields()) {
                if ("bpm".equals(field.getName())) {
                    bpm = dataPoint.getValue(field).asFloat();
                    break;
                }
            }
            final float finalBpm = bpm;
            runOnUiThread(() -> {
                tvHeartRate.setText("💓 Heart Rate: " + String.format("%.1f bpm", finalBpm));
                pbHeartRate.setProgress(Math.min((int) finalBpm, pbHeartRate.getMax()));
            });
        };

        // Register heart rate sensor listener
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
                            break; // register only once
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
                stepListener = null;
            }
            if (heartRateListener != null) {
                sensorsClient.remove(heartRateListener);
                heartRateListener = null;
            }
        }
    }
}
