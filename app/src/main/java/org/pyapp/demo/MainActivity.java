package org.pyapp.demo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ១. កំណត់ផ្ទៃខាងក្រោយអេក្រង់ទាំងមូល (ពណ៌សស្រាល ទំនើប)
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F3F4F6")); 
        scrollView.setFillViewport(true);

        // ២. បង្កើត Layout សំខាន់ (រៀបចំពីលើចុះក្រោម)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(60), dp(24), dp(24));
        scrollView.addView(root);

        // ៣. ចំណងជើង App (Title)
        TextView title = new TextView(this);
        title.setText("Premium App");
        title.setTextSize(30);
        title.setTextColor(Color.parseColor("#111827"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // អក្សរពន្យល់ខាងក្រោមចំណងជើង
        TextView subtitle = new TextView(this);
        subtitle.setText("សូមស្វាគមន៍មកកាន់កម្មវិធីរបស់អ្នក");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.parseColor("#6B7280"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(40));
        root.addView(subtitle);

        // ៤. ប្រអប់សម្រាប់ដាក់ Form (ធ្វើឱ្យវាមើលទៅដូច Card ស)
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(createRoundedBackground(Color.WHITE, Color.parseColor("#E5E7EB")));
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(30));
        root.addView(card, cardParams);

        // អក្សរប្រាប់នៅក្នុង Form
        TextView label = new TextView(this);
        label.setText("ឈ្មោះអ្នកប្រើប្រាស់");
        label.setTextSize(14);
        label.setTextColor(Color.parseColor("#374151"));
        label.setPadding(0, 0, 0, dp(8));
        card.addView(label);

        // ប្រអប់វាយអក្សរ (EditText)
        EditText input = new EditText(this);
        input.setHint("បញ្ចូលឈ្មោះនៅទីនេះ...");
        input.setTextSize(16);
        input.setPadding(dp(16), dp(16), dp(16), dp(16));
        input.setBackground(createRoundedBackground(Color.parseColor("#F9FAFB"), Color.parseColor("#D1D5DB")));
        card.addView(input);

        // ៥. ប៊ូតុងទី១ (Primary Button - ពណ៌ខៀវ Gradient)
        Button btnPrimary = new Button(this);
        btnPrimary.setText("រក្សាទុកទិន្នន័យ");
        btnPrimary.setTextSize(16);
        btnPrimary.setTextColor(Color.WHITE);
        btnPrimary.setAllCaps(false);
        // បង្កើតពណ៌ Gradient ស្អាតៗ
        btnPrimary.setBackground(createGradientBackground(
                Color.parseColor("#3B82F6"), Color.parseColor("#8B5CF6")));
        
        LinearLayout.LayoutParams btnParams1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55));
        btnParams1.setMargins(0, 0, 0, dp(16));
        root.addView(btnPrimary, btnParams1);

        // ៦. ប៊ូតុងទី២ (Secondary Button - ពណ៌ស)
        Button btnSecondary = new Button(this);
        btnSecondary.setText("បោះបង់");
        btnSecondary.setTextSize(16);
        btnSecondary.setTextColor(Color.parseColor("#374151"));
        btnSecondary.setAllCaps(false);
        btnSecondary.setBackground(createRoundedBackground(Color.WHITE, Color.parseColor("#D1D5DB")));
        
        LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55));
        root.addView(btnSecondary, btnParams2);

        // ៧. អក្សរបង្ហាញស្ថានភាព (Status Text) នៅខាងក្រោមគេ
        TextView statusText = new TextView(this);
        statusText.setText("ស្ថានភាព៖ រង់ចាំការបញ្ជា...");
        statusText.setTextSize(14);
        statusText.setTextColor(Color.parseColor("#10B981")); // ពណ៌បៃតង
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(30), 0, 0);
        root.addView(statusText);

        // ៨. ព្រឹត្តិការណ៍ពេលចុចប៊ូតុង
        btnPrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userName = input.getText().toString().trim();
                if (userName.isEmpty()) {
                    statusText.setText("ស្ថានភាព៖ សូមបញ្ចូលឈ្មោះជាមុនសិន!");
                    statusText.setTextColor(Color.parseColor("#EF4444")); // ក្រហម
                    Toast.makeText(MainActivity.this, "សូមបំពេញព័ត៌មាន!", Toast.LENGTH_SHORT).show();
                } else {
                    statusText.setText("ស្ថានភាព៖ បានរក្សាទុកឈ្មោះ " + userName + " ដោយជោគជ័យ!");
                    statusText.setTextColor(Color.parseColor("#10B981")); // បៃតង
                    Toast.makeText(MainActivity.this, "ជោគជ័យ!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSecondary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
                statusText.setText("ស្ថានភាព៖ បានបោះបង់");
                statusText.setTextColor(Color.parseColor("#6B7280")); // ប្រផេះ
            }
        });

        // បង្ហាញ UI ទាំងអស់ចូលក្នុងអេក្រង់
        setContentView(scrollView);
    }

    // ==========================================
    // អនុគមន៍ជំនួយសម្រាប់ការរចនា (UI Helpers)
    // ==========================================

    // បង្កើតផ្ទៃខាងក្រោយជ្រុងមូល (សម្រាប់ប្រអប់ និងប៊ូតុង)
    private GradientDrawable createRoundedBackground(int fillColor, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12)); // ជ្រុងមូល 12dp
        shape.setColor(fillColor); // ពណ៌ខាងក្នុង
        shape.setStroke(dp(1), strokeColor); // ពណ៌ស៊ុមព័ទ្ធជុំវិញ
        return shape;
    }

    // បង្កើតផ្ទៃខាងក្រោយពណ៌ចម្រុះ (Gradient) សម្រាប់ប៊ូតុង
    private GradientDrawable createGradientBackground(int startColor, int endColor) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, // ឆ្វេងទៅស្តាំ
                new int[] {startColor, endColor});
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12));
        return shape;
    }

    // បំប្លែង dp ទៅជា Pixels (ដើម្បីឱ្យទំហំត្រូវគ្នាលើគ្រប់ទូរស័ព្ទ)
    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
