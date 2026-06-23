package com.openusage.app;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.openusage.app.PermissionScreens.PermissionParentActivity;

public class UsagePermissionActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener
{
    private ViewPager carousel;
    private View[] indicators;
    private Button usageperm;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_permission);

        // Set up the image carousel.
        carousel = findViewById(R.id.up_carousel);
        carousel.setAdapter(new UsageInstructionsAdapter());

        // Set up the carousel indicators.
        indicators = new View[] {
                findViewById(R.id.up_indicator_1),
                findViewById(R.id.up_indicator_2),
                findViewById(R.id.up_indicator_3)
        };
        carousel.addOnPageChangeListener(this);

        // Wire the usage permission button.
        usageperm = (Button) findViewById(R.id.up_usage_permission);
        usageperm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivity(intent);
            }
        });

        // Set up the "What is this?" text.
        TextView whatis = findViewById(R.id.up_whatis);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(whatis.getText());
        ssb.setSpan(new URLSpan("#"), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        whatis.setText(ssb, TextView.BufferType.SPANNABLE);
        whatis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(UsagePermissionActivity.this)
                    .setTitle(R.string.up_whatis_title)
                    .setMessage(R.string.up_whatis_text)
                    .setPositiveButton("OK", null);

                // create and show the alert dialog.
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // When the app gets focus back, check if we have usage permission now. If so, continue.
        if (appHasUsageAccess(this))
        {
//            Intent intent = new Intent(this, CapturePermissionActivity.class);
            Intent intent = new Intent(this, PermissionParentActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onPageSelected(int page)
    {
        // Illuminate the corresponding carousel indicator.
        for (int i = 0; i < indicators.length; i++) {
            indicators[i].setEnabled(i == page);
        }

        // If on the final page, enable the "Open Settings" button.
        if (page == indicators.length - 1) {
            usageperm.setEnabled(true);
        }
    }

    public static boolean appHasUsageAccess(Context context)
    {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);
            return (mode == AppOpsManager.MODE_ALLOWED);

        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    /**
     * Adapter for the image carousel that displays instructions on giving the usage permission.
     */
    private class UsageInstructionsAdapter extends PagerAdapter
    {
        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position)
        {
            // Create an image view and layout params.
            ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ImageView img = new ImageView(UsagePermissionActivity.this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setAdjustViewBounds(true);

            // Decide a drawable to use depending on which page of the carousel this is.
            int drawableId = 0;

            if (position == 0) drawableId = R.drawable.up_instruction_1;
            else if (position == 1) drawableId = R.drawable.up_instruction_2;
            else if (position == 2) drawableId = R.drawable.up_instruction_3;

            // Load the image.
            img.setImageDrawable(getResources().getDrawable(drawableId, getTheme()));

            // Put the image in the container.
            container.addView(img, layoutParams);
            return img;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            return view == o;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        Utils.createOptionsMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Utils.optionsItemSelected(this, item);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) { }

    @Override
    public void onPageScrollStateChanged(int i) { }
}
