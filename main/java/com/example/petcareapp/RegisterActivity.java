package com.example.petcareapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    FirebaseAuth auth;
    FirebaseFirestore db;
    TextView alreadyHaveAccount;
    EditText inputUsername, inputEmail, inputPassword, inputConfirmPassword;
    Button btnRegister;
    ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        alreadyHaveAccount = findViewById(R.id.alreadyHaveAccount);
        inputUsername = findViewById(R.id.inputUsername);
        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        inputConfirmPassword = findViewById(R.id.inputConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        pd = new ProgressDialog(this);

        alreadyHaveAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = inputUsername.getText().toString().trim();
                String email = inputEmail.getText().toString().trim();
                String password = inputPassword.getText().toString();
                String confirmPassword = inputConfirmPassword.getText().toString();

                if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, "You must enter all fields", Toast.LENGTH_SHORT).show();
                } else if (password.length() < 6) {
                    Toast.makeText(RegisterActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                } else if (!password.equals(confirmPassword)) {
                    Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                } else {
                    registerUser(username, email, password);
                }
            }
        });
    }

    private void registerUser(final String username, final String email, String password) {
        pd.setMessage("Registering User...");
        pd.show();

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(RegisterActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Authentication successful, now create user document in Firestore
                            FirebaseUser currentUser = auth.getCurrentUser();

                            if (currentUser != null) {
                                Map<String, Object> user = new HashMap<>();
                                user.put("username", username);
                                user.put("email", email);
                                user.put("userId", currentUser.getUid());
                                user.put("bio", "");
                                user.put("profileImageUrl", "default");
                                user.put("createdAt", System.currentTimeMillis());
                                user.put("postCount", 0);

                                // Save to Firestore
                                db.collection("users")
                                        .document(currentUser.getUid())
                                        .set(user)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> dbTask) {
                                                pd.dismiss();

                                                if (dbTask.isSuccessful()) {
                                                    // Update Firebase User profile with display name
                                                    com.google.firebase.auth.UserProfileChangeRequest profileUpdates =
                                                            new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                                    .setDisplayName(username)
                                                                    .build();

                                                    currentUser.updateProfile(profileUpdates)
                                                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                                @Override
                                                                public void onComplete(@NonNull Task<Void> profileTask) {
                                                                    // Registration successful
                                                                    Toast.makeText(RegisterActivity.this,
                                                                            "Registration successful!", Toast.LENGTH_SHORT).show();

                                                                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                                                    startActivity(intent);
                                                                    finish();
                                                                }
                                                            });
                                                } else {
                                                    // Firestore write failed
                                                    Toast.makeText(RegisterActivity.this,
                                                            "Database Error: " + dbTask.getException().getMessage(),
                                                            Toast.LENGTH_LONG).show();
                                                }
                                            }
                                        });
                            }
                        } else {
                            // Authentication failed
                            pd.dismiss();
                            Toast.makeText(RegisterActivity.this,
                                    "Authentication failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}