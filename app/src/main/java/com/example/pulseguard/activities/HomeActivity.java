package com.example.pulseguard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pulseguard.R;
import com.example.pulseguard.helpers.FirestoreHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;
    private static final String TAG = "HomeActivity";

    private TextView tvWelcomeUser, tvSteps, tvHeartRate, tvCalories;
    private Button btnLogout;
    private FirebaseAuth mAuth;

    private final FitnessOptions fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();

        tvWelcomeUser = findViewById(R.id.tv_welcome_user);
        tvSteps = findViewById(R.id.tv_steps);
        tvHeartRate = findViewById(R.id.tv_heart_rate);
        tvCalories = findViewById(R.id.tv_calories);
        btnLogout = findViewById(R.id.btn_logout);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String welcomeText = String.format("Welcome, %s", currentUser.getEmail());
            tvWelcomeUser.setText(welcomeText);
        } else {
            tvWelcomeUser.setText("Welcome to PulseGuard");
        }

        btnLogout.setOnClickListener(view -> {
            mAuth.signOut();
            Intent intent = new Intent(HomeActivity.this, IntroActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Please sign in with Google first", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, IntroActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    account,
                    fitnessOptions);
        } else {
            readAndSaveFitnessData(account);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null) {
                    readAndSaveFitnessData(account);
                } else {
                    Toast.makeText(this, "Google account not found after permission", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Google Fit permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void readAndSaveFitnessData(GoogleSignInAccount account) {
        // Define the time range: start of day to now
        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        // Prepare request to aggregate step count and calories burned
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener(dataReadResponse -> {
                    int totalSteps = 0;
                    float totalCalories = 0f;

                    List<Bucket> buckets = dataReadResponse.getBuckets();
                    if (buckets != null && !buckets.isEmpty()) {
                        for (Bucket bucket : buckets) {
                            List<DataSet> dataSets = bucket.getDataSets();
                            if (dataSets != null) {
                                for (DataSet dataSet : dataSets) {
                                    for (DataPoint dp : dataSet.getDataPoints()) {
                                        for (Field field : dp.getDataType().getFields()) {
                                            String fieldName = field.getName();
                                            if (Field.FIELD_STEPS.equals(fieldName)) {
                                                totalSteps += dp.getValue(field).asInt();
                                            } else if (Field.FIELD_CALORIES.equals(fieldName)) {
                                                totalCalories += dp.getValue(field).asFloat();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Now read heart rate separately
                    readHeartRateData(account, startTime, endTime, totalSteps, totalCalories);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read steps/calories data", e);
                    tvSteps.setText("Steps: --");
                    tvCalories.setText("Calories Burned: --");
                    tvHeartRate.setText("Avg Heart Rate: --");
                    Toast.makeText(this, "Failed to read fitness data", Toast.LENGTH_SHORT).show();
                });
    }

    private void readHeartRateData(GoogleSignInAccount account, long startTime, long endTime,
                                   int totalSteps, float totalCalories) {

        DataReadRequest heartRateRequest = new DataReadRequest.Builder()
                .read(DataType.TYPE_HEART_RATE_BPM)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(heartRateRequest)
                .addOnSuccessListener(dataReadResponse -> {
                    float totalHeartRate = 0f;
                    int count = 0;

                    List<DataSet> dataSets = dataReadResponse.getDataSets();
                    if (dataSets != null && !dataSets.isEmpty()) {
                        for (DataSet dataSet : dataSets) {
                            for (DataPoint dp : dataSet.getDataPoints()) {
                                for (Field field : dp.getDataType().getFields()) {
                                    totalHeartRate += dp.getValue(field).asFloat();
                                    count++;
                                }
                            }
                        }
                    }

                    float avgHeartRate = count > 0 ? totalHeartRate / count : 0f;

                    // Update UI
                    tvSteps.setText(String.format("Steps: %d", totalSteps));
                    tvCalories.setText(String.format("Calories Burned: %.2f kcal", totalCalories));
                    if (avgHeartRate > 0) {
                        tvHeartRate.setText(String.format("Avg Heart Rate: %.1f bpm", avgHeartRate));
                    } else {
                        tvHeartRate.setText("Avg Heart Rate: --");
                    }

                    // Save data to Firestore
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null) {
                        FirestoreHelper.getInstance()
                                .saveDailyHealthData(currentUser.getUid(), totalSteps, totalCalories, avgHeartRate)
                                .addOnSuccessListener(aVoid -> Log.i(TAG, "Health data saved to Firestore"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to save health data to Firestore", e));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read heart rate data", e);
                    tvHeartRate.setText("Avg Heart Rate: --");
                    Toast.makeText(this, "Failed to read heart rate data", Toast.LENGTH_SHORT).show();
                });
    }
}