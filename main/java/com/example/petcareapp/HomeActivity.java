package com.example.petcareapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private ImageView chatButton;
    private ImageView uploadButton;

    private PostAdapter postAdapter;
    private List<Post> postList;
    private LinearLayoutManager layoutManager;

    // Variables to save scroll position
    private int savedScrollPosition = 0;
    private boolean isFirstLoad = true;
    private boolean isLoading = false;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Log.d(TAG, "HomeActivity started");

        initFirebase();
        initViews();
        setupRecyclerView();
        setupSwipeRefreshLayout();
        loadPosts();
        setupClickListeners();
    }

    private void initFirebase() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        recyclerView = findViewById(R.id.recycler_view_posts);
        chatButton = findViewById(R.id.chat_button);
        uploadButton = findViewById(R.id.upload_button);
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        postList = new ArrayList<>();
        postAdapter = new PostAdapter(postList, this);
        recyclerView.setAdapter(postAdapter);

        // Save scroll position when user scrolls
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // Save the first visible item position
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem != RecyclerView.NO_POSITION) {
                    savedScrollPosition = firstVisibleItem;
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // Disable swipe refresh when scrolling
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    swipeRefreshLayout.setEnabled(false);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    swipeRefreshLayout.setEnabled(true);
                }
            }
        });
    }

    private void setupSwipeRefreshLayout() {
        // Set the refresh listener
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (!isLoading) {
                saveScrollPosition();
                loadPosts();
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // Configure the refresh colors
        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
        );

        // Set the progress background color
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(android.R.color.white);

        // Set initial refreshing state for first load
        if (isFirstLoad) {
            swipeRefreshLayout.setRefreshing(true);
        }
    }

    private void loadPosts() {
        if (isLoading) return;

        Log.d(TAG, "Loading posts...");
        isLoading = true;

        db.collection("posts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnCompleteListener(task -> {
                    isLoading = false;

                    // Hide refresh indicator
                    swipeRefreshLayout.setRefreshing(false);

                    if (task.isSuccessful()) {
                        List<Post> newPosts = new ArrayList<>();
                        int loadedCount = 0;

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Post post = document.toObject(Post.class);
                                post.setPostId(document.getId());
                                newPosts.add(post);
                                loadedCount++;
                            } catch (Exception e) {
                                Log.e(TAG, "Error converting document: " + e.getMessage());
                            }
                        }

                        // Update the post list
                        updatePostsList(newPosts);

                        // Restore scroll position if not first load
                        if (!isFirstLoad) {
                            restoreScrollPosition();
                        } else {
                            isFirstLoad = false;
                        }

                        // Show feedback
                        if (loadedCount > 0) {
                            Toast.makeText(HomeActivity.this,
                                    loadedCount + " posts loaded",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(HomeActivity.this,
                                    "No posts yet. Create the first one!",
                                    Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        Log.e(TAG, "Error loading posts: " + task.getException());
                        Toast.makeText(HomeActivity.this,
                                "Failed to load posts: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    isLoading = false;
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(HomeActivity.this,
                            "Network error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updatePostsList(List<Post> newPosts) {
        // Clear existing posts and add new ones
        postList.clear();
        postList.addAll(newPosts);
        postAdapter.notifyDataSetChanged();

        // If it's the first load and we have posts, scroll to top
        if (isFirstLoad && !postList.isEmpty()) {
            recyclerView.post(() -> {
                if (layoutManager != null) {
                    layoutManager.scrollToPosition(0);
                }
            });
        }
    }

    private void saveScrollPosition() {
        if (layoutManager != null) {
            int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
            if (firstVisibleItem != RecyclerView.NO_POSITION) {
                savedScrollPosition = firstVisibleItem;
                Log.d(TAG, "Saved scroll position: " + savedScrollPosition);
            }
        }
    }

    private void restoreScrollPosition() {
        if (layoutManager != null && savedScrollPosition >= 0) {
            // Use postDelayed to ensure layout is complete
            new Handler().postDelayed(() -> {
                if (savedScrollPosition < postList.size()) {
                    layoutManager.scrollToPositionWithOffset(savedScrollPosition, 0);
                    Log.d(TAG, "Restored scroll position to: " + savedScrollPosition);
                } else if (!postList.isEmpty()) {
                    // If saved position is out of bounds, scroll to last position
                    layoutManager.scrollToPosition(postList.size() - 1);
                }
            }, 100); // Small delay to ensure layout is ready
        }
    }

    private void setupClickListeners() {
        // Chat button
        chatButton.setOnClickListener(v -> openChatActivity());

        // Upload button
        uploadButton.setOnClickListener(v -> openCreatePostActivity());

        // Optional: Add click effect
        chatButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        });

        uploadButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }

    private void openChatActivity() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login to use chat", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
        startActivity(intent);
    }

    private void openCreatePostActivity() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login to create posts", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(HomeActivity.this, SimpleCreatePostActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auto-refresh when coming back from CreatePostActivity
        if (!isFirstLoad) {
            new Handler().postDelayed(() -> {
                if (!isLoading) {
                    saveScrollPosition();
                    loadPosts();
                }
            }, 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save scroll position when leaving
        saveScrollPosition();
    }
}