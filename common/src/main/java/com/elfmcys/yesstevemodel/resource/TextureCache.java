package com.elfmcys.yesstevemodel.resource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/**
 * 贴图处理结果磁盘缓存。
 *
 * <p>实测启动时的模型加载瓶颈不在反序列化，而在 {@code YSMClientMapper.buildParsedBundle}
 * 内的贴图处理：每张贴图都要 {@code decodeToImage}（解码 AVIF/PNG 等）再
 * {@code encodeToPng}（{@code ImageIO.write}）。28 个模型、每个数十张贴图，
 * 累计约 5 秒纯 CPU 开销。
 *
 * <p>这里按"原始贴图字节的 SHA-256"缓存处理后的 PNG 字节，命中时直接读取，
 * 完全跳过解码 + 重编码。
 *
 * <p>与 {@link ModelParseCache} 相同的可靠性策略：临时文件 + 原子移动；
 * 任何异常都静默回退到正常处理，不影响功能。
 */
public final class TextureCache {

    private static volatile Path cacheDir = null;

    /** 统计命中/未命中，便于排查（仅日志用途）。 */
    private static int hits = 0;
    private static int misses = 0;

    private TextureCache() {
    }

    public static void setCacheDir(Path dir) {
        cacheDir = dir;
        if (dir != null) {
            try {
                Files.createDirectories(dir);
            } catch (IOException ignored) {
                cacheDir = null;
            }
        }
    }

    public static Path getCacheDir() {
        return cacheDir;
    }

    /** 缓存容量上限（字节）。超过后在下次启动时按最后修改时间清理最旧的一半。 */
    private static final long MAX_CACHE_BYTES = 500L * 1024 * 1024;

    /**
     * 启动时检查缓存体积，超限则清理最旧的条目。
     *
     * <p>模型更新频繁的用户会不断产生新贴图缓存，若不设上限会无限增长。
     * 清理策略：按最后访问时间排序，删除最旧的一半，保留常用的近期条目。
     */
    public static void trimIfOversized() {
        Path dir = cacheDir;
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try {
            java.util.List<Path> files = new java.util.ArrayList<>();
            long[] total = {0L};
            Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            total[0] += Files.size(p);
                            files.add(p);
                        } catch (IOException ignored) {
                        }
                    });
            if (total[0] <= MAX_CACHE_BYTES) {
                return;
            }
            files.sort(java.util.Comparator.comparingLong(p -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }));
            int toDelete = files.size() / 2;
            int deleted = 0;
            for (int i = 0; i < toDelete; i++) {
                try {
                    Files.deleteIfExists(files.get(i));
                    deleted++;
                } catch (IOException ignored) {
                }
            }
            org.slf4j.LoggerFactory.getLogger("yes_steve_model").info(
                    "[YSM] Texture cache trimmed: removed {} of {} files ({} MB -> target under {} MB)",
                    deleted, files.size(),
                    Math.round(total[0] / 1048576.0), MAX_CACHE_BYTES / 1048576);
        } catch (Exception ignored) {
            // 清理失败不影响功能
        }
    }

    public static void resetStats() {
        hits = 0;
        misses = 0;
    }

    public static int getHits() {
        return hits;
    }

    public static int getMisses() {
        return misses;
    }

    /**
     * 计算缓存文件路径。
     *
     * <p>缓存键必须包含所有影响处理结果的参数：原始字节、图像格式、宽高。
     * 仅哈希字节是不够的 —— 同一份字节在不同 format/尺寸下会解码出不同结果，
     * 若只按字节哈希就会错误复用。格式版本号也一并纳入，便于将来失效旧缓存。
     */
    private static Path fileFor(byte[] data, int imageFormat, int width, int height) {
        Path dir = cacheDir;
        if (dir == null || data == null || data.length == 0) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // 缓存格式版本：处理逻辑变更时递增，旧缓存自然失效
            md.update((byte) 1);
            md.update((byte) (imageFormat & 0xFF));
            md.update((byte) ((imageFormat >>> 8) & 0xFF));
            md.update((byte) (width & 0xFF));
            md.update((byte) ((width >>> 8) & 0xFF));
            md.update((byte) ((width >>> 16) & 0xFF));
            md.update((byte) ((width >>> 24) & 0xFF));
            md.update((byte) (height & 0xFF));
            md.update((byte) ((height >>> 8) & 0xFF));
            md.update((byte) ((height >>> 16) & 0xFF));
            md.update((byte) ((height >>> 24) & 0xFF));
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            // 两级目录，避免单目录文件过多
            return dir.resolve(sb.substring(0, 2)).resolve(sb.substring(2) + ".png");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 尝试读取已缓存的贴图处理结果。
     *
     * @return 缓存的字节；未命中返回 null
     */
    public static byte[] load(byte[] sourceData, int imageFormat, int width, int height) {
        Path file = fileFor(sourceData, imageFormat, width, height);
        if (file == null || !Files.isRegularFile(file)) {
            misses++;
            return null;
        }
        try {
            byte[] cached = Files.readAllBytes(file);
            if (cached.length == 0) {
                misses++;
                return null;
            }
            hits++;
            return cached;
        } catch (IOException e) {
            misses++;
            return null;
        }
    }

    /** 写入缓存；失败静默忽略（缓存失败不应影响主流程）。 */
    public static void store(byte[] sourceData, int imageFormat, int width, int height, byte[] processed) {
        if (processed == null || processed.length == 0) {
            return;
        }
        Path file = fileFor(sourceData, imageFormat, width, height);
        if (file == null) {
            return;
        }
        Path tmp = null;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tmp = Files.createTempFile(parent, "tex", ".tmp");
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), 65536)) {
                out.write(processed);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (Exception e) {
            // 只在首次失败时提示一次，避免刷屏；缓存不可用不影响功能
            warnOnce(e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static volatile boolean warned = false;

    private static void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        org.slf4j.LoggerFactory.getLogger("yes_steve_model")
                .warn("[YSM] Texture cache unavailable, falling back to re-encoding each launch: {}", e.toString());
    }
}
