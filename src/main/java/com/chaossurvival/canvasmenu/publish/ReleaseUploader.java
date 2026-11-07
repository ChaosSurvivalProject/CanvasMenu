package com.chaossurvival.canvasmenu.publish;

import com.chaossurvival.canvasmenu.update.Downloader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Release 上传器（<strong>开发者本地用，非游戏运行时</strong>）。
 *
 * <p>流程：草稿 Release → 上传 jar + {@code .sha256} → 校验通过 → PATCH 正式发布。
 * 令牌只从命令行参数或环境变量注入，<strong>代码里不留任何默认值，也绝不打印令牌</strong>。</p>
 *
 * <p>由 Gradle 任务 {@code publishRelease} 调用：
 * {@code main(owner, repo, token, tag, changelog, jarPath)}。</p>
 */
public final class ReleaseUploader {

    /** GitHub REST API 根地址。 */
    private static final String API_BASE = "https://api.github.com";
    /** Release 资产上传地址模板。 */
    private static final String UPLOAD_BASE = "https://uploads.github.com/repos/%s/%s/releases/%s/assets";
    /** jar 的 MIME 类型。 */
    private static final String JAR_CONTENT_TYPE = "application/java-archive";
    /** User-Agent。 */
    private static final String USER_AGENT = "CanvasMenu-ReleaseUploader";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ReleaseUploader() {
    }

    /**
     * Gradle JavaExec 入口。
     *
     * @param args owner, repo, token, tag, changelog, jarPath
     */
    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println("Usage: ReleaseUploader <owner> <repo> <token> <tag> <changelog> <jarPath>");
            System.err.println("Token is read from -PgithubToken or GH_TOKEN; never store it in the repository.");
            System.exit(1);
            return;
        }
        String token = args[2];
        if (token == null || token.isBlank()) {
            System.err.println("Missing GitHub token. Use -PgithubToken=... or GH_TOKEN.");
            System.exit(1);
            return;
        }
        try {
            publish(args[0], args[1], token, args[3], args[4], Path.of(args[5]));
            System.out.println("Release published: " + args[3]);
        } catch (IOException | InterruptedException e) {
            System.err.println("Publish failed: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.exit(1);
        }
    }

    /**
     * 完整发布流程。
     *
     * @param owner     组织 / 用户名
     * @param repo      仓库名
     * @param token     Personal Access Token
     * @param tag       标签，如 {@code v0.1.0}
     * @param changelog 更新日志
     * @param jar       构建产物
     * @throws IOException          网络 / IO 失败
     * @throws InterruptedException 被中断
     */
    public static void publish(String owner, String repo, String token, String tag,
                               String changelog, Path jar) throws IOException, InterruptedException {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must be provided at invocation time");
        }
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Jar not found: " + jar);
        }
        String releaseId = createRelease(owner, repo, token, tag, changelog);
        String uploadUrl = String.format(UPLOAD_BASE, owner, repo, releaseId);

        uploadAsset(uploadUrl, token, jar, jar.getFileName().toString(), JAR_CONTENT_TYPE);
        Path shaFile = createSha256File(jar);
        try {
            uploadAsset(uploadUrl, token, shaFile, shaFile.getFileName().toString(), "text/plain");
        } finally {
            Files.deleteIfExists(shaFile);
        }
        publishRelease(owner, repo, token, releaseId);
    }

    /** 步骤 1：以 draft=true 创建 Release。 */
    private static String createRelease(String owner, String repo, String token,
                                        String tag, String changelog)
            throws IOException, InterruptedException {
        String body = "{\"tag_name\":\"" + jsonEscape(tag)
                + "\",\"name\":\"" + jsonEscape(tag)
                + "\",\"body\":\"" + jsonEscape(changelog)
                + "\",\"draft\":true,\"prerelease\":false}";
        String response = request("POST",
                API_BASE + "/repos/" + owner + "/" + repo + "/releases",
                token, "application/vnd.github+json", body);
        Matcher matcher = ID_PATTERN.matcher(response);
        if (!matcher.find()) {
            throw new IOException("Cannot parse release id from API response");
        }
        return matcher.group(1);
    }

    /** 步骤 2：上传单个资产。 */
    private static void uploadAsset(String uploadUrl, String token, Path file,
                                    String name, String contentType)
            throws IOException, InterruptedException {
        String url = uploadUrl + "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        byte[] bytes = Files.readAllBytes(file);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", contentType)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> response = CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Asset upload failed for " + name + " (HTTP "
                    + response.statusCode() + ")");
        }
    }

    /** 步骤 4：校验通过后将 draft 改为 false。 */
    private static void publishRelease(String owner, String repo, String token, String releaseId)
            throws IOException, InterruptedException {
        request("PATCH",
                API_BASE + "/repos/" + owner + "/" + repo + "/releases/" + releaseId,
                token, "application/vnd.github+json", "{\"draft\":false}");
    }

    private static String request(String method, String url, String token,
                                  String contentType, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", contentType)
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> response = CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(method + " " + url + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static Path createSha256File(Path jar) throws IOException {
        String digest = Downloader.sha256Hex(jar);
        Path sha = jar.resolveSibling(jar.getFileName() + ".sha256");
        Files.writeString(sha, digest + "  " + jar.getFileName() + "\n", StandardCharsets.UTF_8);
        return sha;
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
