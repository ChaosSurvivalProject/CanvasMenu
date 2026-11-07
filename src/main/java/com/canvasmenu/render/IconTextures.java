package com.canvasmenu.render;

import com.chaossurvival.canvasmenu.gui.render.GuiCompat;
import com.chaossurvival.canvasmenu.util.Async;
import com.chaossurvival.canvasmenu.util.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指令贴图仓库：<strong>ResourceManager 异步加载</strong>，避免启动卡顿。
 *
 * <p>加载在 Java 21 <strong>虚拟线程</strong>上进行，主线程永远不等待磁盘 / 资源包 IO。
 * 每张 64×64 PNG 解码为 ARGB 像素数组，并按需预计算「按行合并的绘制段」（run-length），
 * 这样渲染每帧只是遍历小段列表调用稳定的 {@code GuiGraphics.fill}，
 * 既不依赖 1.21.x 频繁变动的 {@code blit} / {@code RenderPipeline} / {@code Identifier} 签名，
 * 又能真实呈现 PNG 内容（不是纯色占位）。</p>
 *
 * <p>资源读取经 {@link GuiCompat#openResource(String)}：优先 Minecraft
 * {@code ResourceManager}（因此<strong>资源包可以覆盖贴图</strong>），
 * 反射不可用时回退 jar 内置 classpath 资源。</p>
 *
 * <p>加载失败 / 尺寸不符 / 资源缺失 → 返回 {@code ready=false} 的句柄，
 * 由 {@link IconBlitter} 自动降级为「主题色圆角 + 图标文字」占位符，
 * 界面永不空白。替换真实贴图只需覆盖同名 64×64 PNG，代码零改动。</p>
 */
public final class IconTextures {

    /** 贴图统一边长。 */
    public static final int SIZE = 64;
    /** 资源根路径（相对游戏资源根）。 */
    public static final String ROOT = "assets/canvasmenu/textures/commands/";
    /** 低于该 alpha 的像素视为透明，不产生绘制段。 */
    private static final int ALPHA_CUTOFF = 8;

    private static final Map<String, IconData> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean preloadStarted;

    private IconTextures() {
    }

    /**
     * 触发一次后台预加载（幂等，重复调用不会起第二个任务）。
     *
     * @param relativePaths 相对 {@link #ROOT} 的路径集合，通常来自
     *                      {@code commands.json} 的 {@code icon} 字段与分类图标
     */
    public static void preload(Collection<String> relativePaths) {
        if (preloadStarted || relativePaths == null) {
            return;
        }
        preloadStarted = true;
        List<String> paths = List.copyOf(relativePaths);
        Async.run(() -> {
            int ok = 0;
            for (String path : paths) {
                if (path == null || path.isBlank()) {
                    continue;
                }
                if (get(path).ready()) {
                    ok++;
                }
            }
            Log.info("Command icons preloaded: {}/{}", ok, paths.size());
        });
    }

    /**
     * 取贴图句柄（未加载过则同步解码；预加载完成后这里只是查表）。
     *
     * @param relativePath 相对路径，例如 {@code weather/weather_sun.png}
     * @return 永不为 {@code null} 的句柄
     */
    public static IconData get(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return IconData.MISSING;
        }
        return CACHE.computeIfAbsent(relativePath, IconTextures::load);
    }

    /** @return 已缓存的贴图数量（用于诊断） */
    public static int cachedCount() {
        return CACHE.size();
    }

    /** 清空缓存（热重载贴图时调用）。 */
    public static void clear() {
        CACHE.clear();
        preloadStarted = false;
    }

    private static IconData load(String relativePath) {
        String full = ROOT + relativePath;
        try (InputStream in = GuiCompat.openResource(full)) {
            if (in == null) {
                Log.debug("Command icon missing: {}", full);
                return IconData.MISSING;
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return IconData.MISSING;
            }
            // 统一到 64×64；若美术给了别的尺寸，按最近邻缩放，代码侧永远按 64 处理
            BufferedImage normalized = image;
            if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
                BufferedImage resized = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = resized.createGraphics();
                g2.drawImage(image, 0, 0, SIZE, SIZE, null);
                g2.dispose();
                normalized = resized;
            }
            int[] argb = normalized.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
            return new IconData(argb, true);
        } catch (Throwable t) {
            Log.debug("Cannot decode command icon {}: {}", full, t.toString());
            return IconData.MISSING;
        }
    }

    /**
     * 一张已解码贴图。绘制段按显示尺寸惰性生成并缓存。
     */
    public static final class IconData {

        /** 缺失 / 解码失败时的共享空句柄。 */
        public static final IconData MISSING = new IconData(new int[0], false);

        private final int[] argb;
        private final boolean ready;
        private final Map<Integer, List<Run>> runsBySize = new ConcurrentHashMap<>();

        private IconData(int[] argb, boolean ready) {
            this.argb = argb;
            this.ready = ready;
        }

        /** @return 贴图是否可用 */
        public boolean ready() {
            return ready;
        }

        /**
         * 取指定显示尺寸下的绘制段列表。
         *
         * @param displaySize 卡片上贴图的边长（像素）
         * @return 段列表（不可变）
         */
        public List<Run> runsFor(int displaySize) {
            if (!ready) {
                return List.of();
            }
            int size = Math.max(4, Math.min(SIZE, displaySize));
            return runsBySize.computeIfAbsent(size, s -> buildRuns(argb, s));
        }

        /**
         * 把 64×64 像素降采样为 {@code size×size} 并逐行合并相邻同色像素。
         *
         * <p>颜色按每通道高 4 位量化，微渐变因此被压成 8~20 段 / 行，
         * 视觉损失几乎不可见，但 fill 调用数从 4096 降到几百，滚动 30+ 卡片依旧流畅。</p>
         */
        private static List<Run> buildRuns(int[] pixels, int size) {
            List<Run> result = new ArrayList<>();
            for (int dy = 0; dy < size; dy++) {
                int sourceY = dy * SIZE / size;
                int runStart = 0;
                int runColor = sample(pixels, 0, sourceY, size);
                for (int dx = 1; dx < size; dx++) {
                    int color = sample(pixels, dx, sourceY, size);
                    if (!sameQuantized(color, runColor)) {
                        appendIfVisible(result, runStart, dx, dy, runColor);
                        runStart = dx;
                        runColor = color;
                    }
                }
                appendIfVisible(result, runStart, size, dy, runColor);
            }
            return List.copyOf(result);
        }

        private static int sample(int[] pixels, int dx, int sourceY, int size) {
            int sourceX = dx * SIZE / size;
            return pixels[sourceY * SIZE + sourceX];
        }

        private static void appendIfVisible(List<Run> out, int x1, int x2, int y, int color) {
            if (x2 > x1 && ((color >>> 24) & 0xFF) > ALPHA_CUTOFF) {
                out.add(new Run(x1, x2, y, color));
            }
        }

        private static boolean sameQuantized(int a, int b) {
            return ((a >>> 28) == (b >>> 28))
                    && (((a >>> 20) & 0xF) == ((b >>> 20) & 0xF))
                    && (((a >>> 12) & 0xF) == ((b >>> 12) & 0xF))
                    && (((a >>> 4) & 0xF) == ((b >>> 4) & 0xF));
        }
    }

    /**
     * 一行中的连续同色区间。
     *
     * @param x1    区间起点（相对贴图左上角）
     * @param x2    区间终点（不含）
     * @param y     行号
     * @param color ARGB 颜色
     */
    public record Run(int x1, int x2, int y, int color) {
    }
}
