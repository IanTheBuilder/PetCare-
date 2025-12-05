package com.example.petcareapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class SimpleCreatePostActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_STORAGE = 2;
    private static final int MAX_IMAGE_WIDTH = 1200; // Maximum width for resized image
    private static final int MAX_IMAGE_HEIGHT = 1600; // Maximum height for resized image
    private static final int COMPRESSION_QUALITY = 75; // Compression quality (0-100)

    private EditText captionEditText;
    private Button uploadButton;
    private Button selectFileButton;
    private ImageView selectedImageView;
    private ProgressDialog progressDialog;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private Uri selectedImageUri;
    private String imageBase64;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialize Views
        captionEditText = findViewById(R.id.caption_edittext);
        uploadButton = findViewById(R.id.upload_button);
        selectFileButton = findViewById(R.id.select_file_button);
        selectedImageView = findViewById(R.id.post_image);

        // Set ImageView to show full image (not cropped)
        selectedImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        selectedImageView.setAdjustViewBounds(true);

        // Initialize Progress Dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Uploading...");
        progressDialog.setCancelable(false);

        // Set click listener to the SELECT FILE BUTTON
        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndPickImage();
            }
        });

        // Also make the ImageView clickable (for re-selecting image)
        selectedImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndPickImage();
            }
        });

        // Upload button listener
        uploadButton.setOnClickListener(v -> uploadPost());
    }

    private void checkPermissionAndPickImage() {
        // For Android 13+ (API 33+)
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
            // For older versions
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
                // Load a preview image (low quality for preview)
                Bitmap previewBitmap = decodeSampledBitmapFromUri(selectedImageUri, 400, 400);
                if (previewBitmap != null) {
                    selectedImageView.setImageBitmap(previewBitmap);
                    selectedImageView.setVisibility(View.VISIBLE);
                }

                // Process full image in background
                new Thread(() -> {
                    try {
                        // Load and resize image to fit within maximum dimensions
                        Bitmap processedBitmap = decodeAndResizeBitmap(selectedImageUri, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
                        if (processedBitmap == null) {
                            runOnUiThread(() -> Toast.makeText(SimpleCreatePostActivity.this,
                                    "Failed to process image", Toast.LENGTH_SHORT).show());
                            return;
                        }

                        // Convert to Base64
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        processedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, baos);
                        imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                        // Clean up
                        processedBitmap.recycle();

                        runOnUiThread(() -> Toast.makeText(SimpleCreatePostActivity.this,
                                "Image ready to upload", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(SimpleCreatePostActivity.this,
                                "Failed to process image", Toast.LENGTH_SHORT).show());
                    }
                }).start();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Decode and resize bitmap efficiently without loading full image into memory
     */
    private Bitmap decodeAndResizeBitmap(Uri uri, int maxWidth, int maxHeight) {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            InputStream input = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            // Calculate inSampleSize (power of 2)
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Use less memory
            input = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            input.close();

            // Fix orientation if needed
            bitmap = fixImageOrientation(uri, bitmap);

            // Further resize to exact dimensions if needed (maintains aspect ratio)
            bitmap = resizeBitmapToFit(bitmap, maxWidth, maxHeight);

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Resize bitmap to fit within maximum dimensions while maintaining aspect ratio
     */
    private Bitmap resizeBitmapToFit(Bitmap image, int maxWidth, int maxHeight) {
        if (image == null) return null;

        int width = image.getWidth();
        int height = image.getHeight();

        // Check if image already fits within limits
        if (width <= maxWidth && height <= maxHeight) {
            return image;
        }

        // Calculate new dimensions while maintaining aspect ratio
        float widthRatio = (float) maxWidth / (float) width;
        float heightRatio = (float) maxHeight / (float) height;
        float ratio = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);

        return Bitmap.createScaledBitmap(image, newWidth, newHeight, true);
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Decode sampled bitmap for preview (small size)
     */
    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            InputStream input = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            input = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            input.close();

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Fix image orientation based on EXIF data
     */
    private Bitmap fixImageOrientation(Uri uri, Bitmap bitmap) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(input);
            input.close();

            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap; // No rotation needed
            }

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap; // Return original if can't fix orientation
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

        if (imageBase64 != null && !imageBase64.isEmpty()) {
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

                    Intent intent = new Intent(SimpleCreatePostActivity.this, HomeActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed to create post: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}