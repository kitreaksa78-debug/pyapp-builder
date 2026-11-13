package org.pyapp.buildfix;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("Build pipeline OK");
        view.setTextSize(24);
        setContentView(view);
    }
}
