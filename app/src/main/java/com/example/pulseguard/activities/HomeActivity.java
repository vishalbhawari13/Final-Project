package com.example.pulseguard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pulseguard.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {

    private TextView tvWelcomeUser;
    private Button btnLogout;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();

        tvWelcomeUser = findViewById(R.id.tv_welcome_user);
        btnLogout = findViewById(R.id.btn_logout);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String welcomeText = "Welcome, " + currentUser.getEmail();
            tvWelcomeUser.setText(welcomeText);
        } else {
            tvWelcomeUser.setText("Welcome to PulseGuard");
        }

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAuth.signOut();
                Intent intent = new Intent(HomeActivity.this, IntroActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}
