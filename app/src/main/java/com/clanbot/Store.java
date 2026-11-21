package com.clanbot;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local storage: settings (prefs) + per-server memory, logs, blacklist, snapshots (SQLite). */
public class Store extends SQLiteOpenHelper {
    public static Store I;
    static SharedPreferences P;

    public static synchronized void init(Context c) {
        if (I == null) {
            Context a = c.getApplicationContext();
            I = new Store(a);
            P = a.getSharedPreferences("cfg", 0);
        }
    }

    Store(Context c) { super(c, "clanbot.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY AUTOINCREMENT,guild TEXT,text TEXT,src TEXT,ts INTEGER)");
        d.execSQL("CREATE INDEX memg ON memory(guild)");
        d.execSQL("CREATE TABLE logs(id INTEGER PRIMARY KEY AUTOINCREMENT,guild TEXT,channel TEXT,msg TEXT,author TEXT,aname TEXT,content TEXT,ts INTEGER,deleted INTEGER DEFAULT 0)");
        d.execSQL("CREATE INDEX logc ON logs(guild,channel)");
        d.execSQL("CREATE INDEX logm ON logs(msg)");
        d.execSQL("CREATE TABLE blk(kind TEXT,id TEXT,PRIMARY KEY(kind,id))");
        d.execSQL("CREATE TABLE inactive(guild TEXT,channel TEXT,PRIMARY KEY(guild,channel))");
        d.execSQL("CREATE TABLE snaps(id INTEGER PRIMARY KEY AUTOINCREMENT,guild TEXT,ts INTEGER,label TEXT,json TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase d, int a, int b) { }

    // ---------- prefs ----------
    public static String get(String k, String d) { return P == null ? d : P.getString(k, d); }
    public static void set(String k, String v) { if (P != null) P.edit().putString(k, v).apply(); }
    public static void del(String k) { if (P != null) P.edit().remove(k).apply(); }
    public static long inc(String k) {
        long v = P.getLong(k, 0) + 1;
        P.edit().putLong(k, v).apply();
        return v;
    }
    public static long getL(String k) { return P == null ? 0 : P.getLong(k, 0); }

    List<String[]> q(String sql, String... a) {
        List<String[]> r = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, a);
        try {
            int n = c.getColumnCount();
            while (c.moveToNext()) {
                String[] row = new String[n];
                for (int i = 0; i < n; i++) row[i] = c.isNull(i) ? "" : c.getString(i);
                r.add(row);
            }
        } finally { c.close(); }
        return r;
    }

    // ---------- memory ----------
    public long addMemory(String g, String t, String src) {
        ContentValues v = new ContentValues();
        v.put("guild", g); v.put("text", t); v.put("src", src); v.put("ts", System.currentTimeMillis());
        long id = getWritableDatabase().insert("memory", null, v);
        getWritableDatabase().execSQL("DELETE FROM memory WHERE guild=? AND id NOT IN (SELECT id FROM memory WHERE guild=? ORDER BY id DESC LIMIT 80)", new Object[]{g, g});
        return id;
    }
    public List<String[]> memories(String g) { return q("SELECT id,text,src,ts FROM memory WHERE guild=? ORDER BY id", g); }
    public List<String[]> searchMemory(String g, String s) { return q("SELECT id,text,src,ts FROM memory WHERE guild=? AND text LIKE ? ORDER BY id", g, "%" + s + "%"); }
    public boolean delMemory(String g, long id) { return getWritableDatabase().delete("memory", "guild=? AND id=?", new String[]{g, "" + id}) > 0; }
    public void clearMemory(String g) { getWritableDatabase().delete("memory", "guild=?", new String[]{g}); }

    // ---------- logs ----------
    int logCount;
    public void addLog(String g, String ch, String msg, String au, String an, String content, long ts) {
        ContentValues v = new ContentValues();
        v.put("guild", g); v.put("channel", ch); v.put("msg", msg); v.put("author", au); v.put("aname", an); v.put("content", content); v.put("ts", ts);
        getWritableDatabase().insert("logs", null, v);
        if (++logCount % 500 == 0) getWritableDatabase().execSQL("DELETE FROM logs WHERE id < (SELECT MAX(id) FROM logs) - 60000");
    }
    public void markDeleted(String msg) {
        ContentValues v = new ContentValues();
        v.put("deleted", 1);
        getWritableDatabase().update("logs", v, "msg=?", new String[]{msg});
    }
    public void updateLog(String msg, String content) {
        ContentValues v = new ContentValues();
        v.put("content", content);
        getWritableDatabase().update("logs", v, "msg=?", new String[]{msg});
    }
    /** rows: msg, author, aname, content, ts, deleted (oldest first) */
    public List<String[]> logs(String g, String ch, int limit, String search) {
        String sql = "SELECT msg,author,aname,content,ts,deleted FROM logs WHERE guild=? AND channel=?";
        List<String[]> r;
        if (search != null && !search.isEmpty()) r = q(sql + " AND content LIKE ? ORDER BY id DESC LIMIT " + limit, g, ch, "%" + search + "%");
        else r = q(sql + " ORDER BY id DESC LIMIT " + limit, g, ch);
        Collections.reverse(r);
        return r;
    }
    /** rows: guild, channel, aname, content, ts */
    public List<String[]> recentAll(int limit) { return q("SELECT guild,channel,aname,content,ts FROM logs ORDER BY id DESC LIMIT " + limit); }

    // ---------- blacklist / inactive ----------
    public void blk(String kind, String id, boolean on) {
        if (on) {
            ContentValues v = new ContentValues();
            v.put("kind", kind); v.put("id", id);
            getWritableDatabase().insertWithOnConflict("blk", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } else getWritableDatabase().delete("blk", "kind=? AND id=?", new String[]{kind, id});
    }
    public boolean isBlk(String kind, String id) { return !q("SELECT 1 FROM blk WHERE kind=? AND id=?", kind, id).isEmpty(); }
    public List<String> blkList(String kind) {
        List<String> r = new ArrayList<>();
        for (String[] x : q("SELECT id FROM blk WHERE kind=?", kind)) r.add(x[0]);
        return r;
    }
    public void setInactive(String g, String ch, boolean off) {
        if (off) {
            ContentValues v = new ContentValues();
            v.put("guild", g); v.put("channel", ch);
            getWritableDatabase().insertWithOnConflict("inactive", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } else getWritableDatabase().delete("inactive", "guild=? AND channel=?", new String[]{g, ch});
    }
    public boolean isInactive(String ch) { return !q("SELECT 1 FROM inactive WHERE channel=?", ch).isEmpty(); }
    public List<String> inactive(String g) {
        List<String> r = new ArrayList<>();
        for (String[] x : q("SELECT channel FROM inactive WHERE guild=?", g)) r.add(x[0]);
        return r;
    }

    // ---------- snapshots ----------
    public long addSnap(String g, String label, String json) {
        ContentValues v = new ContentValues();
        v.put("guild", g); v.put("ts", System.currentTimeMillis()); v.put("label", label); v.put("json", json);
        long id = getWritableDatabase().insert("snaps", null, v);
        getWritableDatabase().execSQL("DELETE FROM snaps WHERE guild=? AND id NOT IN (SELECT id FROM snaps WHERE guild=? ORDER BY id DESC LIMIT 15)", new Object[]{g, g});
        return id;
    }
    public List<String[]> snaps(String g) { return q("SELECT id,ts,label FROM snaps WHERE guild=? ORDER BY id DESC", g); }
    public String snapJson(String g, String id) {
        List<String[]> r = q("SELECT json FROM snaps WHERE guild=? AND id=?", g, id);
        return r.isEmpty() ? null : r.get(0)[0];
    }
}
