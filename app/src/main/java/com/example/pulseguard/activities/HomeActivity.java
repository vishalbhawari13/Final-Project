package com.example.pulseguard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pulseguard.R;
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
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;

    private TextView tvWelcomeUser, tvSteps, tvHeartRate, tvCalories;
    private Button btnLogout;

    private FirebaseAuth mAuth;

    FitnessOptions fitnessOptions = FitnessOptions.builder()
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
            String welcomeText = "Welcome, " + currentUser.getEmail();
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

        // Check Google Fit permissions
        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions);
        } else {
            readFitnessData();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                readFitnessData();
            } else {
                Toast.makeText(this, "Google Fit permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void readFitnessData() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Google account not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        readStepsData(account, startTime, endTime);
        readHeartRateData(account, startTime, endTime);
        readCaloriesData(account, startTime, endTime);
    }

    private void readStepsData(GoogleSignInAccount account, long startTime, long endTime) {
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener(dataReadResponse -> {
                    int totalSteps = 0;
                    for (Bucket bucket : dataReadResponse.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dp : dataSet.getDataPoints()) {
                                for (Field field : dp.getDataType().getFields()) {
                                    totalSteps += dp.getValue(field).asInt();
                                }
                            }
                        }
                    }
                    tvSteps.setText("Steps: " + totalSteps);
                    Log.i("GoogleFit", "Steps today: " + totalSteps);
                })
                .addOnFailureListener(e -> {
                    Log.e("GoogleFit", "Failed to read steps data", e);
                    tvSteps.setText("Steps: --");
                });
    }

    private void readHeartRateData(GoogleSignInAccount account, long startTime, long endTime) {
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .read(DataType.TYPE_HEART_RATE_BPM)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener(dataReadResponse -> {
                    float avgHeartRate = 0;
                    int count = 0;

                    for (DataSet dataSet : dataReadResponse.getDataSets()) {
                        for (DataPoint dp : dataSet.getDataPoints()) {
                            for (Field field : dp.getDataType().getFields()) {
                                avgHeartRate += dp.getValue(field).asFloat();
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        avgHeartRate = avgHeartRate / count;
                        tvHeartRate.setText(String.format("Avg Heart Rate: %.1f bpm", avgHeartRate));
                        Log.i("GoogleFit", "Avg Heart Rate today: " + avgHeartRate);
                    } else {
                        tvHeartRate.setText("Avg Heart Rate: --");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("GoogleFit", "Failed to read heart rate data", e);
                    tvHeartRate.setText("Avg Heart Rate: --");
                });
    }

    private void readCaloriesData(GoogleSignInAccount account, long startTime, long endTime) {
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener(dataReadResponse -> {
                    float totalCalories = 0;
                    for (Bucket bucket : dataReadResponse.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dp : dataSet.getDataPoints()) {
                                for (Field field : dp.getDataType().getFields()) {
                                    totalCalories += dp.getValue(field).asFloat();
                                }
                            }
                        }
                    }
                    tvCalories.setText(String.format("Calories Burned: %.2f kcal", totalCalories));
                    Log.i("GoogleFit", "Calories burned today: " + totalCalories);
                })
                .addOnFailureListener(e -> {
                    Log.e("GoogleFit", "Failed to read calories data", e);
                    tvCalories.setText("Calories Burned: --");
                });
    }
}
