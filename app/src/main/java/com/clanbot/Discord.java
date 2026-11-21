package com.clanbot;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

/** Thin Discord REST client with 429 handling. */
public class Discord {
    public static final String API = "https://discord.com/api/v10";
    public static final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build();

    public static class Res {
        public int code;
        public String body = "";
        public boolean ok() { return code >= 200 && code < 300; }
        public JSONObject obj() { return Util.parse(body); }
        public JSONArray arr() { return Util.parseArr(body); }
        public String err() { return code + ": " + Util.cut(body, 300); }
    }

    static Res exec(Request req) {
        Res r = new Res();
        try (Response resp = http.newCall(req).execute()) {
            r.code = resp.code();
            r.body = resp.body() != null ? resp.body().string() : "";
        } catch (Exception e) {
            r.code = -1;
            r.body = String.valueOf(e.getMessage());
        }
        return r;
    }

    static Request.Builder base(String path, String reason) {
        Request.Builder rb = new Request.Builder().url(API + path)
                .header("Authorization", "Bot " + Store.get("bot_token", "").trim())
                .header("User-Agent", "DiscordBot (clanbot, 1.0)");
        if (reason != null && !reason.isEmpty()) {
            try { rb.header("X-Audit-Log-Reason", URLEncoder.encode(reason, "UTF-8").replace("+", "%20")); } catch (Exception e) { }
        }
        return rb;
    }

    public static Res callRaw(String method, String path, String body, String reason) {
        Res r = new Res();
        for (int i = 0; i < 4; i++) {
            Request.Builder rb = base(path, reason);
            boolean needsBody = method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
            RequestBody rbody = null;
            if (body != null) rbody = RequestBody.create(body, MediaType.parse("application/json"));
            else if (needsBody) rbody = RequestBody.create("", null);
            rb.method(method, rbody);
            r = exec(rb.build());
            if (r.code == 429) {
                long wait = 1500;
                JSONObject o = r.obj();
                if (o != null) wait = (long) (o.optDouble("retry_after", 1.5) * 1000) + 200;
                if (wait > 20000) return r;
                try { Thread.sleep(wait); } catch (InterruptedException e) { return r; }
                continue;
            }
            return r;
        }
        return r;
    }

    public static Res call(String method, String path, JSONObject body, String reason) {
        return callRaw(method, path, body == null ? null : body.toString(), reason);
    }
    public static Res get(String p) { return call("GET", p, null, null); }
    public static Res post(String p, JSONObject b) { return call("POST", p, b, null); }
    public static Res patch(String p, JSONObject b, String reason) { return call("PATCH", p, b, reason); }
    public static Res put(String p) { return call("PUT", p, null, null); }
    public static Res delete(String p, String reason) { return call("DELETE", p, null, reason); }
    public static Res sendMsg(String ch, JSONObject payload) { return post("/channels/" + ch + "/messages", payload); }

    public static Res sendFile(String ch, JSONObject payload, String filename, byte[] data) {
        MultipartBody mb = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", payload.toString())
                .addFormDataPart("files[0]", filename, RequestBody.create(data, MediaType.parse("application/octet-stream")))
                .build();
        Request req = base("/channels/" + ch + "/messages", null).post(mb).build();
        return exec(req);
    }
}
