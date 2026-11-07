package com.chaossurvival.canvasmenu.update;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本比对。
 *
 * <p>支持 {@code v} 前缀、缺省的 minor / patch、pre-release 标识与 build metadata；
 * 遵循 SemVer 优先级规则：正式版高于同版本 pre-release。</p>
 */
public final class SemVer implements Comparable<SemVer> {

    private static final Pattern PATTERN = Pattern.compile(
            "^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String raw;

    private SemVer(int major, int minor, int patch, String preRelease, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease == null ? "" : preRelease;
        this.raw = raw;
    }

    /**
     * 解析版本字符串。
     *
     * @param value 版本字符串
     * @return 解析结果；非法输入返回空
     */
    public static Optional<SemVer> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return Optional.of(new SemVer(major, minor, patch, matcher.group(4), value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * @return 是否为预发布版本
     */
    public boolean isPrerelease() {
        return !preRelease.isEmpty();
    }

    /**
     * @param other 另一个版本
     * @return 本版本是否更新
     */
    public boolean isNewerThan(SemVer other) {
        return other != null && compareTo(other) > 0;
    }

    @Override
    public int compareTo(SemVer other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(patch, other.patch);
        if (result != 0) {
            return result;
        }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) {
            return 0;
        }
        if (preRelease.isEmpty()) {
            return 1;
        }
        if (other.preRelease.isEmpty()) {
            return -1;
        }
        return compareIdentifiers(preRelease, other.preRelease);
    }

    private static int compareIdentifiers(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            if (i >= partsA.length) {
                return -1;
            }
            if (i >= partsB.length) {
                return 1;
            }
            String left = partsA[i];
            String right = partsB[i];
            boolean leftNumeric = left.chars().allMatch(Character::isDigit);
            boolean rightNumeric = right.chars().allMatch(Character::isDigit);
            int cmp;
            if (leftNumeric && rightNumeric) {
                cmp = Long.compare(Long.parseLong(left), Long.parseLong(right));
            } else if (leftNumeric) {
                cmp = -1;
            } else if (rightNumeric) {
                cmp = 1;
            } else {
                cmp = left.compareToIgnoreCase(right);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /** @return 主版本号 */
    public int major() {
        return major;
    }

    /** @return 次版本号 */
    public int minor() {
        return minor;
    }

    /** @return 修订号 */
    public int patch() {
        return patch;
    }

    /** @return pre-release 标识 */
    public String preRelease() {
        return preRelease;
    }

    @Override
    public String toString() {
        return raw;
    }
}
