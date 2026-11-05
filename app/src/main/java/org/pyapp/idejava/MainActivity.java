package org.pyapp.idejava;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView t = new TextView(this);
        t.setText("Java native ដំណើរការ");
        setContentView(t);
    }
}
