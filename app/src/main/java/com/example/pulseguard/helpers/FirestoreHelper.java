package com.example.pulseguard.helpers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Singleton helper class to manage Firestore operations related to user health data.
 */
public class FirestoreHelper {

    private static final String TAG = "FirestoreHelper";

    private static final String COLLECTION_USERS = "users";
    private static final String SUBCOLLECTION_HEALTH_DATA = "healthData";

    // Firestore document field names
    private static final String FIELD_STEPS = "steps";
    private static final String FIELD_CALORIES = "calories";
    private static final String FIELD_HEART_RATE = "heartRate";
    private static final String FIELD_TIMESTAMP = "timestamp";

    private static FirestoreHelper instance;
    private final FirebaseFirestore db;

    private FirestoreHelper() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Get singleton instance of FirestoreHelper.
     *
     * @return FirestoreHelper instance
     */
    public static synchronized FirestoreHelper getInstance() {
        if (instance == null) {
            instance = new FirestoreHelper();
        }
        return instance;
    }

    /**
     * Returns a new SimpleDateFormat instance to avoid locale caching issues.
     */
    private static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    }

    /**
     * Saves daily health data for the specified user.
     *
     * @param userId       Unique user ID
     * @param steps        Steps count
     * @param calories     Calories burned
     * @param avgHeartRate Average heart rate
     * @return Task<Void> to track success/failure
     */
    public Task<Void> saveDailyHealthData(@NonNull String userId,
                                          int steps,
                                          float calories,
                                          float avgHeartRate) {
        if (userId.trim().isEmpty()) {
            Log.e(TAG, "Invalid userId: cannot save health data.");
            return Tasks.forException(new IllegalArgumentException("User ID cannot be empty"));
        }

        String today = getDateFormat().format(new Date());
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_STEPS, steps);
        data.put(FIELD_CALORIES, calories);
        data.put(FIELD_HEART_RATE, avgHeartRate);
        data.put(FIELD_TIMESTAMP, System.currentTimeMillis());

        return db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_HEALTH_DATA)
                .document(today)
                .set(data)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Health data saved successfully for user: " + userId + " on " + today))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to save health data for user: " + userId + " on " + today, e));
    }

    /**
     * Retrieves daily health data for a user for the specified date.
     *
     * @param userId    Unique user ID
     * @param date      Date string in yyyy-MM-dd format
     * @param onSuccess Optional success listener
     * @param onFailure Optional failure listener
     * @return Task<DocumentSnapshot> for chaining or awaiting result
     */
    public Task<DocumentSnapshot> getDailyHealthData(@NonNull String userId,
                                                     @NonNull String date,
                                                     @Nullable OnSuccessListener<DocumentSnapshot> onSuccess,
                                                     @Nullable OnFailureListener onFailure) {
        if (userId.trim().isEmpty() || date.trim().isEmpty()) {
            Log.e(TAG, "Invalid userId or date: cannot retrieve health data.");
            return Tasks.forException(new IllegalArgumentException("User ID and date must not be empty"));
        }

        Task<DocumentSnapshot> task = db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_HEALTH_DATA)
                .document(date)
                .get();

        if (onSuccess != null) {
            task.addOnSuccessListener(onSuccess);
        }
        if (onFailure != null) {
            task.addOnFailureListener(onFailure);
        }

        return task;
    }
}
