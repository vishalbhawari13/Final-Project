package com.example.pulseguard.activities;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "PulseGuardDashboard";

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;
    private static final int ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 1002;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1003;

    private TextView tvWelcome, tvSteps, tvCalories, tvHeartRate;
    private ProgressBar pbSteps, pbCalories, pbHeartRate;
    private Button btnExportPdf;

    private FitnessOptions fitnessOptions;
    private SensorsClient sensorsClient;

    private OnDataPointListener stepListener;
    private OnDataPointListener heartRateListener;

    private int currentSteps = 0;
    private float currentCalories = 0f;
    private float currentHeartRate = 0f;

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

        btnExportPdf.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                generatePdfReport();
            } else {
                requestStoragePermission();
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

    private void generatePdfReport() {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(300, 400, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        paint.setTextSize(14f);
        paint.setFakeBoldText(true);
        canvas.drawText("PulseGuard Health Report", 50, 40, paint);

        paint.setTextSize(12f);
        paint.setFakeBoldText(false);
        canvas.drawText("Steps: " + currentSteps, 20, 80, paint);
        canvas.drawText("Calories: " + String.format("%.1f", currentCalories), 20, 110, paint);
        canvas.drawText("Heart Rate: " + String.format("%.1f bpm", currentHeartRate), 20, 140, paint);

        pdfDocument.finishPage(page);

        String fileName = "PulseGuard_Report_" + System.currentTimeMillis() + ".pdf";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PulseGuard");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);

                if (uri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream != null) {
                        pdfDocument.writeTo(outputStream);
                        outputStream.close();
                        Toast.makeText(this, "PDF saved to Documents/PulseGuard", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Unable to create file Uri", Toast.LENGTH_SHORT).show();
                }

            } else {
                // Fallback for Android 9 and below
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PulseGuard");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                pdfDocument.writeTo(fos);
                fos.close();
                Toast.makeText(this, "PDF saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving PDF: ", e);
            Toast.makeText(this, "Failed to save PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            pdfDocument.close();
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
