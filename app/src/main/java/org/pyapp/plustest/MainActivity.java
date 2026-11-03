package org.pyapp.plustest;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private int total = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 80, 60, 60);

        TextView title = new TextView(this);
        title.setText("បូកលេខ");
        title.setTextSize(26);
        root.addView(title);

        final TextView result = new TextView(this);
        result.setText("សរុប: 0");
        result.setTextSize(20);
        root.addView(result);

        Button add = new Button(this);
        add.setText("+5");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                total = total + 5;
                result.setText("សរុប: " + total);
                Toast.makeText(MainActivity.this, "បន្ថែម ៥", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(add);

        setContentView(root);
    }
}
