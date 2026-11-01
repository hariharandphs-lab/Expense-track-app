package com.smartexpense.android.ui.companion;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.smartexpense.android.R;

/**
 * Shorekeeper-inspired virtual companion.
 * Displays Shorekeeper in sitting (idle) and standing (greeting / walking / dragging) poses.
 * Supports gentle floating, periodic short pacing, drag-to-reposition without blocking UI,
 * and tap-to-chat greeting interactions.
 */
public class ShorekeeperCompanionView extends FrameLayout {

    public interface OnCompanionClickListener {
        void onCompanionClick();
    }

    private ImageView ivCompanion;
    private View vShadow;
    private TextView tvSpeechBubble;
    private FrameLayout layoutSpriteContainer;

    private ObjectAnimator floatAnimator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable paceRunnable;

    private boolean isPaused = false;
    private boolean isWalking = false;
    private boolean isDragging = false;
    private float touchStartX, touchStartY;
    private float viewStartX, viewStartY;
    private long touchDownTime;
    private int touchSlop;

    private OnCompanionClickListener clickListener;

    public ShorekeeperCompanionView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ShorekeeperCompanionView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ShorekeeperCompanionView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_shorekeeper_companion, this, true);
        setClipChildren(false);
        setClipToPadding(false);

        ivCompanion = findViewById(R.id.iv_companion);
        vShadow = findViewById(R.id.v_shadow);
        tvSpeechBubble = findViewById(R.id.tv_speech_bubble);
        layoutSpriteContainer = findViewById(R.id.layout_sprite_container);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Start in sitting pose
        setSittingPose();

        setupTouchListener();
        setupFloatingAnimation();
        setupPacingCycle();

        // Hide speech bubble after 4.5 seconds
        handler.postDelayed(() -> {
            if (tvSpeechBubble != null) {
                tvSpeechBubble.animate().alpha(0f).setDuration(600).start();
            }
        }, 4500);
    }

    public void setOnCompanionClickListener(OnCompanionClickListener listener) {
        this.clickListener = listener;
    }

    public void setSittingPose() {
        if (ivCompanion != null) {
            ivCompanion.setImageResource(R.drawable.shorekeeper_sitting);
        }
    }

    public void setStandingPose() {
        if (ivCompanion != null) {
            ivCompanion.setImageResource(R.drawable.shorekeeper_standing);
        }
    }

    /**
     * Gentle vertical float animation to make her feel alive in idle mode.
     */
    private void setupFloatingAnimation() {
        if (layoutSpriteContainer == null) return;
        floatAnimator = ObjectAnimator.ofFloat(layoutSpriteContainer, "translationY", 0f, -5f, 0f);
        floatAnimator.setDuration(1900);
        floatAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        floatAnimator.setRepeatCount(ValueAnimator.INFINITE);
        floatAnimator.setRepeatMode(ValueAnimator.REVERSE);
        floatAnimator.start();
    }

    /**
     * Periodic gentle movement: stands up, takes short steps, looks around, sits back down.
     */
    private void setupPacingCycle() {
        paceRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPaused || isDragging) {
                    handler.postDelayed(this, 12000);
                    return;
                }
                performPacingMovement();
                // Next movement in 14 to 20 seconds
                long nextDelay = 14000 + (long) (Math.random() * 6000);
                handler.postDelayed(this, nextDelay);
            }
        };
        handler.postDelayed(paceRunnable, 10000);
    }

    private void performPacingMovement() {
        if (isWalking || isDragging || ivCompanion == null) return;
        isWalking = true;

        // Switch to standing pose for movement
        setStandingPose();

        // Decide direction: walk left by ~20dp or right by ~20dp
        final float walkDistance = (Math.random() > 0.5f ? -22f : 22f);
        ivCompanion.setScaleX(walkDistance < 0 ? -1f : 1f); // face movement direction

        // Walk translation
        ivCompanion.animate()
                .translationX(walkDistance)
                .setDuration(900)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Pause for 2 seconds while standing
                        handler.postDelayed(() -> {
                            if (isDragging || isPaused) return;
                            // Walk back to center
                            ivCompanion.setScaleX(walkDistance < 0 ? 1f : -1f);
                            ivCompanion.animate()
                                    .translationX(0f)
                                    .setDuration(900)
                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            ivCompanion.setScaleX(1f);
                                            setSittingPose();
                                            isWalking = false;
                                        }
                                    }).start();
                        }, 2200);
                    }
                }).start();
    }

    /**
     * Touch listener supporting both tap-to-chat and smooth drag-to-reposition
     * so she never covers any button or content.
     */
    private void setupTouchListener() {
        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    viewStartX = getX();
                    viewStartY = getY();
                    touchDownTime = System.currentTimeMillis();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - touchStartX;
                    float dy = event.getRawY() - touchStartY;

                    if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isDragging = true;
                        setStandingPose(); // Stand up while being moved
                        showGreetingBubble("Moving... 🌸", 2000);
                    }

                    if (isDragging) {
                        float newX = viewStartX + dx;
                        float newY = viewStartY + dy;

                        // Clamp within parent bounds
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            float minX = 8f;
                            float maxX = parent.getWidth() - getWidth() - 8f;
                            float minY = 32f;
                            // Keep above bottom navigation (~90dp from bottom)
                            float maxY = parent.getHeight() - getHeight() - 88f;

                            newX = Math.max(minX, Math.min(newX, maxX));
                            newY = Math.max(minY, Math.min(newY, maxY));
                        }

                        setX(newX);
                        setY(newY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long duration = System.currentTimeMillis() - touchDownTime;
                    float totalDist = Math.abs(event.getRawX() - touchStartX) + Math.abs(event.getRawY() - touchStartY);

                    if (!isDragging && totalDist < touchSlop && duration < 400) {
                        // Quick tap detected -> Greeting animation & Chat
                        performTapGreeting();
                        performClick();
                    } else if (isDragging) {
                        // Drag completed -> Sit down comfortably
                        isDragging = false;
                        setSittingPose();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    setSittingPose();
                    return true;
            }
            return false;
        });
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /**
     * Joyful tap reaction: springs up to standing pose, shows greeting, and launches chat.
     */
    private void performTapGreeting() {
        setStandingPose();
        showGreetingBubble("Hi! How can I help? ✨", 3500);

        // Spring scale animation
        animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(160)
                .withEndAction(() -> {
                    animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(160)
                            .withEndAction(() -> {
                                // Settle back to sitting after a moment
                                handler.postDelayed(this::setSittingPose, 1800);
                            })
                            .start();
                }).start();

        if (clickListener != null) {
            clickListener.onCompanionClick();
        }
    }

    public void showGreetingBubble(String text, long durationMs) {
        if (tvSpeechBubble == null) return;
        tvSpeechBubble.setText(text);
        tvSpeechBubble.animate().cancel();
        tvSpeechBubble.setAlpha(1f);
        tvSpeechBubble.setVisibility(VISIBLE);
        handler.postDelayed(() -> {
            if (tvSpeechBubble != null) {
                tvSpeechBubble.animate().alpha(0f).setDuration(500).start();
            }
        }, durationMs);
    }

    /**
     * Pauses all animations and timers when app goes to background.
     */
    public void pause() {
        isPaused = true;
        if (floatAnimator != null && floatAnimator.isRunning()) {
            floatAnimator.pause();
        }
        if (paceRunnable != null) {
            handler.removeCallbacks(paceRunnable);
        }
        if (ivCompanion != null) {
            ivCompanion.animate().cancel();
            ivCompanion.setTranslationX(0f);
            ivCompanion.setScaleX(1f);
        }
        isWalking = false;
        setSittingPose();
    }

    /**
     * Resumes animations when app returns to foreground.
     */
    public void resume() {
        isPaused = false;
        if (floatAnimator != null) {
            if (floatAnimator.isPaused()) {
                floatAnimator.resume();
            } else if (!floatAnimator.isRunning()) {
                floatAnimator.start();
            }
        }
        if (paceRunnable != null) {
            handler.removeCallbacks(paceRunnable);
            handler.postDelayed(paceRunnable, 10000);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pause();
        if (floatAnimator != null) {
            floatAnimator.cancel();
        }
    }
}
