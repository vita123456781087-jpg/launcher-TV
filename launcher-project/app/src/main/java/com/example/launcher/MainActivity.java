package com.example.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    // ===== НАСТРОЙКИ ВНЕШНЕГО ВИДА =====
    private static final int COLUMNS = 6;               // колонок в сетке
    private static final int ICON_DP = 64;              // размер иконки
    private static final int CELL_HEIGHT_DP = 116;      // высота ячейки
    private static final int COLOR_TOP = 0xFF101820;    // градиент фона: верх
    private static final int COLOR_BOTTOM = 0xFF1F2F46; // градиент фона: низ
    private static final int COLOR_ACCENT = 0xFF00C8FF; // рамка выбранного приложения
    // ====================================

    private static class App {
        String label;
        Drawable icon;
        Intent intent;
    }

    private final List<App> apps = new ArrayList<App>();
    private GridView grid;
    private AppAdapter adapter;
    private float density;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        buildUi();
        loadApps();
    }

    private int dp(int v) { return (int) (v * density + 0.5f); }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_TOP, COLOR_BOTTOM});
        root.setBackground(bg);

        // Часы и дата (системный TextClock, сам обновляется раз в минуту)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(48), dp(28), dp(48), 0);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(44);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        top.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(0xAAFFFFFF);
        date.setTextSize(18);
        top.addView(date);

        root.addView(top, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Сетка приложений
        grid = new GridView(this);
        grid.setNumColumns(COLUMNS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(8));
        grid.setPadding(dp(40), 0, dp(40), dp(24));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(makeSelector());
        grid.setFocusable(true);
        grid.setCacheColorHint(Color.TRANSPARENT);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                try {
                    startActivity(apps.get(pos).intent);
                } catch (Exception ignored) { }
            }
        });

        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        gp.topMargin = dp(130);
        root.addView(grid, gp);

        setContentView(root);
    }

    private Drawable makeSelector() {
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x33FFFFFF);
        sel.setCornerRadius(dp(14));
        sel.setStroke(dp(2), COLOR_ACCENT);
        return sel;
    }

    private void loadApps() {
        // Список собирается в фоне один раз и при установке/удалении приложений
        new Thread(new Runnable() {
            @Override public void run() {
                final List<App> result = queryApps();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        apps.clear();
                        apps.addAll(result);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private List<App> queryApps() {
        PackageManager pm = getPackageManager();
        List<App> out = new ArrayList<App>();
        Set<String> seen = new HashSet<String>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};

        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                App a = new App();
                a.label = String.valueOf(ri.loadLabel(pm));
                a.icon = ri.loadIcon(pm);
                a.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                out.add(a);
            }
        }
        final Collator col = Collator.getInstance();
        Collections.sort(out, new Comparator<App>() {
            @Override public int compare(App x, App y) { return col.compare(x.label, y.label); }
        });
        return out;
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            Holder h;
            if (v == null) {
                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(6), dp(8), dp(6), dp(8));
                cell.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(CELL_HEIGHT_DP)));

                ImageView iv = new ImageView(MainActivity.this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(iv, new LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)));

                TextView tv = new TextView(MainActivity.this);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(14);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                tp.topMargin = dp(8);
                cell.addView(tv, tp);

                h = new Holder();
                h.icon = iv;
                h.label = tv;
                cell.setTag(h);
                v = cell;
            } else {
                h = (Holder) v.getTag();
            }
            App a = apps.get(pos);
            h.icon.setImageDrawable(a.icon);
            h.label.setText(a.label);
            return v;
        }
    }

    private static class Holder {
        ImageView icon;
        TextView label;
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Нажатие Home возвращает в начало списка
        if (grid != null) grid.setSelection(0);
    }

    @Override
    public void onBackPressed() {
        // На главном экране лаунчера «Назад» ничего не делает
    }
}
