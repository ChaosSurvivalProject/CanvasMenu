package com.chaossurvival.canvasmenu.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一日志门面。
 *
 * <p>约定：只记录可诊断的技术信息，<strong>绝不记录令牌、绝对路径或用户隐私</strong>。
 * 网络失败一律走 {@code debug} 级别，避免刷屏打断游戏。</p>
 */
public final class Log {

    /** 模组标识，与 {@code fabric.mod.json} 的 id 保持一致。 */
    public static final String MOD_ID = "canvasmenu";

    private static final Logger LOGGER = LoggerFactory.getLogger("CanvasMenu");

    private Log() {
    }

    /** 输出信息级日志。 */
    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    /** 输出警告级日志。 */
    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    /** 输出错误级日志。 */
    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    /** 输出调试级日志，用于静默降级路径。 */
    public static void debug(String message, Object... args) {
        LOGGER.debug(message, args);
    }
}
