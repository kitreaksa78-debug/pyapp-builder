package org.pyapp.demo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private LinearLayout chatContainer;
    private ScrollView scrollView;
    private EditText inputBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ១. កំណត់ផ្ទៃខាងក្រោយ App ជាពណ៌ខ្មៅ (Dark Mode ដូចរូបភាព)
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#121212")); // ពណ៌ខ្មៅទំនើប

        // ២. របារខាងលើ (Header)
        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(Color.parseColor("#1E1E1E"));
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("PocketPaw AI Agent");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);
        rootLayout.addView(header);

        // ៣. តំបន់បង្ហាញសារ Chat (ScrollView)
        scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f); // យកទំហំទំនេរទាំងអស់
        scrollView.setLayoutParams(scrollParams);

        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(dp(16), dp(16), dp(16), dp(16));
        scrollView.addView(chatContainer);
        rootLayout.addView(scrollView);

        // បង្ហាញសារស្វាគមន៍ពី AI ភ្លាមៗ
        addMessage("សួស្តី! ខ្ញុំគឺជា AI Agent របស់អ្នក។ តើអ្នកចង់សួរអ្វី?", false);

        // ៤. តំបន់វាយអក្សរ និងផ្ញើសារ (ខាងក្រោមគេ)
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        inputLayout.setBackgroundColor(Color.parseColor("#1E1E1E"));

        inputBox = new EditText(this);
        inputBox.setHint("វាយសារនៅទីនេះ...");
        inputBox.setHintTextColor(Color.parseColor("#888888"));
        inputBox.setTextColor(Color.WHITE);
        inputBox.setBackground(createRoundedBackground(Color.parseColor("#2C2C2C"), Color.parseColor("#3A3A3A")));
        inputBox.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputParams.setMargins(0, 0, dp(10), 0);
        inputLayout.addView(inputBox, inputParams);

        Button sendBtn = new Button(this);
        sendBtn.setText("ផ្ញើ");
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setAllCaps(false);
        sendBtn.setBackground(createRoundedBackground(Color.parseColor("#3B82F6"), Color.parseColor("#3B82F6")));
        inputLayout.addView(sendBtn);
        
        rootLayout.addView(inputLayout);
        setContentView(rootLayout);

        // ៥. ពេលចុចប៊ូតុងផ្ញើ
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = inputBox.getText().toString().trim();
                if (!text.isEmpty()) {
                    addMessage(text, true); // បង្ហាញសារអ្នកប្រើ
                    inputBox.setText("");   // លុបអក្សរចេញពីប្រអប់
                    
                    // ក្លែងធ្វើឱ្យ AI ឆ្លើយតប (ក្រោយ ១ វិនាទី)
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            addMessage("ខ្ញុំបានទទួលសាររបស់អ្នកហើយ។ (នេះជាការឆ្លើយតបសាកល្បង)", false);
                        }
                    }, 1000);
                }
            }
        });
    }

    // អនុគមន៍សម្រាប់បង្កើតប្រអប់សារ (Chat Bubbles)
    private void addMessage(String message, boolean isUser) {
        TextView messageText = new TextView(this);
        messageText.setText(message);
        messageText.setTextSize(16);
        messageText.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        
        if (isUser) {
            // សារអ្នកប្រើ (ពណ៌ខៀវ តម្រឹមស្តាំ)
            messageText.setTextColor(Color.WHITE);
            messageText.setBackground(createChatBubble(Color.parseColor("#2563EB"), 16, 16, 16, 4));
            params.gravity = Gravity.END;
            params.setMargins(dp(50), 0, 0, dp(16));
        } else {
            // សារពី AI (ពណ៌ប្រផេះ តម្រឹមឆ្វេង)
            messageText.setTextColor(Color.WHITE);
            messageText.setBackground(createChatBubble(Color.parseColor("#333333"), 16, 16, 4, 16));
            params.gravity = Gravity.START;
            params.setMargins(0, 0, dp(50), dp(16));
        }
        
        chatContainer.addView(messageText, params);
        
        // រមៀលទៅកាន់សារថ្មីបំផុត
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    // បង្កើតផ្ទៃខាងក្រោយសម្រាប់ប្រអប់ទូទៅ
    private GradientDrawable createRoundedBackground(int fillColor, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12));
        shape.setColor(fillColor);
        shape.setStroke(dp(1), strokeColor);
        return shape;
    }

    // បង្កើតផ្ទៃខាងក្រោយសម្រាប់ Chat Bubble (ជ្រុងមូលខុសៗគ្នា)
    private GradientDrawable createChatBubble(int color, int tl, int tr, int br, int bl) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        // កំណត់ជ្រុងមូល (Top-Left, Top-Right, Bottom-Right, Bottom-Left)
        shape.setCornerRadii(new float[]{
                dp(tl), dp(tl), 
                dp(tr), dp(tr), 
                dp(br), dp(br), 
                dp(bl), dp(bl)
        });
        return shape;
    }

    // បំប្លែង dp ទៅ Pixels
    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
