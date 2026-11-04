package com.androditto;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Handles Android's PROCESS_TEXT action for selected text. */
public final class SendSelectedTextActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 24, 28, 20);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
        TextView title = new TextView(this);
        title.setText("AndroDitto · Send to PC");
        title.setTextSize(20);
        root.addView(title);
        TextView status = new TextView(this);
        status.setText("Sending selected text…");
        status.setPadding(0, 18, 0, 12);
        root.addView(status);
        setContentView(root);

        CharSequence selected = getIntent().getCharSequenceExtra(IntentText.EXTRA_PROCESS_TEXT);
        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        String host = prefs.getString("host", "").trim();
        String password = prefs.getString("password", "");
        if (getIntent().getAction() == null || !IntentText.ACTION_PROCESS_TEXT.equals(getIntent().getAction())
                || selected == null || selected.length() == 0) {
            showError(status, "No selected text was provided.");
            return;
        }
        if (host.isEmpty() || password.isEmpty()) {
            status.setText("Set the PC Tailscale IP and Ditto password in AndroDitto first.");
            Button open = new Button(this);
            open.setText("Open AndroDitto");
            root.addView(open);
            open.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, MainActivity.class));
                finish();
            });
            return;
        }
        String value = selected.toString();
        new Thread(() -> {
            String result;
            try {
                DittoProtocol.sendText(host, password, value, MainActivity.tailscaleAddress());
                TransferHistory.add(this, "SENT", "TEXT", "Selected text to PC");
                result = "Sent to Ditto on your PC.";
            } catch (Exception e) {
                result = "Could not send: " + e.getMessage();
            }
            String message = result;
            new Handler(Looper.getMainLooper()).post(() -> {
                status.setText(message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                if (message.startsWith("Sent")) new Handler(Looper.getMainLooper()).postDelayed(this::finish, 700);
                else {
                    Button close = new Button(this);
                    close.setText("Close");
                    ((LinearLayout)root).addView(close);
                    close.setOnClickListener(v -> finish());
                }
            });
        }, "AndroDitto-send-selection").start();
    }

    private void showError(TextView status, String message) {
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1200);
    }

    /** Intent constants avoid coupling this tiny receiver to selection UI internals. */
    private static final class IntentText {
        static final String ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT";
        static final String EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT";
    }
}
