package org.pyapp.counter;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int count = 0;
    private TextView label;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(60, 60, 60, 60);

        label = new TextView(this);
        label.setText("ចុចបាន 0 ដង");
        label.setTextSize(28);
        label.setGravity(Gravity.CENTER);
        root.addView(label);

        Button b = new Button(this);
        b.setText("ចុច +1");
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count = count + 1;
                label.setText("ចុចបាន " + count + " ដង");
            }
        });
        root.addView(b);

        setContentView(root);
    }
}
