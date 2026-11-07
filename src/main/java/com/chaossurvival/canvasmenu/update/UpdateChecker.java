package com.chaossurvival.canvasmenu.update;

import com.chaossurvival.canvasmenu.config.ModConfig;
import com.chaossurvival.canvasmenu.util.Async;
import com.chaossurvival.canvasmenu.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * GitHub Release 更新检查。
 *
 * <p><strong>只信任配置里固定的 {@code owner/repo}</strong>，运行时不接受改源；
 * 全部请求在虚拟线程异步执行，连接超时 5s、读超时 10s；
 * 任何网络 / 解析失败都返回 {@link Optional#empty()} 并记 debug 日志，
 * <strong>静默降级，不抛异常、不弹窗、不阻塞渲染线程</strong>。</p>
 */
public final class UpdateChecker {

    /** GitHub API 要求的 User-Agent。 */
    public static final String USER_AGENT = "CanvasMenu-Updater";
    /** 连接超时。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 单次请求超时。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private UpdateChecker() {
    }

    /** Release 资产描述。 */
    public record Asset(String name, String url, long size) {
    }

    /** Release 信息。 */
    public record ReleaseInfo(String tag, String name, String body, String publishedAt, List<Asset> assets) {

        /** @return 首个可安装的 jar 资产 */
        public Optional<Asset> jarAsset() {
            for (Asset asset : assets) {
                String lower = asset.name().toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".jar")
                        && !lower.contains("sources")
                        && !lower.contains("javadoc")
                        && !lower.contains("-dev")) {
                    return Optional.of(asset);
                }
            }
            return Optional.empty();
        }

        /** @return 首个 sha256 校验资产 */
        public Optional<Asset> sha256Asset() {
            for (Asset asset : assets) {
                if (asset.name().toLowerCase(java.util.Locale.ROOT).endsWith(".sha256")) {
                    return Optional.of(asset);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * 构造唯一可信的 API 地址。
     *
     * @param config 配置
     * @return API URL
     */
    public static String buildUrl(ModConfig config) {
        // 强制使用出厂固定源，忽略配置对象里可能被外部改写的值
        return "https://api.github.com/repos/" + ModConfig.TRUSTED_OWNER + "/"
                + ModConfig.TRUSTED_REPO + "/releases/latest";
    }

    /**
     * 异步检查更新。
     *
     * @param config       配置
     * @param localVersion 本地版本（来自 fabric.mod.json）
     * @return 有更新时返回 ReleaseInfo，否则空
     */
    public static CompletableFuture<Optional<ReleaseInfo>> check(ModConfig config, String localVersion) {
        return Async.supply(() -> checkSync(config, localVersion));
    }

    private static Optional<ReleaseInfo> checkSync(ModConfig config, String localVersion) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(buildUrl(config)))
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
            ReleaseInfo release = parseRelease(response.body());
            Optional<SemVer> remote = SemVer.parse(release.tag());
            Optional<SemVer> local = SemVer.parse(localVersion);
            if (remote.isEmpty()) {
                return Optional.empty();
            }
            if (config.ignorePrerelease() && remote.get().isPrerelease()) {
                return Optional.empty();
            }
            if (local.isPresent() && !remote.get().isNewerThan(local.get())) {
                return Optional.empty();
            }
            return release.jarAsset().isPresent() ? Optional.of(release) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            Log.debug("Update check failed, silent fallback: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * 解析 GitHub Release JSON。
     *
     * @param json API 响应
     * @return ReleaseInfo
     */
    public static ReleaseInfo parseRelease(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Asset> assets = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("assets");
        if (array != null) {
            for (JsonElement element : array) {
                JsonObject asset = element.getAsJsonObject();
                assets.add(new Asset(
                        text(asset, "name"),
                        text(asset, "browser_download_url"),
                        number(asset, "size")));
            }
        }
        return new ReleaseInfo(
                text(root, "tag_name"),
                text(root, "name"),
                text(root, "body"),
                text(root, "published_at"),
                List.copyOf(assets));
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static long number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
