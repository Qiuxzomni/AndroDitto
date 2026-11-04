package com.androditto;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 13, 18);
    private static final int PANEL = Color.rgb(16, 25, 34);
    private static final int PANEL_ALT = Color.rgb(19, 31, 42);
    private static final int LINE = Color.rgb(48, 69, 80);
    private static final int CYAN = Color.rgb(64, 223, 239);
    private static final int AMBER = Color.rgb(255, 184, 77);
    private static final int GREEN = Color.rgb(91, 242, 162);
    private static final int WHITE = Color.rgb(233, 242, 247);
    private static final int MUTED = Color.rgb(130, 150, 163);

    private LinearLayout root;
    private FrameLayout pages;
    private ScrollView homePage, historyPage;
    private LinearLayout homeContent;
    private TextView homeStatus, listenerValue, pcAddressValue, historySentValue, historyReceivedValue;
    private LinearLayout homeRecentRows, historyRows;
    private FrameLayout bottomNav;
    private Button receiveButton;
    private EditText textInput;
    private ArtFrame homeLinkFrame, homeActionsFrame, homeActivityFrame;
    private int currentPage;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        if (prefs.getBoolean("backgroundReceive", false)) startReceiverService();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        FrameLayout swipeArea = new SwipeFrame(this);
        pages = new FrameLayout(this);
        homePage = buildHomePage();
        historyPage = buildHistoryPage();
        pages.addView(homePage, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(historyPage, new FrameLayout.LayoutParams(-1, -1));
        historyPage.setVisibility(View.GONE);
        swipeArea.addView(pages, new FrameLayout.LayoutParams(-1, -1));
        root.addView(swipeArea, new LinearLayout.LayoutParams(-1, 0, 1));
        bottomNav = buildBottomNav();
        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        updateHome();
        updateBottomNav();
    }

    @Override protected void onResume() {
        super.onResume();
        if (homeContent != null) updateHome();
        if (currentPage == 1) refreshHistory();
    }

    private ScrollView buildHomePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(0, 0, 0, dp(8));
        scroll.addView(homeContent, new ScrollView.LayoutParams(-1, -2));
        ArtFrame header = artFrame(R.drawable.home_header, 52, 116);
        Button settings = transparentTap();
        settings.setOnClickListener(v -> showSettings());
        header.place(settings, 829, 68, 93, 91);
        addArt(homeContent, header, 0);

        homeLinkFrame = artFrame(R.drawable.home_link, 180, 315);
        listenerValue = label("PC ONLINE", 13, GREEN, true);
        listenerValue.setTextSize(10);
        listenerValue.setSingleLine(true);
        listenerValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        listenerValue.setGravity(Gravity.CENTER);
        listenerValue.setBackground(round(0xFF091B20, 0x8840D9E8, 12));
        homeLinkFrame.place(listenerValue, 118, 312, 183, 46);
        pcAddressValue = label(configuredHost(), 12, WHITE, true);
        pcAddressValue.setGravity(Gravity.CENTER);
        pcAddressValue.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        pcAddressValue.setSingleLine(true);
        pcAddressValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pcAddressValue.setBackground(round(0xFF0A1A20, 0x6640D9E8, 10));
        homeLinkFrame.place(pcAddressValue, 121, 378, 176, 43);
        addArt(homeContent, homeLinkFrame, 10);

        ArtFrame composer = artFrame(R.drawable.home_composer, 507, 389);
        textInput = new EditText(this);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setTextSize(16);
        textInput.setTextColor(WHITE);
        textInput.setHintTextColor(MUTED);
        textInput.setHint("");
        textInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        textInput.setBackgroundColor(Color.TRANSPARENT);
        textInput.setSingleLine(false);
        textInput.setMinLines(3);
        textInput.setMaxLines(5);
        composer.place(textInput, 54, 588, 833, 164);
        Button send = transparentTap();
        send.setOnClickListener(v -> sendTextToPc());
        composer.place(send, 178, 785, 585, 81);
        TextView paste = label("PASTE", 11, CYAN, true);
        paste.setGravity(Gravity.CENTER);
        paste.setOnClickListener(v -> pasteClipboard());
        composer.place(paste, 785, 521, 97, 51);
        addArt(homeContent, composer, 10);

        homeActionsFrame = artFrame(R.drawable.home_actions, 910, 210);
        Button choose = transparentTap();
        choose.setOnClickListener(v -> chooseFiles());
        homeActionsFrame.place(choose, 55, 991, 372, 99);
        receiveButton = actionButton("", Color.TRANSPARENT, WHITE, Color.TRANSPARENT);
        receiveButton.setBackgroundColor(Color.TRANSPARENT);
        receiveButton.setOnClickListener(v -> toggleReceiving());
        homeActionsFrame.place(receiveButton, 773, 993, 128, 82);
        TextView latest = label("SHARE LAST  ↓", 9, CYAN, true);
        latest.setGravity(Gravity.CENTER);
        latest.setOnClickListener(v -> shareLatestReceived());
        homeActionsFrame.place(latest, 256, 916, 155, 42);
        addArt(homeContent, homeActionsFrame, 10);

        homeActivityFrame = artFrame(R.drawable.home_activity, 1141, 361);
        homeRecentRows = new LinearLayout(this);
        homeRecentRows.setOrientation(LinearLayout.VERTICAL);
        homeRecentRows.setBackground(round(0xFF09151B, 0x6650D9E8, 9));
        homeRecentRows.setPadding(dp(6), dp(3), dp(6), dp(3));
        homeRecentRows.setTag("home-recent-rows");
        homeActivityFrame.place(homeRecentRows, 235, 1213, 656, 234);
        TextView viewAll = label("OPEN LOG  ›", 10, CYAN, true);
        viewAll.setGravity(Gravity.CENTER);
        viewAll.setOnClickListener(v -> showPage(1));
        homeActivityFrame.place(viewAll, 752, 1148, 135, 48);
        addArt(homeContent, homeActivityFrame, 10);
        return scroll;
    }

    private View buildConnectionCard() {
        LinearLayout card = panel(PANEL, LINE, 18);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout titleRow = row();
        TextView title = label("DITTO LINK", 12, AMBER, true);
        title.setLetterSpacing(0.12f);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        listenerValue = label("RECEIVE OFF", 10, AMBER, true);
        listenerValue.setLetterSpacing(0.06f);
        titleRow.addView(listenerValue);
        card.addView(titleRow);
        space(card, 14);

        LinearLayout route = row();
        route.setGravity(Gravity.CENTER_VERTICAL);
        TextView phone = chip("ANDROID", CYAN);
        route.addView(phone, new LinearLayout.LayoutParams(-2, dp(34)));
        TextView arrows = label("  ◀━━━━  ⇄  ━━━━▶  ", 13, CYAN, true);
        arrows.setGravity(Gravity.CENTER);
        route.addView(arrows, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView pc = chip("PC", WHITE);
        route.addView(pc, new LinearLayout.LayoutParams(-2, dp(34)));
        card.addView(route);
        space(card, 14);

        TextView pcAddress = label("PC  ·  " + configuredHost(), 13, WHITE, true);
        card.addView(pcAddress);
        space(card, 4);
        String phoneIp = tailscaleAddress();
        TextView addresses = label("PHONE  ·  " + (phoneIp.isEmpty() ? "Tailscale not detected" : phoneIp), 11, MUTED, false);
        addresses.setTypeface(Typeface.MONOSPACE);
        card.addView(addresses);
        return card;
    }

    private View buildComposerCard() {
        LinearLayout card = panel(PANEL, LINE, 18);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout heading = row();
        TextView title = label("CLIPBOARD TEXT", 12, AMBER, true);
        title.setLetterSpacing(0.1f);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView paste = label("PASTE", 10, CYAN, true);
        paste.setPadding(dp(10), dp(6), dp(10), dp(6));
        paste.setBackground(round(PANEL_ALT, CYAN, 12));
        heading.addView(paste);
        paste.setOnClickListener(v -> pasteClipboard());
        card.addView(heading);
        space(card, 10);

        textInput = new EditText(this);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setMinLines(3);
        textInput.setMaxLines(5);
        textInput.setTextSize(15);
        textInput.setTextColor(WHITE);
        textInput.setHintTextColor(MUTED);
        textInput.setHint("Write or paste text…");
        textInput.setPadding(dp(13), dp(12), dp(13), dp(12));
        textInput.setBackground(round(Color.rgb(10, 17, 24), LINE, 12));
        card.addView(textInput, matchHeight(dp(108)));
        space(card, 10);

        Button send = actionButton("➤   SEND TO PC", CYAN, BG, CYAN);
        send.setTextSize(14);
        send.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(send, matchHeight(dp(48)));
        send.setOnClickListener(v -> sendTextToPc());
        return card;
    }

    private View buildActionRow() {
        LinearLayout actions = row();
        LinearLayout files = panel(PANEL_ALT, LINE, 16);
        files.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView fileIcon = label("▤", 22, AMBER, true);
        files.addView(fileIcon);
        space(files, 7);
        TextView fileTitle = label("FILES", 11, MUTED, true);
        fileTitle.setLetterSpacing(0.1f);
        files.addView(fileTitle);
        TextView fileAction = label("Choose files  ›", 14, WHITE, true);
        files.addView(fileAction);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, dp(112), 1);
        actionLp.rightMargin = dp(7);
        actions.addView(files, actionLp);
        files.setOnClickListener(v -> chooseFiles());

        LinearLayout receive = panel(PANEL_ALT, LINE, 16);
        receive.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView receiveIcon = label("⇩", 22, CYAN, true);
        receive.addView(receiveIcon);
        space(receive, 7);
        TextView receiveTitle = label("BACKGROUND RX", 10, MUTED, true);
        receiveTitle.setLetterSpacing(0.07f);
        receive.addView(receiveTitle);
        receiveButton = actionButton("", PANEL, WHITE, LINE);
        receiveButton.setTextSize(13);
        LinearLayout.LayoutParams toggleLp = matchHeight(dp(34));
        toggleLp.topMargin = dp(5);
        receive.addView(receiveButton, toggleLp);
        LinearLayout.LayoutParams receiveLp = new LinearLayout.LayoutParams(0, dp(112), 1);
        receiveLp.leftMargin = dp(7);
        actions.addView(receive, receiveLp);
        receiveButton.setOnClickListener(v -> toggleReceiving());
        receive.setOnClickListener(v -> toggleReceiving());
        return actions;
    }

    private ScrollView buildHistoryPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(8));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        ArtFrame header = artFrame(R.drawable.home_header, 52, 116);
        Button settings = transparentTap(); settings.setOnClickListener(v -> showSettings());
        header.place(settings, 829, 68, 93, 91);
        addArt(content, header, 0);
        ArtFrame metrics = artFrame(R.drawable.history_metrics, 188, 522);
        historySentValue = label("00", 28, CYAN, true); historySentValue.setGravity(Gravity.CENTER);
        historySentValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        historyReceivedValue = label("00", 28, GREEN, true); historyReceivedValue.setGravity(Gravity.CENTER);
        historyReceivedValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        historySentValue.setBackground(round(0xFF08171E, 0x7740DCE8, 9));
        historyReceivedValue.setBackground(round(0xFF081A16, 0x775BEA91, 9));
        metrics.place(historySentValue, 332, 564, 94, 70);
        metrics.place(historyReceivedValue, 785, 564, 94, 70);
        addArt(content, metrics, 10);
        ArtFrame timeline = artFrame(R.drawable.history_timeline, 729, 742);
        historyRows = new LinearLayout(this);
        historyRows.setOrientation(LinearLayout.VERTICAL);
        historyRows.setBackground(round(0xFF09151B, 0x7750D9E8, 9));
        historyRows.setPadding(dp(7), dp(5), dp(7), dp(5));
        timeline.place(historyRows, 232, 841, 664, 582);
        addArt(content, timeline, 10);
        scroll.setTag(content);
        return scroll;
    }

    private ArtFrame buildBottomNav() {
        ArtFrame nav = artFrame(R.drawable.home_nav, 1502, 145);
        nav.setTag(new int[] { R.drawable.home_nav, R.drawable.history_nav });
        Button home = transparentTap(), history = transparentTap();
        home.setOnClickListener(v -> showPage(0)); history.setOnClickListener(v -> showPage(1));
        nav.place(home, 85, 1508, 390, 138);
        nav.place(history, 475, 1508, 390, 138);
        return nav;
    }

    private void updateBottomNav() {
        if (root == null) return;
        Object tag = bottomNav.getTag();
        if (tag instanceof int[]) ((ArtFrame)bottomNav).setArt(currentPage == 0 ? ((int[])tag)[0] : ((int[])tag)[1]);
    }

    private void showPage(int page) {
        currentPage = page == 0 ? 0 : 1;
        homePage.setVisibility(currentPage == 0 ? View.VISIBLE : View.GONE);
        historyPage.setVisibility(currentPage == 1 ? View.VISIBLE : View.GONE);
        if (currentPage == 0) {
            updateHome();
            homePage.post(() -> homePage.scrollTo(0, 0));
        } else {
            refreshHistory();
            historyPage.post(() -> historyPage.scrollTo(0, 0));
        }
        updateBottomNav();
    }

    private void updateHome() {
        if (homeContent == null) return;
        if (listenerValue != null) {
            boolean enabled = getSharedPreferences("peer", MODE_PRIVATE).getBoolean("backgroundReceive", false);
            boolean ready = ClipboardListenerService.isReady();
            listenerValue.setText(configuredHost().contains("not configured") ? "PC NOT SET" : ready ? "LINK READY" : enabled ? "CONNECTING" : "PC LINK SET");
            listenerValue.setTextColor(ready ? GREEN : AMBER);
        }
        if (receiveButton != null) {
            boolean enabled = getSharedPreferences("peer", MODE_PRIVATE).getBoolean("backgroundReceive", false);
            receiveButton.setText(enabled ? "" : "OFF");
            receiveButton.setTextColor(enabled ? GREEN : MUTED);
            receiveButton.setBackground(enabled ? new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                    : round(Color.rgb(13, 29, 37), CYAN, 16));
        }
        if (pcAddressValue != null) pcAddressValue.setText(configuredHost().contains("not configured") ? "ADD PC IN SETTINGS" : configuredHost());
        refreshHomeRecent();
        if (homeStatus != null) {
            String message = getSharedPreferences("peer", MODE_PRIVATE).getString("lastStatus", "Ready to transfer.");
            homeStatus.setText(message);
        }
    }

    private void addHomeRecent() {
        LinearLayout card = panel(PANEL, LINE, 16);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout header = row();
        TextView title = label("RECENT ACTIVITY", 11, AMBER, true);
        title.setLetterSpacing(0.1f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView viewAll = label("VIEW ALL  ›", 10, CYAN, true);
        header.addView(viewAll);
        viewAll.setOnClickListener(v -> showPage(1));
        card.addView(header);
        LinearLayout recentRows = new LinearLayout(this);
        recentRows.setOrientation(LinearLayout.VERTICAL);
        recentRows.setTag("home-recent-rows");
        space(card, 6);
        card.addView(recentRows);
        homeContent.addView(card);
        refreshHomeRecent();
    }

    private void refreshHomeRecent() {
        LinearLayout target = homeRecentRows;
        if (target == null) return;
        target.removeAllViews();
        List<TransferHistory.Entry> entries = TransferHistory.read(this);
        if (entries.isEmpty()) {
            TextView empty = label("NO RECENT TRANSFERS", 12, MUTED, true);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            target.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }
        int count = Math.min(2, entries.size());
        for (int i = 0; i < count; i++) {
            TransferHistory.Entry entry = entries.get(i);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setOrientation(LinearLayout.VERTICAL);
            line.setPadding(dp(7), dp(1), dp(4), dp(1));
            TextView title = label(entry.label, 14, WHITE, true);
            title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));
            String direction = entry.direction.equals("SENT") ? "TO PC" : "FROM PC";
            TextView meta = label(direction + "  ·  " + entry.kind + "  ·  " + shortTime(entry.time), 11,
                    entry.direction.equals("SENT") ? CYAN : GREEN, false);
            line.addView(meta, new LinearLayout.LayoutParams(-1, 0, 1));
            if (i > 0) { View divider = new View(this); divider.setBackgroundColor(0x663D6673); target.addView(divider, new LinearLayout.LayoutParams(-1, dp(1))); }
            target.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private void refreshHistory() {
        if (historyRows == null) return;
        List<TransferHistory.Entry> entries = TransferHistory.read(this);
        int sent = 0, received = 0;
        for (TransferHistory.Entry entry : entries) {
            if (entry.direction.equals("SENT")) sent++; else received++;
        }
        if (historySentValue != null) historySentValue.setText(String.format(java.util.Locale.ROOT, "%02d", sent));
        if (historyReceivedValue != null) historyReceivedValue.setText(String.format(java.util.Locale.ROOT, "%02d", received));
        historyRows.removeAllViews();
        if (entries.isEmpty()) {
            TextView none = label("NO TRANSFERS YET", 17, WHITE, true);
            none.setGravity(Gravity.CENTER);
            historyRows.addView(none, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView hint = label("Send text or files to begin your local timeline", 12, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            historyRows.addView(hint, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }
        int count = Math.min(4, entries.size());
        for (int i = 0; i < count; i++) {
            TransferHistory.Entry entry = entries.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(2), dp(8), dp(2));
            TextView name = label(entry.label, 15, WHITE, true);
            name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(-1, 0, 1));
            String direction = entry.direction.equals("SENT") ? "TO PC" : "FROM PC";
            TextView meta = label(direction + "  ·  " + entry.kind + "  ·  " + shortTime(entry.time), 11,
                    entry.direction.equals("SENT") ? CYAN : GREEN, false);
            row.addView(meta, new LinearLayout.LayoutParams(-1, 0, 1));
            if (i > 0) { View divider = new View(this); divider.setBackgroundColor(0x773B5E69); historyRows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1))); }
            historyRows.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private View statCard(String title, String value, int accent) {
        LinearLayout card = panel(PANEL, LINE, 14);
        card.setPadding(dp(13), dp(10), dp(13), dp(8));
        TextView label = label(title, 9, MUTED, true);
        label.setLetterSpacing(0.11f);
        card.addView(label);
        TextView number = label(value, 25, accent, true);
        number.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        card.addView(number);
        return card;
    }

    private View historyRow(TransferHistory.Entry entry) {
        LinearLayout card = panel(PANEL, LINE, 14);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout row = row();
        TextView direction = label(entry.direction.equals("SENT") ? "↑" : "↓", 23,
                entry.direction.equals("SENT") ? CYAN : GREEN, true);
        direction.setGravity(Gravity.CENTER);
        direction.setBackground(round(PANEL_ALT, LINE, 12));
        row.addView(direction, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, 0, 0);
        TextView name = label(entry.label, 14, WHITE, true);
        details.addView(name);
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.time));
        TextView meta = label(entry.direction + "  ·  " + entry.kind + "  ·  " + when, 10, MUTED, false);
        details.addView(meta);
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void showSettings() {
        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        Dialog dialog = new Dialog(this);
        LinearLayout box = panel(PANEL, CYAN, 18);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(16));
        TextView heading = label("CONNECTION SETTINGS", 15, WHITE, true);
        heading.setLetterSpacing(0.06f);
        box.addView(heading);
        TextView phone = label("Phone Tailscale IP: " + (tailscaleAddress().isEmpty() ? "not detected" : tailscaleAddress())
                + "\nAdd this IP to Ditto on the PC under Options → Friends.", 11, MUTED, false);
        phone.setPadding(0, dp(10), 0, dp(12));
        box.addView(phone);
        EditText pc = editField("PC Tailscale IP", prefs.getString("host", ""));
        box.addView(pc, matchHeight(dp(48)));
        space(box, 9);
        EditText secret = editField("Ditto shared password", prefs.getString("password", ""));
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(secret, matchHeight(dp(48)));
        space(box, 14);
        LinearLayout buttons = row();
        Button cancel = actionButton("CANCEL", PANEL_ALT, MUTED, LINE);
        Button save = actionButton("SAVE LINK", CYAN, BG, CYAN);
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1));
        View separator = new View(this); buttons.addView(separator, new LinearLayout.LayoutParams(dp(9), 1));
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        box.addView(buttons);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            prefs.edit().putString("host", pc.getText().toString().trim())
                    .putString("password", secret.getText().toString()).apply();
            if (prefs.getBoolean("backgroundReceive", false)) {
                stopService(new Intent(this, ClipboardListenerService.class));
                mainHandler.postDelayed(this::startReceiverService, 200);
            }
            updateHome();
            setStatus("Connection settings saved.");
            dialog.dismiss();
        });
        dialog.setContentView(box);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(34), -2);
        }
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(34), -2);
    }

    private void sendTextToPc() {
        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        String host = prefs.getString("host", "").trim();
        String password = prefs.getString("password", "");
        String value = textInput.getText().toString();
        if (host.isEmpty() || password.isEmpty()) { setStatus("Set your PC address and Ditto password first."); showSettings(); return; }
        if (value.trim().isEmpty()) { setStatus("Enter some text to send."); return; }
        setStatus("Sending text to PC…");
        new Thread(() -> {
            String result;
            try {
                DittoProtocol.sendText(host, password, value, tailscaleAddress());
                TransferHistory.add(this, "SENT", "TEXT", "Text to PC");
                result = "Text sent to Ditto.";
            } catch (Exception e) { result = "Could not send: " + e.getMessage(); }
            String message = result;
            mainHandler.post(() -> { setStatus(message); updateHome(); });
        }).start();
    }

    private void toggleReceiving() {
        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("backgroundReceive", false);
        if (enabled) {
            prefs.edit().putBoolean("backgroundReceive", false).apply();
            stopService(new Intent(this, ClipboardListenerService.class));
            updateHome(); setStatus("Background receive stopped.");
            return;
        }
        if (prefs.getString("password", "").isEmpty()) { showSettings(); setStatus("Save your Ditto password to receive."); return; }
        prefs.edit().putBoolean("backgroundReceive", true).apply();
        startReceiverService();
        updateHome(); setStatus("Background receive enabled.");
    }

    private void startReceiverService() {
        Intent service = new Intent(this, ClipboardListenerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
    }

    private void pasteClipboard() {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            textInput.setText(value);
            textInput.setSelection(textInput.length());
            setStatus("Clipboard text loaded into the composer.");
        } else setStatus("Phone clipboard is empty.");
    }

    private void chooseFiles() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(picker, 42);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 42 || resultCode != RESULT_OK || data == null) return;
        ArrayList<Uri> selected = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) selected.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) selected.add(data.getData());
        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        String pc = prefs.getString("host", "").trim(), secret = prefs.getString("password", "");
        if (pc.isEmpty() || secret.isEmpty()) { showSettings(); setStatus("Set your PC address and Ditto password first."); return; }
        setStatus("Preparing " + selected.size() + " file(s)…");
        new Thread(() -> {
            String result;
            try {
                List<String> names = new ArrayList<>();
                for (Uri uri : selected) {
                    String name = displayName(uri);
                    if (name == null || name.isEmpty()) name = "shared-file";
                    name = name.replaceAll("[^A-Za-z0-9._-]", "_");
                    File cached = new File(getCacheDir(), "shared_" + name);
                    try (java.io.InputStream input = getContentResolver().openInputStream(uri);
                         FileOutputStream output = new FileOutputStream(cached)) {
                        if (input == null) throw new java.io.IOException("Cannot open " + name);
                        byte[] buffer = new byte[65536]; int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    prefs.edit().putString("sharedFile:" + name, cached.getAbsolutePath()).apply();
                    names.add(name);
                }
                prefs.edit().putBoolean("backgroundReceive", true).apply();
                startReceiverService();
                long deadline = System.currentTimeMillis() + 8000;
                while (!ClipboardListenerService.isReady() && System.currentTimeMillis() < deadline) Thread.sleep(100);
                if (!ClipboardListenerService.isReady()) throw new java.io.IOException("Could not start the Ditto receiver.");
                DittoProtocol.sendFileClip(pc, secret, tailscaleAddress(), names);
                for (String name : names) TransferHistory.add(this, "SENT", "FILE", name);
                result = "Shared " + names.size() + " file(s) with Ditto.";
            } catch (Exception e) { result = "Could not send files: " + e.getMessage(); }
            String message = result;
            mainHandler.post(() -> { setStatus(message); updateHome(); });
        }).start();
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment();
    }

    private void shareLatestReceived() {
        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        String publicUri = prefs.getString("lastReceivedUri", "");
        Uri uri;
        if (!publicUri.isEmpty()) {
            uri = Uri.parse(publicUri);
        } else {
            String path = prefs.getString("lastReceivedPath", "");
            if (path.isEmpty() || !new File(path).isFile()) { setStatus("No received file is available yet."); return; }
            uri = Uri.parse("content://com.androditto.files/" + Uri.encode(new File(path).getName()));
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        String type = getContentResolver().getType(uri);
        share.setType(type == null ? "application/octet-stream" : type);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share received file"));
    }

    private void setStatus(String message) {
        getSharedPreferences("peer", MODE_PRIVATE).edit().putString("lastStatus", message).apply();
        if (homeStatus != null) homeStatus.setText(message);
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String shortTime(long timestamp) {
        return android.text.format.DateFormat.getTimeFormat(this).format(new Date(timestamp));
    }

    private Button transparentTap() {
        Button button = new Button(this);
        button.setText("");
        button.setBackground(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        return button;
    }

    private ArtFrame artFrame(int drawable, int sourceY, int sourceHeight) {
        return new ArtFrame(this, drawable, sourceY, sourceHeight);
    }

    private void addArt(LinearLayout parent, ArtFrame frame, int gapAfter) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (gapAfter > 0) params.bottomMargin = dp(gapAfter);
        parent.addView(frame, params);
    }

    private final class ArtFrame extends FrameLayout {
        private static final int SOURCE_WIDTH = 941;
        private static final float VERTICAL_STRETCH = 1.18f;
        private final int sourceTop, sourceHeight;
        private final android.widget.ImageView image;
        private final ArrayList<Placement> placements = new ArrayList<>();

        ArtFrame(Context context, int drawable, int top, int height) {
            super(context);
            sourceTop = top; sourceHeight = height;
            image = new android.widget.ImageView(context);
            image.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            image.setImageResource(drawable);
            addView(image, new FrameLayout.LayoutParams(-1, -1));
            setClipChildren(true);
        }

        void setArt(int drawable) { image.setImageResource(drawable); }

        void place(View view, int x, int y, int width, int height) {
            placements.add(new Placement(view, x, y, width, height));
            addView(view);
            requestLayout();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (width == 0) width = getResources().getDisplayMetrics().widthPixels;
            float scale = width / (float)SOURCE_WIDTH;
            int height = Math.round(sourceHeight * scale * VERTICAL_STRETCH);
            setMeasuredDimension(width, height);
            image.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            for (Placement p : placements) {
                int w = Math.max(1, Math.round(p.width * scale));
                int h = Math.max(1, Math.round(p.height * scale * VERTICAL_STRETCH));
                p.view.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            float scale = width / (float)SOURCE_WIDTH;
            image.layout(0, 0, width, b - t);
            for (Placement p : placements) {
                int left = Math.round(p.x * scale);
                int top = Math.round((p.y - sourceTop) * scale * VERTICAL_STRETCH);
                p.view.layout(left, top, left + p.view.getMeasuredWidth(), top + p.view.getMeasuredHeight());
            }
        }
    }

    private static final class Placement {
        final View view; final int x, y, width, height;
        Placement(View view, int x, int y, int width, int height) {
            this.view = view; this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    private String configuredHost() {
        String host = getSharedPreferences("peer", MODE_PRIVATE).getString("host", "").trim();
        return host.isEmpty() ? "PC address not configured" : host;
    }

    private LinearLayout panel(int color, int stroke, int radius) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(round(color, stroke, radius));
        return view;
    }

    private LinearLayout row() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        return line;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private TextView chip(String text, int color) {
        TextView view = label(text, 10, color, true);
        view.setLetterSpacing(0.08f);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(PANEL_ALT, color, 10));
        return view;
    }

    private Button actionButton(String text, int fill, int foreground, int stroke) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(foreground);
        button.setTextSize(12);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setAllCaps(false);
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        button.setBackground(round(fill, stroke, 12));
        button.setStateListAnimator(null);
        return button;
    }

    private EditText editField(String hint, String text) {
        EditText field = new EditText(this);
        field.setText(text);
        field.setHint(hint);
        field.setTextColor(WHITE);
        field.setHintTextColor(MUTED);
        field.setSingleLine(true);
        field.setPadding(dp(12), dp(5), dp(12), dp(5));
        field.setBackground(round(Color.rgb(10, 17, 24), LINE, 10));
        return field;
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != fill) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchHeight(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private void space(LinearLayout parent, int size) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size))); }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    static String tailscaleAddress() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress address : Collections.list(nic.getInetAddresses())) {
                    String ip = address.getHostAddress();
                    if (!address.isLoopbackAddress() && ip != null && ip.matches("100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\.[0-9]{1,3}\\.[0-9]{1,3}")) return ip;
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private final class SwipeFrame extends FrameLayout {
        private float downX, downY;
        private boolean horizontalSwipe;
        SwipeFrame(Context context) { super(context); setBackgroundColor(BG); }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            // Keep horizontal tab gestures available while ScrollView still handles vertical drags.
            super.requestDisallowInterceptTouchEvent(false);
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getX(); downY = event.getY(); horizontalSwipe = false;
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - downX, dy = event.getY() - downY;
                if (Math.abs(dx) > dp(56) && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                    horizontalSwipe = true;
                    return true;
                }
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && horizontalSwipe) {
                float dx = event.getX() - downX;
                if (Math.abs(dx) > dp(56)) showPage(dx < 0 ? 1 : 0);
                horizontalSwipe = false;
                return true;
            }
            return true;
        }
    }
}
