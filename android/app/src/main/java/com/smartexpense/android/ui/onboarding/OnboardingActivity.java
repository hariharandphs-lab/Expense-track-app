package com.smartexpense.android.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.smartexpense.android.R;
import com.smartexpense.android.databinding.ActivityOnboardingBinding;
import com.smartexpense.android.ui.auth.LoginActivity;

/**
 * Onboarding screen — 3-page ViewPager2 introducing key features before login.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;

    private static final int[] TITLES = {
            R.string.onboarding_title_1,
            R.string.onboarding_title_2,
            R.string.onboarding_title_3
    };

    private static final int[] DESCS = {
            R.string.onboarding_desc_1,
            R.string.onboarding_desc_2,
            R.string.onboarding_desc_3
    };

    private static final int[] IMAGES = {
            R.drawable.onboarding_sms,
            R.drawable.onboarding_ai,
            R.drawable.onboarding_chart
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        OnboardingAdapter adapter = new OnboardingAdapter();
        binding.viewPager.setAdapter(adapter);
        binding.dotsIndicator.attachTo(binding.viewPager);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                boolean isLast = position == TITLES.length - 1;
                binding.btnNext.setText(isLast ?
                        getString(R.string.get_started) : getString(R.string.next));
                binding.tvSkip.setVisibility(isLast ? View.GONE : View.VISIBLE);
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < TITLES.length - 1) {
                binding.viewPager.setCurrentItem(current + 1, true);
            } else {
                goToLogin();
            }
        });

        binding.tvSkip.setOnClickListener(v -> goToLogin());
    }

    private void goToLogin() {
        // Mark onboarding as seen
        getSharedPreferences("smart_expense_prefs", MODE_PRIVATE)
                .edit().putBoolean("onboarding_done", true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    // ─── Inner Adapter ───────────────────────────────────────────────────────
    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageVH> {

        @Override
        public PageVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new PageVH(view);
        }

        @Override
        public void onBindViewHolder(PageVH holder, int position) {
            holder.ivImage.setImageResource(IMAGES[position]);
            holder.tvTitle.setText(getString(TITLES[position]));
            holder.tvDesc.setText(getString(DESCS[position]));
        }

        @Override
        public int getItemCount() { return TITLES.length; }

        class PageVH extends RecyclerView.ViewHolder {
            ImageView ivImage;
            TextView tvTitle, tvDesc;

            PageVH(View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.iv_onboarding_image);
                tvTitle = itemView.findViewById(R.id.tv_onboarding_title);
                tvDesc = itemView.findViewById(R.id.tv_onboarding_desc);
            }
        }
    }
}
