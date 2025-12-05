// PostAdapter.java - USING FIRESTORE
package com.example.petcareapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
    private List<Post> posts;
    private Context context;
    private FirebaseFirestore db;
    private String currentUserId;

    public PostAdapter(List<Post> posts, Context context) {
        this.posts = posts;
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.postImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        holder.postImageView.setAdjustViewBounds(true);

        // Set basic info
        holder.usernameTextView.setText(post.getUsername());
        holder.captionTextView.setText(post.getCaption());
        holder.likeCountTextView.setText(String.valueOf(post.getLikeCount()));
        holder.commentCountTextView.setText(String.valueOf(post.getCommentCount()));
        holder.timeTextView.setText(getTimeAgo(post.getTimestamp()));

        // Load profile image
        if (post.getUserId() != null) {
            loadUserProfile(post.getUserId(), holder.profileImageView);
        } else {
            holder.profileImageView.setImageResource(R.drawable.ic_profile);
        }
        // Before loading image in PostAdapter:
        Runtime runtime = Runtime.getRuntime();
        long usedMem = runtime.totalMemory() - runtime.freeMemory();
        long maxMem = runtime.maxMemory();
        long availableMem = maxMem - usedMem;

        // Only load if we have enough memory (e.g., at least 50MB available)
        if (availableMem > 50 * 1024 * 1024) { // 50MB
            // Load image
        } else {
            // Skip image or load placeholder
            holder.postImageView.setImageResource(R.drawable.post_background);
        }

        // Load post image from Base64
        // Then when setting the image:
        if (post.isHasImage() && post.getImageBase64() != null && !post.getImageBase64().isEmpty()) {
            try {
                // Decode Base64 to Bitmap
                byte[] decodedString = android.util.Base64.decode(post.getImageBase64(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(
                        decodedString, 0, decodedString.length);

                holder.postImageView.setImageBitmap(decodedByte);
                holder.postImageView.setVisibility(View.VISIBLE);

                // Remove any fixed height constraints
                ViewGroup.LayoutParams params = holder.postImageView.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                holder.postImageView.setLayoutParams(params);

            } catch (Exception e) {
                e.printStackTrace();
                holder.postImageView.setVisibility(View.GONE);
            }
        } else {
            holder.postImageView.setVisibility(View.GONE);
        }

        // Check if current user liked the post
        boolean isLiked = post.getLikedBy() != null &&
                post.getLikedBy().containsKey(currentUserId);
        setLikeButton(holder.likeButton, isLiked);

        // Like button click listener
        holder.likeButton.setOnClickListener(v -> {
            toggleLike(post, holder);
        });

        // Comment button click listener
        holder.commentButton.setOnClickListener(v -> {
            openComments(post);
        });
    }
    // Add this helper method in PostAdapter:
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
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
    private void loadUserProfile(String userId, ImageView profileImageView) {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String profileImageUrl = documentSnapshot.getString("profileImageUrl");
                        if (profileImageUrl != null && !profileImageUrl.equals("default")) {
                            Picasso.get()
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile)
                                    .into(profileImageView);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Failed to load profile image, keep default
                });
    }

    private void toggleLike(Post post, PostViewHolder holder) {
        String postId = post.getPostId();
        boolean isLiked = post.getLikedBy() != null &&
                post.getLikedBy().containsKey(currentUserId);

        Map<String, Object> updates = new HashMap<>();

        if (isLiked) {
            // Unlike
            updates.put("likeCount", post.getLikeCount() - 1);
            updates.put("likedBy." + currentUserId, FieldValue.delete());
            setLikeButton(holder.likeButton, false);
            post.setLikeCount(post.getLikeCount() - 1);
            post.getLikedBy().remove(currentUserId);
        } else {
            // Like
            updates.put("likeCount", post.getLikeCount() + 1);
            updates.put("likedBy." + currentUserId, true);
            setLikeButton(holder.likeButton, true);
            post.setLikeCount(post.getLikeCount() + 1);
            if (post.getLikedBy() == null) {
                post.setLikedBy(new HashMap<>());
            }
            post.getLikedBy().put(currentUserId, true);
        }

        // Update in Firestore
        db.collection("posts").document(postId)
                .update(updates)
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Failed to update like: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });

        // Update UI
        holder.likeCountTextView.setText(String.valueOf(post.getLikeCount()));
    }

    private void setLikeButton(ImageView likeButton, boolean isLiked) {
        if (isLiked) {
            likeButton.setImageResource(R.drawable.like_filled);
            likeButton.setColorFilter(ContextCompat.getColor(context, R.color.red));
        } else {
            likeButton.setImageResource(R.drawable.like);
            likeButton.setColorFilter(ContextCompat.getColor(context, R.color.gray));
        }
    }

    private String getTimeAgo(Long timestamp) {
        if (timestamp == null) return "Just now";

        long time = timestamp;
        long now = System.currentTimeMillis();
        long diff = now - time;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d ago";
        if (hours > 0) return hours + "h ago";
        if (minutes > 0) return minutes + "m ago";
        return "Just now";
    }


    private void openComments(Post post) {
        Intent intent = new Intent(context, CommentsActivity.class);
        intent.putExtra("postId", post.getPostId());
        context.startActivity(intent);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public void updatePosts(List<Post> newPosts) {
        posts.clear();
        posts.addAll(newPosts);
        notifyDataSetChanged();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView usernameTextView;
        ImageView profileImageView;
        TextView captionTextView;
        ImageView postImageView;
        TextView likeCountTextView;
        TextView commentCountTextView;
        TextView timeTextView;
        ImageView likeButton;
        ImageView commentButton;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);

            profileImageView = itemView.findViewById(R.id.profile_image);
            usernameTextView = itemView.findViewById(R.id.username);
            captionTextView = itemView.findViewById(R.id.caption);
            postImageView = itemView.findViewById(R.id.post_image);
            likeButton = itemView.findViewById(R.id.like_button);
            commentButton = itemView.findViewById(R.id.comment_button);
            likeCountTextView = itemView.findViewById(R.id.like_count);
            commentCountTextView = itemView.findViewById(R.id.comment_count);
            timeTextView = itemView.findViewById(R.id.time);
        }
    }
}