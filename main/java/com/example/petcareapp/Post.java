package com.example.petcareapp;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class Post {
    private String postId;
    private String userId;
    private String username;
    private String caption;
    private String imageBase64;
    private String userProfileImage;

    private Long timestamp; // Use Long instead of Date

    private int likeCount = 0;
    private int commentCount = 0;
    private boolean hasImage = false;
    private Map<String, Boolean> likedBy = new HashMap<>();

    // REQUIRED: Empty constructor
    public Post() {}

    // Constructor
    public Post(String userId, String username, String caption) {
        this.userId = userId;
        this.username = username;
        this.caption = caption;
        this.likeCount = 0;
        this.commentCount = 0;
        this.hasImage = false;
        this.likedBy = new HashMap<>();
    }

    // Getters and setters

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
        this.hasImage = imageBase64 != null && !imageBase64.isEmpty();
    }

    public String getUserProfileImage() { return userProfileImage; }
    public void setUserProfileImage(String userProfileImage) {
        this.userProfileImage = userProfileImage;
    }

    // IMPORTANT: Let Firestore handle the timestamp with @ServerTimestamp
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public boolean isHasImage() { return hasImage; }
    public void setHasImage(boolean hasImage) { this.hasImage = hasImage; }

    public Map<String, Boolean> getLikedBy() { return likedBy; }
    public void setLikedBy(Map<String, Boolean> likedBy) { this.likedBy = likedBy; }
    @Exclude
    public Date getDate() {
        return timestamp != null ? new Date(timestamp) : null;
    }
}