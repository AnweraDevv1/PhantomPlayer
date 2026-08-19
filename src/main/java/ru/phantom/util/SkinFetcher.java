package ru.phantom.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Загружает текстуры скина по нику через публичные API Mojang.
 * Все вызовы обязаны выполняться АСИНХРОННО.
 */
public final class SkinFetcher {

    private static final Map<String, Skin> CACHE = new HashMap<>();

    public record Skin(String value, String signature) {
    }

    private SkinFetcher() {
    }

    /** Возвращает скин по нику игрока или null, если не удалось получить. */
    public static Skin fetch(String name) {
        String key = name.toLowerCase();
        Skin cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            UUID uuid = fetchUuid(name);
            if (uuid == null) {
                return null;
            }
            Skin skin = fetchByUuid(uuid);
            if (skin != null) {
                CACHE.put(key, skin);
            }
            return skin;
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID fetchUuid(String name) throws Exception {
        JsonObject json = get("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (json == null || !json.has("id")) {
            return null;
        }
        String raw = json.get("id").getAsString();
        return UUID.fromString(raw.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }

    private static Skin fetchByUuid(UUID uuid) throws Exception {
        JsonObject json = get("https://sessionserver.mojang.com/session/minecraft/profile/"
                + uuid.toString().replace("-", "") + "?unsigned=false");
        if (json == null || !json.has("properties")) {
            return null;
        }
        for (JsonElement el : json.getAsJsonArray("properties")) {
            JsonObject prop = el.getAsJsonObject();
            if ("textures".equals(prop.get("name").getAsString())) {
                String value = prop.get("value").getAsString();
                String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                return new Skin(value, signature);
            }
        }
        return null;
    }

    private static JsonObject get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "PhantomPlayer/1.0");
        if (conn.getResponseCode() != 200) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } finally {
            conn.disconnect();
        }
    }
}
