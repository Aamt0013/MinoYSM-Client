package com.elfmcys.yesstevemodel.resource;

import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/**
 * 模型解析结果磁盘缓存。
 *
 * <p>26.x 移植后每次启动都要重新反序列化全部模型（实测约 19 秒），因为
 * {@link RawYsmModel} 只存在于内存。这里按"输入字节的 SHA-256"把解析结果
 * Java 序列化到磁盘，命中时直接反序列化回来，跳过整个解析流程。
 *
 * <p>缓存键包含 format，避免不同解析路径共用同一条目。写入采用
 * "临时文件 + 原子移动"，防止进程中断留下半截文件。
 *
 * <p>设计为独立工具类，不侵入 {@link YSMBinaryDeserializer} 的原有逻辑。
 */
public final class ModelParseCache {

    private static volatile Path cacheDir = null;

    private ModelParseCache() {
    }

    /** 设置缓存目录；传 null 关闭缓存。 */
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

    private static Path fileFor(byte[] data, int format) {
        Path dir = cacheDir;
        if (dir == null || data == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((byte) (format & 0xFF));
            md.update((byte) ((format >>> 8) & 0xFF));
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return dir.resolve(sb.toString() + ".ysmparsed");
        } catch (Exception e) {
            return null;
        }
    }

    /** 尝试读取缓存；未命中或损坏返回 null。 */
    public static RawYsmModel load(byte[] data, int format) {
        Path file = fileFor(data, format);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(file), 65536))) {
            Object obj = in.readObject();
            return obj instanceof RawYsmModel model ? model : null;
        } catch (Exception ignored) {
            // 缓存损坏或版本不兼容：忽略，由调用方走正常解析并覆盖
            return null;
        }
    }

    /** 写入缓存；失败不影响主流程。 */
    public static void store(byte[] data, int format, RawYsmModel model) {
        if (model == null) {
            return;
        }
        Path file = fileFor(data, format);
        if (file == null) {
            return;
        }
        Path tmp = null;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tmp = Files.createTempFile(parent, "ysmparse", ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmp), 65536))) {
                out.writeObject(model);
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
                .warn("[YSM] Model parse cache unavailable, falling back to re-parsing: {}", e.toString());
    }

    // ------------------------------------------------------------------
    // 透光扫描结果缓存
    //
    // TranslucencyScanner 要对每张贴图按面的 UV 范围逐像素采样，是
    // buildParsedBundle 里最贵的一步（46 个模型实测约 7.6 秒）。
    // 它的输入（贴图像素 + 面的 UV）在 RawYsmModel 里已经稳定，
    // 输出只有两类：
    //   1. scan() 的返回值（STATE_INVISIBLE / STATE_OPAQUE / STATE_TRANSLUCENT）
    //   2. 副作用：某些贴图索引被标记为"含彩色半透明像素"
    // 两者都与模型内容一一对应，因此可以整体缓存。
    // ------------------------------------------------------------------

    /**
     * 一个模型的透光扫描结果。
     *
     * <p>main 与 arm 两个网格各自有独立的 {@link com.elfmcys.yesstevemodel.resource.YSMClientMapper.TranslucencyScanner}
     * 实例，扫描调用序列互不相干，因此分开存放——共用一个数组会让两边的
     * 重放游标互相干扰，导致面状态错位。
     *
     * <p>{@code states} 按 scan() 的调用顺序存放返回值；
     * {@code flags} 是最终 {@code results[]} 数组（每张贴图是否含彩色半透明像素）。
     *
     * <p>之所以按"调用顺序"而不是"骨骼/立方体/面索引"来存：buildMesh 的遍历
     * 顺序完全由 RawYsmModel 的结构决定，只要模型没变，调用顺序就完全一致。
     * 这样避免引入额外的索引映射，出错面也更小。
     */
    public record ScanResult(
            int[] mainStates, boolean[] mainFlags,
            int[] armStates, boolean[] armFlags
    ) implements java.io.Serializable {
        /** 是否有任何可用数据。 */
        public boolean hasMain() {
            return mainStates != null && mainStates.length > 0;
        }

        public boolean hasArm() {
            return armStates != null && armStates.length > 0;
        }
    }

    private static Path scanFileFor(byte[] data, int format) {
        Path parsed = fileFor(data, format);
        if (parsed == null) {
            return null;
        }
        String name = parsed.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        return parsed.getParent().resolve(base + ".ysmscan");
    }

    /** 读取透光扫描缓存；未命中返回 null。 */
    public static ScanResult loadScan(byte[] data, int format) {
        Path file = scanFileFor(data, format);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(file), 65536))) {
            Object obj = in.readObject();
            return obj instanceof ScanResult r ? r : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 写入透光扫描缓存；失败不影响主流程。 */
    public static void storeScan(byte[] data, int format, ScanResult result) {
        if (result == null) {
            return;
        }
        Path file = scanFileFor(data, format);
        if (file == null) {
            return;
        }
        Path tmp = null;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tmp = Files.createTempFile(parent, "ysmscan", ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmp), 65536))) {
                out.writeObject(result);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (Exception e) {
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
}
