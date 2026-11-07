package com.chaossurvival.canvasmenu.theme;

import com.chaossurvival.canvasmenu.util.Log;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON → {@link Theme} 加载器。
 *
 * <p>逐字段解析，<strong>任何缺字段、坏色值、解析异常都回退到默认主题对应值</strong>，
 * 绝不因为一份主题文件让游戏崩溃。支持 {@code #RRGGBB} 与 {@code #AARRGGBB} 两种色值。</p>
 */
public final class ThemeLoader {

    private static final String RESOURCE_ROOT = "/assets/canvasmenu/themes/";

    private ThemeLoader() {
    }

    /**
     * 解析主题 JSON 字符串。
     *
     * @param json 主题 JSON
     * @return 解析后的主题；失败时返回默认主题
     */
    public static Theme parse(String json) {
        Theme d = Theme.defaults();
        if (json == null || json.isBlank()) {
            return d;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return d;
            }
            JsonObject root = element.getAsJsonObject();
            String name = string(root, "name", d.name());
            int[] radius = ints(root, "radius", d.radius());
            int[] gap = ints(root, "gap", d.gap());
            int[] elevation = colors(root, "elevation", d.elevation());
            JsonObject bg = object(root, "bg");
            JsonObject text = object(root, "text");
            JsonObject accent = object(root, "accent");
            JsonObject font = object(root, "font");
            JsonObject motion = object(root, "motion");

            return new Theme(
                    name,
                    radius,
                    gap,
                    elevation,
                    new Theme.Bg(
                            color(bg, "base", d.bg().base()),
                            color(bg, "panel", d.bg().panel()),
                            color(bg, "float", d.bg().floating())),
                    new Theme.Text(
                            color(text, "primary", d.text().primary()),
                            color(text, "secondary", d.text().secondary())),
                    new Theme.Accent(
                            color(accent, "main", d.accent().main()),
                            color(accent, "glow", d.accent().glow())),
                    new Theme.Fonts(
                            string(font, "display", d.font().display()),
                            string(font, "body", d.font().body())),
                    new Theme.Motion(
                            integer(motion, "fast", d.motion().fast()),
                            integer(motion, "normal", d.motion().normal()),
                            integer(motion, "slow", d.motion().slow()),
                            string(motion, "curve", d.motion().curve()))
            );
        } catch (RuntimeException e) {
            Log.warn("Invalid theme JSON, falling back to defaults: {}", e.toString());
            return d;
        }
    }

    /**
     * 从磁盘文件加载主题，文件不存在或不可读时回退内置预设。
     *
     * @param file 主题文件路径
     * @param fallbackName 回退用的预设名
     * @return 主题
     */
    public static Theme load(Path file, String fallbackName) {
        Theme fallback = Palette.byName(fallbackName);
        if (file == null || !Files.isRegularFile(file)) {
            return fallback;
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.warn("Cannot read theme file, using preset: {}", e.toString());
            return fallback;
        }
    }

    /**
     * 从 classpath 内置资源加载主题。
     *
     * @param name 主题名（不含扩展名），只允许字母数字与下划线
     * @return 主题；资源缺失时回退预设
     */
    public static Theme loadBuiltin(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]+")) {
            return Theme.defaults();
        }
        String path = RESOURCE_ROOT + name + ".json";
        try (InputStream in = ThemeLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                return Palette.byName(name);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[512];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                return parse(sb.toString());
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Cannot load builtin theme '{}', using preset: {}", name, e.toString());
            return Palette.byName(name);
        }
    }

    /**
     * 把主题导出为 JSON（供布局编辑器 / 社区主题功能使用）。
     *
     * @param theme 主题
     * @return 格式化后的 JSON 字符串
     */
    public static String serialize(Theme theme) {
        JsonObject root = new JsonObject();
        root.addProperty("name", theme.name());
        root.add("radius", array(theme.radius()));
        root.add("gap", array(theme.gap()));
        JsonArray elevation = new JsonArray();
        for (int value : theme.elevation()) {
            elevation.add(hex(value));
        }
        root.add("elevation", elevation);
        root.add("bg", bg(theme));
        root.add("text", text(theme));
        root.add("accent", accent(theme));
        root.add("font", font(theme));
        root.add("motion", motion(theme));
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static JsonObject bg(Theme t) {
        JsonObject o = new JsonObject();
        o.addProperty("base", hex(t.bg().base()));
        o.addProperty("panel", hex(t.bg().panel()));
        o.addProperty("float", hex(t.bg().floating()));
        return o;
    }

    private static JsonObject text(Theme t) {
        JsonObject o = new JsonObject();
        o.addProperty("primary", hex(t.text().primary()));
        o.addProperty("secondary", hex(t.text().secondary()));
        return o;
    }

    private static JsonObject accent(Theme t) {
        JsonObject o = new JsonObject();
        o.addProperty("main", hex(t.accent().main()));
        o.addProperty("glow", hex(t.accent().glow()));
        return o;
    }

    private static JsonObject font(Theme t) {
        JsonObject o = new JsonObject();
        o.addProperty("display", t.font().display());
        o.addProperty("body", t.font().body());
        return o;
    }

    private static JsonObject motion(Theme t) {
        JsonObject o = new JsonObject();
        o.addProperty("fast", t.motion().fast());
        o.addProperty("normal", t.motion().normal());
        o.addProperty("slow", t.motion().slow());
        o.addProperty("curve", t.motion().curve());
        return o;
    }

    private static JsonArray array(int[] values) {
        JsonArray a = new JsonArray();
        for (int v : values) {
            a.add(v);
        }
        return a;
    }

    private static String hex(int argb) {
        if ((argb >>> 24) == 0xFF) {
            return String.format("#%06X", argb & 0x00FFFFFF);
        }
        return String.format("#%08X", argb);
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement e = root.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int color(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        return parseColor(e.getAsString(), fallback);
    }

    private static int[] ints(JsonObject root, String key, int[] fallback) {
        JsonElement e = root.get(key);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) {
            return fallback;
        }
        JsonArray a = e.getAsJsonArray();
        int[] result = new int[a.size()];
        for (int i = 0; i < a.size(); i++) {
            try {
                result[i] = a.get(i).getAsInt();
            } catch (RuntimeException ignored) {
                result[i] = fallback[Math.min(i, fallback.length - 1)];
            }
        }
        return result;
    }

    private static int[] colors(JsonObject root, String key, int[] fallback) {
        JsonElement e = root.get(key);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) {
            return fallback;
        }
        JsonArray a = e.getAsJsonArray();
        int[] result = new int[a.size()];
        for (int i = 0; i < a.size(); i++) {
            int fb = fallback[Math.min(i, fallback.length - 1)];
            try {
                result[i] = parseColor(a.get(i).getAsString(), fb);
            } catch (RuntimeException ignored) {
                result[i] = fb;
            }
        }
        return result;
    }

    /**
     * 解析 {@code #RRGGBB} / {@code #AARRGGBB} 色值。
     *
     * @param value    原始字符串
     * @param fallback 解析失败时的回退色
     * @return ARGB 整数
     */
    public static int parseColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String s = value.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            return switch (s.length()) {
                case 6 -> 0xFF000000 | (int) (Long.parseLong(s, 16) & 0x00FFFFFFL);
                case 8 -> (int) Long.parseLong(s, 16);
                default -> fallback;
            };
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
