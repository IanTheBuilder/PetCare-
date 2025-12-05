package com.example.petcareapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welcome);
        // Delay 2 seconds before going to Login screen
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
                startActivity(intent);
                finish(); // Prevent back button returning to splash
            }
        }, 2000); // 2000 = 2 seconds
    }



   /* TO BE USED LATAER
    @Override
    protected void onStart(){
        super.onStart();

        if(FirebaseAuth.getInstance().getCurrentUser() != null){
            startActivity(new Intent(WelcomeActivity.this, HomeActivity.class));
            finish();
        }
    }
    */
}