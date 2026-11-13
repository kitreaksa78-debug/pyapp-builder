package org.pyapp.buildfix36;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("Gradle 8.14.3 + SDK 36 OK");
        view.setTextSize(22);
        setContentView(view);
    }
}
