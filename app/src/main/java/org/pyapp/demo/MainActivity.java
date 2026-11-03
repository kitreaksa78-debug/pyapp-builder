package org.pyapp.demo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONObject;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {

    LinearLayout messages;
    EditText input;
    Button send;
    String API_URL = "https://YOUR-WEBSITE.onrender.com/api/chat";

    int white = Color.WHITE;
    int purple = Color.rgb(139,92,246);

    GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().setStatusBarColor(Color.rgb(9,13,22));
        getWindow().setNavigationBarColor(Color.rgb(9,13,22));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(18,18,18,12);
        root.setBackgroundColor(Color.rgb(9,13,22));

        // HEADER
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(15,15,15,15);
        header.setBackground(bg(Color.rgb(20,29,43),20));

        TextView logo = text("✦",30,purple);
        logo.setPadding(0,0,15,0);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(1);

        TextView title = text("NEXUS AI",21,white);
        title.setTypeface(null,Typeface.BOLD);

        TextView status = text("● AI Agent Online",12,
            Color.rgb(74,222,128));

        titleBox.addView(title);
        titleBox.addView(status);

        header.addView(logo);
        header.addView(titleBox);

        root.addView(header);

        // WELCOME
        TextView welcome = text(
            "សួស្តី! 👋\nខ្ញុំជា AI Agent របស់អ្នក។\nតើថ្ងៃនេះខ្ញុំអាចជួយអ្វីអ្នកបាន?",
            17,white
        );

        welcome.setPadding(16,22,16,22);
        welcome.setBackground(bg(Color.rgb(25,35,53),18));

        LinearLayout.LayoutParams wp =
            new LinearLayout.LayoutParams(-1,-2);
        wp.setMargins(0,20,0,15);
        root.addView(welcome,wp);

        // CHAT AREA
        ScrollView scroll = new ScrollView(this);

        messages = new LinearLayout(this);
        messages.setOrientation(1);
        messages.setPadding(3,5,3,10);

        scroll.addView(messages);

        root.addView(scroll,
            new LinearLayout.LayoutParams(-1,0,1));

        // INPUT AREA
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(8,8,8,8);
        bottom.setBackground(bg(Color.rgb(20,29,43),18));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(white);
        input.setHintTextColor(Color.GRAY);
        input.setHint("សរសេរសាររបស់អ្នក...");
        input.setTextSize(15);
        input.setPadding(12,12,8,12);
        input.setBackgroundColor(Color.TRANSPARENT);

        send = new Button(this);
        send.setText("➤");
        send.setTextSize(20);
        send.setTextColor(white);
        send.setBackground(bg(purple,15));

        bottom.addView(input,
            new LinearLayout.LayoutParams(0,-2,1));

        bottom.addView(send,
            new LinearLayout.LayoutParams(55,52));

        root.addView(bottom);

        setContentView(root);

        send.setOnClickListener(v -> sendMessage());
        input.setOnEditorActionListener((v,id,event)->{
            sendMessage();
            return true;
        });
    }

    void addMessage(String message, boolean user) {
        TextView t = text(message,15,white);
        t.setPadding(15,12,15,12);
        t.setBackground(bg(
            user ? Color.rgb(100,75,210)
                 : Color.rgb(29,42,61),18
        ));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-2,-2);

        p.setMargins(0,7,0,7);

        if(user) p.gravity = Gravity.RIGHT;
        else p.gravity = Gravity.LEFT;

        messages.addView(t,p);
    }

    void sendMessage() {
        String question = input.getText().toString().trim();

        if(question.isEmpty()) return;

        addMessage(question,true);
        input.setText("");

        send.setEnabled(false);

        addMessage("AI កំពុងគិត... ⏳",false);

        new Thread(() -> {
            String answer;

            try {
                URL url = new URL(API_URL);
                HttpURLConnection con =
                    (HttpURLConnection)url.openConnection();

                con.setRequestMethod("POST");
                con.setConnectTimeout(15000);
                con.setReadTimeout(60000);
                con.setDoOutput(true);

                con.setRequestProperty(
                    "Content-Type","application/json"
                );

                JSONObject data = new JSONObject();
                data.put("message",question);

                OutputStream os = con.getOutputStream();
                os.write(data.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = con.getResponseCode();

                InputStream is = responseCode >= 400
                    ? con.getErrorStream()
                    : con.getInputStream();

                BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(is,"UTF-8")
                    );

                StringBuilder result = new StringBuilder();
                String line;

                while((line=reader.readLine())!=null) {
                    result.append(line);
                }

                reader.close();

                JSONObject json =
                    new JSONObject(result.toString());

                answer = json.optString(
                    "reply","AI មិនបានផ្ញើចម្លើយទេ។"
                );

                con.disconnect();

            } catch(Exception e) {
                answer = "Connection Error: " + e.getMessage();
            }

            final String response = answer;

            runOnUiThread(() -> {
                messages.removeViewAt(
                    messages.getChildCount()-1
                );

                addMessage(response,false);
                send.setEnabled(true);
            });

        }).start();
    }
}
