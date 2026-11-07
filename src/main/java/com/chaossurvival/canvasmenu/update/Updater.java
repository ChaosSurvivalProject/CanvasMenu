package com.chaossurvival.canvasmenu.update;

import com.chaossurvival.canvasmenu.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 更新安装器：<strong>下载 + 重启替换 + update.lock</strong>。
 *
 * <p>绝不热替换运行中的 jar（不触碰类加载器）；只在游戏退出阶段把新 jar 移动到
 * {@code mods/} 并删除旧 jar。{@code update.lock} 防止重复调度与重复替换。</p>
 */
public final class Updater {

    /** 锁文件名。 */
    public static final String LOCK_FILE = "update.lock";
    /** 下载暂存目录。 */
    public static final String STAGING_DIR = ".canvasmenu-update";
    /** 旧 jar 匹配模式。 */
    private static final String OLD_JAR_GLOB = "canvas-menu-*.jar";

    private static volatile boolean hookRegistered;
    private static volatile Path pendingNewJar;
    private static volatile Path pendingModsDir;

    private Updater() {
    }

    /**
     * 调度一次退出时替换。
     *
     * @param newJar 已下载并校验通过的新 jar（相对游戏目录）
     * @param modsDir mods 目录
     * @param oldJar  旧 jar（可为 {@code null}，此时按文件名模式清理）
     * @return 是否成功写入 lock
     */
    public static synchronized boolean scheduleSwap(Path newJar, Path modsDir, Path oldJar) {
        if (newJar == null || modsDir == null || !Files.isRegularFile(newJar)) {
            return false;
        }
        Path lockPath = modsDir.resolve(LOCK_FILE);
        if (Files.isRegularFile(lockPath)) {
            Log.debug("Update lock already exists, skipping duplicate schedule");
            return false;
        }
        String content = "{\"newJar\":\"" + escape(newJar.toString())
                + "\",\"oldJar\":\"" + escape(oldJar == null ? "" : oldJar.toString())
                + "\",\"state\":\"pending\"}";
        try {
            Files.createDirectories(modsDir);
            Files.writeString(lockPath, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warn("Cannot write update lock: {}", e.toString());
            return false;
        }
        pendingNewJar = newJar;
        pendingModsDir = modsDir;
        registerHook();
        Log.info("Update scheduled; it will be applied after the game exits");
        return true;
    }

    /**
     * Fabric {@code CLIENT_STOPPING} 事件调用（比 JVM shutdown hook 更早、更可靠）。
     */
    public static void onClientStopping() {
        performSwap(true);
    }

    /**
     * 启动时检查是否有挂起更新提示。
     *
     * @param modsDir mods 目录
     * @return 提示文本
     */
    public static Optional<String> pendingMessage(Path modsDir) {
        return Files.isRegularFile(modsDir.resolve(LOCK_FILE))
                ? Optional.of("Update pending — restart the game to apply")
                : Optional.empty();
    }

    /** 注册 JVM 兜底 hook，确保非正常退出路径也能完成替换。 */
    private static synchronized void registerHook() {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> performSwap(true), "canvasmenu-update-swap"));
    }

    private static synchronized void performSwap(boolean removeLock) {
        Path newJar = pendingNewJar;
        Path modsDir = pendingModsDir;
        if (newJar == null || modsDir == null || !Files.isRegularFile(newJar)) {
            return;
        }
        try {
            Path target = modsDir.resolve(newJar.getFileName().toString());
            Files.move(newJar, target, StandardCopyOption.REPLACE_EXISTING);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, OLD_JAR_GLOB)) {
                for (Path old : stream) {
                    if (!old.equals(target)) {
                        Files.deleteIfExists(old);
                    }
                }
            }
            Files.deleteIfExists(modsDir.resolve(LOCK_FILE));
            Log.info("Update applied: {}", target.getFileName());
        } catch (IOException e) {
            Log.warn("Update swap failed, will retry next launch: {}", e.toString());
        } finally {
            if (removeLock) {
                pendingNewJar = null;
            }
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
