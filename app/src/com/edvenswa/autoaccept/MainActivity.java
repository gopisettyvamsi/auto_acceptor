package com.edvenswa.autoaccept;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;

public class MainActivity extends Activity {
    static final String PREFS = "p";
    static final String DEFAULT_PKGS = "com.rapido.captain,com.ubercab.driver,com.olacabs.oladriver";

    SharedPreferences sp;

    EditText field(LinearLayout root, String label, String val, int type) {
        TextView t = new TextView(this); t.setText(label); root.addView(t);
        EditText e = new EditText(this); e.setInputType(type); e.setText(val); root.addView(e);
        return e;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        sv.addView(root);

        Switch on = new Switch(this); on.setText("Auto-accept ON"); on.setChecked(sp.getBoolean("on", false));
        root.addView(on);
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText fare = field(root, "Minimum fare (Rs, 0 = any)", sp.getString("fare", "0"), num);
        EditText dist = field(root, "Max pickup distance (km, 0 = any)", sp.getString("dist", "0"), num);
        EditText delay = field(root, "Delay before tap (ms)", sp.getString("delay", "300"), InputType.TYPE_CLASS_NUMBER);
        EditText pkgs = field(root, "Driver app package names (comma separated)", sp.getString("pkgs", DEFAULT_PKGS), InputType.TYPE_CLASS_TEXT);

        Button save = new Button(this); save.setText("Save");
        save.setOnClickListener(v -> {
            sp.edit().putBoolean("on", on.isChecked())
                .putString("fare", fare.getText().toString())
                .putString("dist", dist.getText().toString())
                .putString("delay", delay.getText().toString())
                .putString("pkgs", pkgs.getText().toString()).apply();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        Button acc = new Button(this); acc.setText("Step 1: enable Auto Acceptor in Accessibility settings");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(acc);
        TextView log = new TextView(this); log.setTextIsSelectable(true); log.setTextSize(11);
        Button ref = new Button(this); ref.setText("Debug: show last screen read");
        ref.setOnClickListener(v -> log.setText(sp.getString("dump", "nothing read yet - open a ride offer in the driver app first")));
        root.addView(ref); root.addView(log);
        setContentView(sv);
    }
}
