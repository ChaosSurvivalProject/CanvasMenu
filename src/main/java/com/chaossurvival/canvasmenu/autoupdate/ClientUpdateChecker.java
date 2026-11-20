package com.chaossurvival.canvasmenu.autoupdate;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Calendar;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Fabric 客户端更新检查（@Environment(CLIENT)）：启动自检 + 每周四 12:00 + 下载校验 + 提示重启。
 *
 * <p>安全红线：不内置 Token；只走 HTTPS；元数据直连固定地址；不静默替换运行中的 mod jar，
 * 下载到 mods/.update/，校验通过后提示玩家重启生效；跨 minor/major 只提示不自动下载。
 */
@Environment(EnvType.CLIENT)
public final class ClientUpdateChecker {

    private final String manifestUrl;
    private final String currentVersion;
    private final Path modsDir;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ClientUpdateChecker(String manifestUrl, String currentVersion, Path modsDir) {
        this.manifestUrl = manifestUrl;
        this.currentVersion = currentVersion;
        this.modsDir = modsDir;
    }

    /** 便捷构造：从 FabricLoader 获取 mods 目录。 */
    public static ClientUpdateChecker fromMods(String manifestUrl, String currentVersion) {
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        return new ClientUpdateChecker(manifestUrl, currentVersion, mods);
    }

    /** 在 onInitializeClient 调用：自检一次 + 注册周四 12:00 定时任务。 */
    public void start() {
        checkOnce();
        scheduleWeeklyThursdayNoon();
    }

    /** 计算下一次周四 12:00（Asia/Shanghai）的毫秒延迟，周期 7 天。 */
    private void scheduleWeeklyThursdayNoon() {
        Calendar now = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
        next.set(Calendar.HOUR_OF_DAY, 12);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.WEEK_OF_YEAR, 1);
        long delay = next.getTimeInMillis() - now.getTimeInMillis();
        long week = 7L * 24 * 60 * 60 * 1000;
        new Timer("canvasmenu-update", true).scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { checkOnce(); }
        }, delay, week);
    }

    /** 检查一次：拉 manifest -> 门禁 -> 下载校验 -> 提示。 */
    public void checkOnce() {
        try {
            if (!manifestUrl.startsWith("https://")) {
                log("[CanvasMenu] 更新源非 HTTPS，已跳过检查。");
                return;
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(manifestUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "CanvasMenu-UpdateGate/1.0")
                    .GET().build();
            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();

            Map<String, Object> root = Json.asMap(Json.parse(body));
            List<Object> channels = Json.asList(root.get("channels"));
            Map<String, Object> ch = null;
            for (Object o : channels) {
                Map<String, Object> c = Json.asMap(o);
                if ("stable".equals(Json.str(c, "id"))) { ch = c; break; }
            }
            if (ch == null) return;
            Map<String, Object> target = Json.asMap(ch.get("target"));
            String version = Json.str(target, "version");
            String minecraft = Json.str(target, "minecraft") == null ? "1.21.11" : Json.str(target, "minecraft");

            // 门禁：仅同 major.minor 内允许更新；跨 minor/major 只提示，不自动下载。
            if (!sameMinor(currentVersion, version) || !sameMinor(minecraft, "1.21.11")) {
                log("[CanvasMenu] 新版本 " + version + " 需要不同 MC/大版本，请手动更新（不会自动跨版本）。 / "
                        + "New version targets a different Minecraft version; update manually.");
                return;
            }
            if (!greater(version, currentVersion)) return; // 已是最新或本地更高

            List<Object> assets = Json.asList(ch.get("assets"));
            Map<String, Object> asset = Json.asMap(assets.get(0));
            String url = Json.str(asset, "downloadUrl");
            String sha = Json.str(asset, "sha256");
            String fileName = Json.str(asset, "fileName");

            Path stageDir = modsDir.resolve(".update");
            Files.createDirectories(stageDir);
            Path part = stageDir.resolve(fileName + ".part");
            HttpResponse<Path> res = http.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(part));
            if (res.statusCode() / 100 != 2) {
                Files.deleteIfExists(part);
                throw new IllegalStateException("HTTP " + res.statusCode());
            }
            String actual = sha256(part);
            if (!actual.equalsIgnoreCase(sha)) {
                Files.deleteIfExists(part);
                log("[CanvasMenu] 更新包校验失败（SHA256_MISMATCH），已删除，稍后重试。 / "
                        + "Update checksum failed; removed and will retry.");
                return;
            }
            Path ready = stageDir.resolve(fileName);
            try {
                Files.move(part, ready, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException e) {
                Files.move(part, ready, StandardCopyOption.REPLACE_EXISTING);
            }
            log("[CanvasMenu] 新版本 " + version + " 已下载并校验，重启游戏后生效。 / "
                    + "New version " + version + " downloaded; restart the game to apply.");
        } catch (Exception e) {
            log("[CanvasMenu] 更新检查失败（NETWORK/SHA256），下次再说：" + e.getMessage());
        }
    }

    // ---- 版本工具（仅比较 major/minor/patch） ----
    private static int[] parse(String v) {
        String core = v.replaceAll("\\+.*", "").split("-")[0];
        String[] p = core.split("\\.");
        int[] r = new int[3];
        for (int i = 0; i < 3 && i < p.length; i++) {
            String d = p[i].replaceAll("\\D", "");
            r[i] = d.isEmpty() ? 0 : Integer.parseInt(d);
        }
        return r;
    }
    private static boolean sameMinor(String a, String b) {
        int[] x = parse(a), y = parse(b);
        return x[0] == y[0] && x[1] == y[1];
    }
    private static boolean greater(String a, String b) {
        int[] x = parse(a), y = parse(b);
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return x[i] > y[i];
        return false;
    }
    private static String sha256(Path f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}
