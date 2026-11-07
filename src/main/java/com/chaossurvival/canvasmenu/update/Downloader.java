package com.chaossurvival.canvasmenu.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.DoubleConsumer;

/**
 * 异步下载 jar（调用方在虚拟线程里执行本类的同步方法）。
 *
 * <p>校验 {@code Content-Length} 与实际字节数；若 Release 附带 {@code .sha256} 资产，
 * 还会做 SHA-256 校验。写入 {@code .part} 临时文件，全部通过后才原子移动为最终文件。</p>
 */
public final class Downloader {

    /** 下载缓冲大小。 */
    private static final int BUFFER_SIZE = 8192;
    /** 下载请求超时。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Downloader() {
    }

    /**
     * 下载文件。
     *
     * @param url             下载地址
     * @param destination     目标路径
     * @param progress        进度回调 [0,1]，可为 {@code null}
     * @param expectedSha256  期望的 SHA-256 十六进制串，可为 {@code null}
     * @return 最终文件路径
     * @throws IOException          网络 / 校验 / IO 失败
     * @throws InterruptedException 被中断
     */
    public static Path download(String url, Path destination, DoubleConsumer progress,
                                String expectedSha256) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", UpdateChecker.USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Download failed with HTTP " + status);
        }
        long expectedSize = response.headers().firstValueAsLong("Content-Length").orElse(-1L);

        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path part = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(part);

        long total = 0L;
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(part,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (progress != null && expectedSize > 0L) {
                    progress.accept(Math.min(1.0d, (double) total / (double) expectedSize));
                }
            }
        }

        long actualSize = Files.size(part);
        if (expectedSize > 0L && actualSize != expectedSize) {
            Files.deleteIfExists(part);
            throw new IOException("Size mismatch: expected " + expectedSize + ", got " + actualSize);
        }

        if (expectedSha256 != null && !expectedSha256.isBlank()) {
            String expected = expectedSha256.trim().split("\\s+")[0];
            String actual = sha256Hex(part);
            if (!actual.equalsIgnoreCase(expected)) {
                Files.deleteIfExists(part);
                throw new IOException("SHA-256 mismatch, download discarded");
            }
        }

        Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
        if (progress != null) {
            progress.accept(1.0d);
        }
        return destination;
    }

    /**
     * 读取远程 {@code .sha256} 文件的内容（只取第一行的第一个 token）。
     *
     * @param url 校验文件地址
     * @return 十六进制摘要，失败返回 {@code null}
     */
    public static String fetchSha256(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", UpdateChecker.USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String body = response.body().trim();
            return body.isEmpty() ? null : body.split("\\s+")[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 计算文件 SHA-256。
     *
     * @param file 文件
     * @return 小写十六进制摘要
     * @throws IOException 读取失败
     */
    public static String sha256Hex(Path file) throws IOException {
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
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }
}
