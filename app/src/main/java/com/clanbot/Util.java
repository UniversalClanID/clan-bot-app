package com.clanbot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Util {
    public static final int BG = 0xFF090B14, CARD = 0xFF141829, CARD2 = 0xFF1D2338, ACC = 0xFF7C5CFF, ACC2 = 0xFF22D3EE,
            TXT = 0xFFF1F5F9, SUB = 0xFF8B93A7, OK = 0xFF34D399, BAD = 0xFFF87171, WARN = 0xFFFBBF24;
    public static final Handler UI = new Handler(Looper.getMainLooper());

    // ---------- JSON helpers ----------
    public static String str(JSONObject o, String k) {
        if (o == null || !o.has(k) || o.isNull(k)) return "";
        return o.optString(k, "");
    }
    public static JSONObject jo(Object... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]); } catch (JSONException e) { }
        return o;
    }
    public static JSONArray ja(Object... v) {
        JSONArray a = new JSONArray();
        for (Object x : v) a.put(x);
        return a;
    }
    public static void put(JSONObject o, String k, Object v) {
        try { o.put(k, v); } catch (JSONException e) { }
    }
    public static JSONObject parse(String s) {
        try { return new JSONObject(s); } catch (Exception e) { return null; }
    }
    public static JSONArray parseArr(String s) {
        try { return new JSONArray(s); } catch (Exception e) { return null; }
    }
    public static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
    public static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
    public static String created(String id) {
        try {
            long ms = (Long.parseLong(id) >> 22) + 1420070400000L;
            return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(ms));
        } catch (Exception e) { return "?"; }
    }
    public static String hm(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }
    public static String dt(long ms) {
        return new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    // ---------- view helpers ----------
    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
    public static GradientDrawable rect(Context c, int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, r));
        return g;
    }
    public static GradientDrawable grad(Context c, float r, int... colors) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(c, r));
        return g;
    }
    public static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(rect(c, CARD, 18));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }
    public static Button btn(Context c, String s, boolean primary, View.OnClickListener cl) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(primary ? grad(c, 14, ACC, ACC2) : rect(c, CARD2, 14));
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setOnClickListener(cl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(c, 4);
        lp.rightMargin = dp(c, 6);
        b.setLayoutParams(lp);
        return b;
    }
    public static EditText edit(Context c, String hint, String val, boolean secret) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(SUB);
        e.setTextColor(TXT);
        e.setText(val);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setBackground(rect(c, CARD2, 12));
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 4);
        lp.bottomMargin = dp(c, 8);
        e.setLayoutParams(lp);
        return e;
    }
    public static void toast(final Context c, final String s) {
        UI.post(new Runnable() { public void run() { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); } });
    }
    public static void circle(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View x, Outline o) { o.setOval(0, 0, x.getWidth(), x.getHeight()); }
        });
        v.setClipToOutline(true);
    }
    public static void rounded(View v, final float rPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View x, Outline o) { o.setRoundRect(0, 0, x.getWidth(), x.getHeight(), rPx); }
        });
        v.setClipToOutline(true);
    }

    // ---------- image loader ----------
    static final LruCache<String, Bitmap> CACHE = new LruCache<>(80);
    public static void img(final ImageView iv, final String url) {
        iv.setTag(url);
        if (url == null || url.isEmpty()) { iv.setImageDrawable(null); return; }
        Bitmap b = CACHE.get(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        iv.setImageDrawable(null);
        Discord.http.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { }
            @Override public void onResponse(Call call, Response r) throws IOException {
                try (Response rr = r) {
                    byte[] by = rr.body().bytes();
                    final Bitmap bm = BitmapFactory.decodeByteArray(by, 0, by.length);
                    if (bm != null) {
                        CACHE.put(url, bm);
                        UI.post(new Runnable() { public void run() { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); } });
                    }
                }
            }
        });
    }
}
