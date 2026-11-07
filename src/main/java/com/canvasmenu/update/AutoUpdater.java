package com.canvasmenu.update;

import com.chaossurvival.canvasmenu.util.Async;
import com.chaossurvival.canvasmenu.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * <strong>GitHub Release 自动更新器 —— 完整实现</strong>。
 *
 * <p>整条链路：启动时异步检查 → SemVer 比对 → 设置页提示 → 虚拟线程下载
 * → {@code mods/.canvasmenu-update/} 暂存 → {@code update.lock} 防重复
 * → 退出阶段替换 → 下次启动生效。</p>
 *
 * <pre>
 *   onInitializeClient
 *     └─> checkOnStartup()  [虚拟线程]
 *           GET /repos/ChaosSurvivalProject/CanvasMenu/releases/latest
 *           └─> compare(tag, localVersion) ─> AVAILABLE / UP_TO_DATE
 *                 └─> SettingsScreen「下载并重启」
 *                       └─> downloadAndSchedule()  [虚拟线程]
 *                             sha256 校验 → .part → 原子 move
 *                             → mods/.canvasmenu-update/canvas-menu-x.y.z.jar
 *                             → 写 update.lock（CREATE_NEW 原子，防重复）
 *                                   └─> CLIENT_STOPPING / JVM shutdown
 *                                         → move 到 mods/、删旧 jar、删 lock
 * </pre>
 *
 * <p><strong>四条硬约束</strong>：</p>
 * <ol>
 *   <li><strong>不阻塞主线程</strong>：所有网络与磁盘 IO 都经 {@link Async} 的虚拟线程，
 *       结果只写 {@code volatile} 字段，渲染线程只读；</li>
 *   <li><strong>失败静默降级</strong>：网络错误 / HTTP 非 200 / JSON 坏数据 / 校验失败
 *       一律返回空或把状态置为 {@code FAILED} 且清空提示文案，<strong>不弹错误、不打断游戏</strong>，
 *       设置页始终保留「检查更新」重试入口；</li>
 *   <li><strong>不重复下载</strong>：{@code update.lock} 用 {@code CREATE_NEW} 原子创建，
 *       已存在时直接跳过；</li>
 *   <li><strong>绝不热替换运行中的 jar</strong>：本类<strong>不触碰 ClassLoader、不重新加载类、
 *       不在游戏运行期间改写当前被加载的 jar 内容</strong>。只在退出阶段做纯文件层的
 *       「移动 + 删除」，新 jar 直到<strong>下次启动</strong>才会被 Fabric Loader 读取。</li>
 * </ol>
 *
 * <p>更新源固定为出厂常量，运行时不提供改源入口（防止本地配置投毒）。
 * 令牌只在开发者本地发版时使用，运行期检查与下载均为<strong>匿名只读</strong>请求，
 * 不需要、也不会读取任何凭据。</p>
 */
public final class AutoUpdater {

    /** 唯一的、可信的更新源（出厂固定）。 */
    public static final String OWNER = "ChaosSurvivalProject";
    /** @see #OWNER */
    public static final String REPO = "CanvasMenu";
    /** GitHub Releases API 地址。 */
    public static final String API_URL =
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

    /** 下载暂存目录（位于 {@code mods/} 下）。 */
    public static final String STAGING_DIR = ".canvasmenu-update";
    /** 更新锁文件名。 */
    public static final String LOCK_FILE = "update.lock";
    /** 旧 jar 匹配模式。 */
    private static final String OLD_JAR_GLOB = "canvas-menu-*.jar";
    /** GitHub API 要求的 User-Agent。 */
    private static final String USER_AGENT = "CanvasMenu-AutoUpdater";

    private static final Path MODS_DIR = Path.of("mods");
    private static final Path STAGING = MODS_DIR.resolve(STAGING_DIR);
    private static final Path LOCK = MODS_DIR.resolve(LOCK_FILE);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(90);
    private static final int BUFFER_SIZE = 8192;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 更新器状态。 */
    public enum State {
        /** 尚未检查。 */
        IDLE,
        /** 正在检查。 */
        CHECKING,
        /** 已是最新版本。 */
        UP_TO_DATE,
        /** 发现可用更新。 */
        AVAILABLE,
        /** 正在下载。 */
        DOWNLOADING,
        /** 已下载并写入 lock，等待重启生效。 */
        SCHEDULED,
        /** 失败（静默，不弹窗）。 */
        FAILED
    }

    /** Release 资产。 */
    public record Asset(String name, String url, long size) {
    }

    /** 一条可安装的 Release。 */
    public record ReleaseInfo(String tag, String name, String body, String publishedAt,
                              Asset jar, Asset sha256) {
    }

    private static volatile State state = State.IDLE;
    private static volatile ReleaseInfo available;
    private static volatile int progressPercent;
    private static volatile String message = "";
    private static volatile long checkId;
    private static volatile boolean checking;
    private static volatile boolean downloading;
    private static volatile boolean hookRegistered;

    private AutoUpdater() {
    }

    // ------------------------------------------------------------------
    // 检查
    // ------------------------------------------------------------------

    /**
     * 启动时检查（幂等；重复调用在本次检查结束前被忽略）。
     */
    public static void checkOnStartup() {
        runCheck();
    }

    /**
     * 设置页手动「检查更新」。
     */
    public static void checkAgain() {
        runCheck();
    }

    private static synchronized void runCheck() {
        if (checking) {
            return;
        }
        checking = true;
        state = State.CHECKING;
        message = "";
        // 虚拟线程执行网络请求；whenComplete 同样在虚拟线程，只写 volatile 字段
        Async.supply(AutoUpdater::fetchLatest).whenComplete((result, error) -> {
            checking = false;
            checkId++;
            ReleaseInfo found = (error == null && result != null) ? result.orElse(null) : null;
            available = found;
            if (found != null) {
                state = State.AVAILABLE;
                message = "发现新版本 " + found.tag() + "，可一键下载";
            } else {
                state = State.UP_TO_DATE;
                message = "已是最新版本 " + localVersion();
            }
            if (error != null) {
                Log.debug("Update check failed, silent fallback: {}", error.toString());
            }
        });
    }

    /**
     * 同步执行：请求 Releases API → 解析 → 版本比对。任何异常都返回空。
     */
    private static Optional<ReleaseInfo> fetchLatest() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                Log.debug("Update check returned HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            List<Asset> assets = parseAssets(root.getAsJsonArray("assets"));
            Asset jar = pickJar(assets);
            if (jar == null) {
                return Optional.empty();
            }
            Asset sha = pickSha256(assets);
            String tag = text(root, "tag_name");
            if (!isNewer(tag, localVersion())) {
                return Optional.empty();
            }
            return Optional.of(new ReleaseInfo(tag,
                    text(root, "name"), text(root, "body"),
                    text(root, "published_at"), jar, sha));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            Log.debug("Update check failed, silent fallback: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * 版本比较：支持 {@code v} 前缀 / 三段数字 / 预发布后缀。
     * 预发布（含 {@code -}）一律忽略；本地无法解析时按 {@code 0.0.0} 处理。
     */
    static boolean isNewer(String remoteTag, String localVersion) {
        if (remoteTag == null || remoteTag.isBlank()) {
            return false;
        }
        String normalized = remoteTag.trim();
        if (normalized.contains("-")) {
            return false; // 忽略 alpha / beta / rc
        }
        int[] remote = parseVersion(normalized);
        int[] local = parseVersion(localVersion);
        for (int i = 0; i < 3; i++) {
            if (remote[i] != local[i]) {
                return remote[i] > local[i];
            }
        }
        return false;
    }

    private static int[] parseVersion(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' || c == '+' || c == ' ') {
                cut = i;
                break;
            }
        }
        String[] parts = s.substring(0, cut).split("\\.");
        int[] result = {0, 0, 0};
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    /** @return 本地版本（来自 {@code fabric.mod.json}） */
    public static String localVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("canvasmenu")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("0.0.0");
        } catch (RuntimeException e) {
            return "0.0.0";
        }
    }

    // ------------------------------------------------------------------
    // 下载 + 锁
    // ------------------------------------------------------------------

    /**
     * 一键下载并调度重启替换。全程虚拟线程，主线程只读 {@link #progressPercent()}。
     *
     * <p>已存在 {@code update.lock} 时直接返回（防止重复下载）；下载写入
     * {@code .part} 临时文件，长度与可选 SHA-256 全部校验通过后才原子改名为最终 jar。</p>
     */
    public static synchronized void downloadAndSchedule() {
        ReleaseInfo info = available;
        if (info == null || info.jar() == null || downloading
                || state == State.SCHEDULED || Files.isRegularFile(LOCK)) {
            return;
        }
        downloading = true;
        state = State.DOWNLOADING;
        progressPercent = 0;
        message = "";

        Async.supply(() -> {
            try {
                // 1) 可选的 SHA-256 摘要（失败返回 null，降级为长度校验）
                String expected = info.sha256() == null ? null : fetchText(info.sha256().url());
                if (expected != null) {
                    String[] tokens = expected.trim().split("\\s+");
                    expected = tokens.length == 0 ? null : tokens[0];
                }

                // 2) 下载到暂存目录（.part → 原子 move）
                Files.createDirectories(STAGING);
                Path destination = STAGING.resolve(safeFileName(info.jar().name()));
                Path part = destination.resolveSibling(destination.getFileName() + ".part");
                Files.deleteIfExists(part);
                downloadFile(info.jar().url(), part, info.jar().size());

                // 3) 校验
                if (expected != null && !expected.isBlank() && !sha256Hex(part).equalsIgnoreCase(expected)) {
                    Files.deleteIfExists(part);
                    throw new IOException("SHA-256 mismatch, download discarded");
                }
                Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
                return destination;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((destination, error) -> {
            downloading = false;
            if (error != null || destination == null) {
                // 静默降级：不设置报错文案、不弹窗；设置页仍可再次点击重试
                state = State.FAILED;
                message = "";
                Log.debug("Update download failed, silent fallback: {}",
                        error == null ? "no destination" : error.toString());
                return;
            }
            if (writeLock(destination, info)) {
                state = State.SCHEDULED;
                message = "更新已下载，重启游戏后生效";
                progressPercent = 100;
            } else {
                state = State.FAILED;
                message = "";
            }
        });
    }

    private static void downloadFile(String url, Path target, long expectedSize)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response = CLIENT.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Download failed with HTTP " + status);
        }
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long reference = expectedSize > 0L ? expectedSize : contentLength;

        long total = 0L;
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(target,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (reference > 0L) {
                    progressPercent = (int) Math.min(100L, total * 100L / reference);
                }
            }
        }
        long actual = Files.size(target);
        if (reference > 0L && actual != reference) {
            Files.deleteIfExists(target);
            throw new IOException("Size mismatch: expected " + reference + ", got " + actual);
        }
    }

    /** 读取远程文本（用于 {@code .sha256} 资产）；失败返回 {@code null}。 */
    private static String fetchText(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? response.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 原子创建 {@code update.lock}。{@code CREATE_NEW} 保证并发/重复调度时只有一个成功。
     */
    private static synchronized boolean writeLock(Path stagedJar, ReleaseInfo info) {
        String content = "{\"state\":\"pending\",\"newJar\":\"" + escape(stagedJar.toString())
                + "\",\"tag\":\"" + escape(info.tag()) + "\"}";
        try {
            Files.createDirectories(MODS_DIR);
            Files.writeString(LOCK, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            Log.debug("Cannot create update lock (duplicate schedule?): {}", e.toString());
            return false;
        }
        registerHook();
        Log.info("Update {} staged; it will be applied on next launch", info.tag());
        return true;
    }

    private static synchronized void registerHook() {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        // JVM 兜底 hook：非正常退出路径也能完成文件层替换
        Runtime.getRuntime().addShutdownHook(new Thread(AutoUpdater::performSwap,
                "canvasmenu-update-swap"));
    }

    // ------------------------------------------------------------------
    // 退出阶段替换（绝不热替换）
    // ------------------------------------------------------------------

    /**
     * Fabric {@code CLIENT_STOPPING} 事件调用。
     *
     * <p>只在退出阶段做<strong>纯文件层</strong>操作：把暂存 jar 移入 {@code mods/}、
     * 删掉旧 jar、删掉 lock。不触碰 ClassLoader、不重新加载任何类，
     * 因此严格意义上<strong>不是热替换</strong>；新版本在下次启动才被加载。</p>
     */
    public static void onClientStopping() {
        performSwap();
    }

    /**
     * @return 启动时可展示的挂起更新提示（有 lock 时非空）
     */
    public static Optional<String> pendingMessage() {
        return Files.isRegularFile(LOCK)
                ? Optional.of("检测到挂起更新，重启游戏后生效")
                : Optional.empty();
    }

    private static synchronized void performSwap() {
        if (!Files.isRegularFile(LOCK)) {
            return;
        }
        try {
            String content = Files.readString(LOCK, StandardCharsets.UTF_8);
            String newJarPath = jsonString(content, "newJar");
            if (newJarPath == null) {
                return;
            }
            Path staged = Path.of(newJarPath);
            if (!Files.isRegularFile(staged)) {
                Log.debug("Staged update jar missing, will retry next launch");
                return;
            }
            Path target = MODS_DIR.resolve(staged.getFileName().toString());
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(MODS_DIR, OLD_JAR_GLOB)) {
                for (Path old : stream) {
                    if (!old.equals(target)) {
                        Files.deleteIfExists(old);
                    }
                }
            }
            Files.deleteIfExists(LOCK);
            Log.info("Update applied on exit: {}", target.getFileName());
        } catch (IOException | RuntimeException e) {
            // 失败保留 lock，下次启动再试；不弹错误
            Log.debug("Update swap failed, will retry next launch: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 状态读取（主线程安全）
    // ------------------------------------------------------------------

    /** @return 当前状态 */
    public static State state() {
        return state;
    }

    /** @return 最近一次检查到的可用更新，无则 {@code null} */
    public static ReleaseInfo available() {
        return available;
    }

    /** @return 下载进度百分比 {@code [0,100]} */
    public static int progressPercent() {
        return progressPercent;
    }

    /** @return 设置页状态文案（失败时为空，实现「静默降级」） */
    public static String message() {
        return message;
    }

    /** @return 已完成检查次数（供 UI 判断结果是否刷新） */
    public static long checkId() {
        return checkId;
    }

    /** @return 是否正在检查 */
    public static boolean isChecking() {
        return checking;
    }

    /** @return 是否正在下载 */
    public static boolean isDownloading() {
        return downloading;
    }

    /** @return 是否已有挂起的重启替换 */
    public static boolean hasPendingSwap() {
        return Files.isRegularFile(LOCK);
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    private static List<Asset> parseAssets(JsonArray array) {
        List<Asset> assets = new ArrayList<>();
        if (array == null) {
            return assets;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            assets.add(new Asset(text(o, "name"), text(o, "browser_download_url"),
                    number(o, "size")));
        }
        return assets;
    }

    private static Asset pickJar(List<Asset> assets) {
        for (Asset asset : assets) {
            String lower = asset.name().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jar") && !lower.contains("sources")
                    && !lower.contains("javadoc") && !lower.contains("-dev")) {
                return asset;
            }
        }
        return null;
    }

    private static Asset pickSha256(List<Asset> assets) {
        for (Asset asset : assets) {
            if (asset.name().toLowerCase(Locale.ROOT).endsWith(".sha256")) {
                return asset;
            }
        }
        return null;
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    /** 只保留文件名部分，防止 Release 资产名里出现路径穿越。 */
    private static String safeFileName(String name) {
        String clean = name == null ? "canvas-menu-update.jar" : name.replace("\\", "/");
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) {
            clean = clean.substring(slash + 1);
        }
        return clean.matches("[A-Za-z0-9._-]+") ? clean : "canvas-menu-update.jar";
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return e.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String jsonString(String json, String key) {
        try {
            JsonElement e = JsonParser.parseString(json).getAsJsonObject().get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
