package com.example.petcareapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CreatePostActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_STORAGE = 2;

    private EditText captionEditText;
    private Button uploadButton;
    private ImageView postImageView;
    private MaterialCardView imageCard;
    private MaterialCardView addPhotoCard;
    private ImageView removeImageButton;
    private TextView usernameText;
    private ImageView backButton;
    private ProgressDialog progressDialog;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private Uri selectedImageUri;
    private String imageBase64;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        // Add slide animation
        overridePendingTransition(R.anim.slide_in_up, 0);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialize Views
        captionEditText = findViewById(R.id.caption_edittext);
        uploadButton = findViewById(R.id.upload_button);
        postImageView = findViewById(R.id.post_image);
        imageCard = findViewById(R.id.image_card);
        addPhotoCard = findViewById(R.id.add_photo_card);
        removeImageButton = findViewById(R.id.remove_image_button);
        usernameText = findViewById(R.id.username_text);
        backButton = findViewById(R.id.back_button);

        // Set current user's name
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.getDisplayName() != null) {
            usernameText.setText(currentUser.getDisplayName());
        }

        // Initialize Progress Dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Posting...");
        progressDialog.setCancelable(false);

        // Back button
        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, R.anim.slide_out_down);
        });

        // Add photo button
        addPhotoCard.setOnClickListener(v -> checkPermissionAndPickImage());

        // Remove image button
        removeImageButton.setOnClickListener(v -> {
            selectedImageUri = null;
            imageBase64 = null;
            imageCard.setVisibility(View.GONE);
        });

        // Upload button
        uploadButton.setOnClickListener(v -> uploadPost());
    }

    private void checkPermissionAndPickImage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        PERMISSION_REQUEST_STORAGE);
            } else {
                openImagePicker();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_STORAGE);
            } else {
                openImagePicker();
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select a Photo"), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            Toast.makeText(this, "Permission denied. Cannot select images.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                // Show the image
                postImageView.setImageBitmap(bitmap);
                imageCard.setVisibility(View.VISIBLE);

                inputStream.close();

                // Convert to Base64 in background
                new Thread(() -> {
                    try {
                        InputStream is = getContentResolver().openInputStream(selectedImageUri);
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                        imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                        is.close();

                        runOnUiThread(() -> Toast.makeText(CreatePostActivity.this,
                                "Image ready to upload", Toast.LENGTH_SHORT).show());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadPost() {
        String caption = captionEditText.getText().toString().trim();

        if (caption.isEmpty()) {
            Toast.makeText(this, "Please enter a caption", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login to create posts", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.show();

        // Create post data
        Map<String, Object> postData = new HashMap<>();
        postData.put("userId", currentUser.getUid());
        postData.put("username", currentUser.getDisplayName() != null ?
                currentUser.getDisplayName() : "Anonymous");
        postData.put("caption", caption);
        postData.put("timestamp", System.currentTimeMillis());
        postData.put("likeCount", 0);
        postData.put("commentCount", 0);

        if (imageBase64 != null) {
            postData.put("imageBase64", imageBase64);
            postData.put("hasImage", true);
        } else {
            postData.put("hasImage", false);
        }

        postData.put("likedBy", new HashMap<String, Boolean>());

        // Save to Firestore
        db.collection("posts")
                .add(postData)
                .addOnSuccessListener(documentReference -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show();

                    // Update with post ID
                    documentReference.update("postId", documentReference.getId());

                    Intent intent = new Intent(CreatePostActivity.this, HomeActivity.class);
                    startActivity(intent);
                    overridePendingTransition(0, R.anim.slide_out_down);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to create post: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

}