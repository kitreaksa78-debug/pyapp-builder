package org.pyapp.qajava;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** App native Java (Android Views) — គ្មាន WebView។ */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 2, pad, pad);
        root.setBackgroundColor(Color.parseColor("#0D1117"));

        final TextView title = new TextView(this);
        title.setText("សួស្តី 👋");
        title.setTextSize(26f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        TextView hint = new TextView(this);
        hint.setText("app នេះសរសេរដោយ Java");
        hint.setTextSize(15f);
        hint.setTextColor(Color.parseColor("#8B949E"));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, pad / 2, 0, pad);

        final EditText name = new EditText(this);
        name.setHint("ឈ្មោះរបស់អ្នក");
        name.setTextColor(Color.WHITE);
        name.setHintTextColor(Color.parseColor("#8B949E"));

        Button btn = new Button(this);
        btn.setText("ចុចទីនេះ");
        btn.setOnClickListener(v -> {
            String who = name.getText().toString().trim();
            title.setText(who.isEmpty() ? "បានចុច 🎉" : "សួស្តី " + who + " 🎉");
            Toast.makeText(MainActivity.this, "Java + Android", Toast.LENGTH_SHORT).show();
        });

        root.addView(title);
        root.addView(hint);
        root.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }
}
