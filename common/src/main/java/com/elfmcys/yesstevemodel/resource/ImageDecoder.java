package com.elfmcys.yesstevemodel.resource;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模型贴图解码入口。
 *
 * <p>支持 PNG / JPEG / BMP（走 JDK 自带的 ImageIO）与 WebP（走 TwelveMonkeys，纯 Java）。
 *
 * <p><b>AVIF 不受支持。</b> 可用的 AVIF 解码器要么是原生库（libavif / dav1d），要么没有明确的
 * 开源许可，两者都不适合打包进模组。此前用的是 ImageStream，其上游仓库
 * （TartaricAlkaline/ImageStream）没有任何许可证文件，属于不可再分发的依赖，已移除。
 *
 * <p>遇到 AVIF 贴图时不会抛异常：先尝试一次 ImageIO（万一运行环境装了 AVIF 插件），
 * 失败则返回 null 并只打印一次提示，由调用方回退到默认贴图。这样模型其余部分仍能正常加载，
 * 不会因为一张贴图让整个模型加载失败。
 */
public final class ImageDecoder {

    public static final int FORMAT_BMP = 1;
    public static final int FORMAT_PNG = 2;
    public static final int FORMAT_JPEG = 3;
    public static final int FORMAT_WEBP = 4;
    public static final int FORMAT_AVIF = 5;

    private static final AtomicBoolean WEBP_UNAVAILABLE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean AVIF_UNSUPPORTED_LOGGED = new AtomicBoolean();

    /** WebP 的 ImageReaderSpi 是公开的；对应的 WebPImageReader 本身是包级私有，只能经 SPI 创建。 */
    private static final ImageReaderSpi WEBP_SPI = new WebPImageReaderSpi();

    private ImageDecoder() {
    }

    /**
     * 按格式解码。解码失败返回 {@code null}，不抛异常。
     *
     * @param data   原始字节
     * @param format {@link #FORMAT_PNG} 等格式常量；其它值按 ImageIO 处理
     */
    public static BufferedImage decode(byte[] data, int format) {
        if (data == null || data.length == 0) {
            return null;
        }
        return switch (format) {
            case FORMAT_WEBP -> decodeWebp(data);
            case FORMAT_AVIF -> decodeAvif(data);
            default -> decodeWithImageIo(data);
        };
    }

    private static BufferedImage decodeWithImageIo(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage decodeWebp(byte[] data) {
        ImageReader reader = null;
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (stream == null) {
                return null;
            }
            // 经 SPI 创建，不依赖 ImageIO 的全局注册 —— 模组的类加载器不一定能被 ServiceLoader 看到。
            reader = WEBP_SPI.createReaderInstance();
            reader.setInput(stream, true, true);
            return reader.read(0);
        } catch (Throwable t) {
            if (WEBP_UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
                System.err.println("[YSM] WebP 贴图解码失败，该贴图将回退为默认贴图: " + t);
            }
            return null;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private static BufferedImage decodeAvif(byte[] data) {
        // 环境里恰好有 AVIF 插件时仍能解出来
        BufferedImage viaImageIo = decodeWithImageIo(data);
        if (viaImageIo != null) {
            return viaImageIo;
        }
        if (AVIF_UNSUPPORTED_LOGGED.compareAndSet(false, true)) {
            System.err.println("[YSM] 当前版本不支持 AVIF 贴图，该贴图将回退为默认贴图。"
                    + "如需支持请将贴图转为 PNG 或 WebP。");
        }
        return null;
    }
}
