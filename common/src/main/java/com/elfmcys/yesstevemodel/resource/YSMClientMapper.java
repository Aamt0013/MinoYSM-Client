package com.elfmcys.yesstevemodel.resource;

import org.jetbrains.annotations.Nullable;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.audio.AudioCodec;
import com.elfmcys.yesstevemodel.audio.AudioTrackData;
import com.elfmcys.yesstevemodel.client.ClientModelInfo;
import rip.ysm.compat.oculus.ShadersTextureType;
import com.elfmcys.yesstevemodel.client.gui.custom.AbstractConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.ExtraAnimationButtons;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.CheckboxConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RadioConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RangeConfig;
import com.elfmcys.yesstevemodel.client.model.MainModelData;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationState;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.event.ParticleEventKeyFrame;
import com.elfmcys.yesstevemodel.geckolib3.core.keyframe.BoneAnimation;
import com.elfmcys.yesstevemodel.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.elfmcys.yesstevemodel.geckolib3.core.keyframe.bone.BoneKeyFrameProcessor;
import com.elfmcys.yesstevemodel.geckolib3.core.keyframe.bone.EasingType;
import com.elfmcys.yesstevemodel.geckolib3.core.keyframe.bone.RawBoneKeyFrame;
import com.elfmcys.yesstevemodel.geckolib3.core.keyframe.event.EventKeyFrame;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.FloatValue;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.file.*;
import com.elfmcys.yesstevemodel.geckolib3.resource.GeckoLibCache;
import com.elfmcys.yesstevemodel.geckolib3.util.IInterpolable;
import com.elfmcys.yesstevemodel.geckolib3.util.LinearKeyframeInterpolator;
import com.elfmcys.yesstevemodel.geckolib3.util.TicksInterpolator;
import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.resource.models.*;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.util.data.StringMapPair;
import com.elfmcys.yesstevemodel.util.data.StringPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.apache.commons.lang3.tuple.Pair;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggPacketReader;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.vorbis.VorbisFile;
import org.joml.Vector2f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;

public class YSMClientMapper {




    public static class TranslucencyScanner {
        private final BufferedImage[] images;
        private final boolean[] results;
//        private int remaining;

        public static final int STATE_INVISIBLE = 0;
        public static final int STATE_OPAQUE = 1;
        public static final int STATE_TRANSLUCENT = 2;

        /**
         * 录制下来的 scan() 返回值序列。
         *
         * <p>非 null 时进入"重放模式"：scan() 不再解码贴图逐像素采样，
         * 而是按调用顺序直接返回录制好的结果。用于跳过启动时的重复扫描。
         */
        private final int[] replayStates;
        private int replayCursor = 0;

        /** 扫描是否真的执行过（重放模式下为 false，不需要写缓存）。 */
        private boolean didScan = false;

        public TranslucencyScanner(BufferedImage[] images, int expectedCount) {
            this(images, expectedCount, null);
        }

        /**
         * @param replay 已缓存的 scan() 返回值序列；非 null 且长度足够时走重放模式
         */
        public TranslucencyScanner(BufferedImage[] images, int expectedCount, @Nullable int[] replay) {
            this(images, expectedCount, replay, null);
        }

        /**
         * @param replay 已缓存的 scan() 返回值序列
         * @param cachedFlags 已缓存的 {@code results[]}（每张贴图是否含彩色半透明像素）
         */
        public TranslucencyScanner(BufferedImage[] images, int expectedCount,
                                   @Nullable int[] replay, @Nullable boolean[] cachedFlags) {
            this.images = images;
            this.results = new boolean[Math.max(expectedCount, images.length)];
            this.replayStates = replay;
            // 重放模式下 results[] 不会被 scan() 填充（那是真实扫描的副作用），
            // 必须从缓存恢复，否则 buildMesh 拿到的 translucencyArray 全是 false，
            // 半透明贴图会被当成不透明，渲染出错。
            if (replay != null && cachedFlags != null) {
                System.arraycopy(cachedFlags, 0,
                        this.results, 0, Math.min(cachedFlags.length, this.results.length));
            }
        }

        /** 是否处于重放模式。 */
        public boolean isReplaying() {
            return replayStates != null;
        }

        /** 扫描是否真的执行过。 */
        public boolean didScan() {
            return didScan;
        }

        /** 已录制的 scan() 返回值序列；未扫描时返回 null。 */
        @Nullable
        public int[] getRecordedStates() {
            return recordedStates;
        }

        @Nullable
        private int[] recordedStates = null;

        /** 记录本次 scan 的返回值，供后续写入缓存。 */
        private void record(int state) {
            if (recordedStates == null) {
                recordedStates = new int[64];
            } else if (recordCursor >= recordedStates.length) {
                recordedStates = java.util.Arrays.copyOf(recordedStates, recordedStates.length * 2);
            }
            recordedStates[recordCursor++] = state;
        }

        private int recordCursor = 0;

//        public boolean isFinished() {
//            return remaining <= 0;
//        }

        public boolean[] getResults() {
            return results;
        }

        public int scan(RawYsmModel.RawFace face) {
            // 重放模式：直接返回录制结果，不解码、不采样
            if (replayStates != null) {
                if (replayCursor < replayStates.length) {
                    return replayStates[replayCursor++];
                }
                // 录制长度不足（模型结构变了）：退回真实扫描
            }

            didScan = true;
            int state = scanImpl(face);
            record(state);
            return state;
        }

        private int scanImpl(RawYsmModel.RawFace face) {
            float minU = face.u[0], maxU = face.u[0];
            float minV = face.v[0], maxV = face.v[0];
            for (int i = 1; i < 4; i++) {
                minU = Math.min(minU, face.u[i]);
                maxU = Math.max(maxU, face.u[i]);
                minV = Math.min(minV, face.v[i]);
                maxV = Math.max(maxV, face.v[i]);
            }

            boolean hasValidImage = false;
            boolean faceHasVisiblePixel = false;
            boolean faceHasTransparentPixel = false;

            for (int i = 0; i < images.length; i++) {
                if (images[i] == null) continue;
                hasValidImage = true;

                BufferedImage img = images[i];
                int imgW = img.getWidth();
                int imgH = img.getHeight();

                int startX = (int) Math.floor(minU * imgW + 0.01f);
                int endX = (int) Math.floor(maxU * imgW - 0.01f);
                if (endX < startX) endX = startX;

                int startY = (int) Math.floor(minV * imgH + 0.01f);
                int endY = (int) Math.floor(maxV * imgH - 0.01f);
                if (endY < startY) endY = startY;

                startX = Math.max(0, Math.min(startX, imgW - 1));
                endX = Math.max(0, Math.min(endX, imgW - 1));
                startY = Math.max(0, Math.min(startY, imgH - 1));
                endY = Math.max(0, Math.min(endY, imgH - 1));

                boolean imageHasVisiblePixel = false;
                boolean imageHasTransparentPixel = false;
                boolean imageHasColoredTranslucentPixel = false;

                for (int x = startX; x <= endX; x++) {
                    for (int y = startY; y <= endY; y++) {
                        int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;

                        if (alpha > 0) {
                            imageHasVisiblePixel = true;

                            if (alpha < 255) {
                                imageHasColoredTranslucentPixel = true;
                            }
                        }

                        if (alpha < 255) {
                            imageHasTransparentPixel = true;
                        }

                        if (imageHasVisiblePixel && imageHasTransparentPixel && imageHasColoredTranslucentPixel) {
                            break;
                        }
                    }

                    if (imageHasVisiblePixel && imageHasTransparentPixel && imageHasColoredTranslucentPixel) {
                        break;
                    }
                }

                if (imageHasVisiblePixel) {
                    faceHasVisiblePixel = true;

                    if (imageHasTransparentPixel) {
                        faceHasTransparentPixel = true;
                    }

                    if (imageHasColoredTranslucentPixel) {
                        results[i] = true;
                    }
                }
            }

            if (!hasValidImage) return STATE_OPAQUE;
            if (!faceHasVisiblePixel) return STATE_INVISIBLE;
            if (faceHasTransparentPixel) return STATE_TRANSLUCENT;
            return STATE_OPAQUE;
        }
    }

    private static BufferedImage decodeToImage(byte[] data, int imageFormat, int width, int height) {
        if (data == null || data.length == 0) {
            return null;
        }

        if (imageFormat == 0) {
            imageFormat = YSMFolderDeserializer.detectFormat(data);
            if (imageFormat == 0) {
                imageFormat = 1;
            }
        }

        try {
            if (imageFormat == -1) {
                if (width > 0 && height > 0 && data.length >= width * height * 4) {
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    int[] pixels = new int[width * height];
                    for (int i = 0; i < pixels.length; i++) {
                        int r = data[i * 4] & 0xFF;
                        int g = data[i * 4 + 1] & 0xFF;
                        int b = data[i * 4 + 2] & 0xFF;
                        int a = data[i * 4 + 3] & 0xFF;
                        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    img.setRGB(0, 0, width, height, pixels, 0, width);
                    return img;
                } else throw new RuntimeException("Invalid RGBA texture");
            } else {
                switch (imageFormat) {
                    case 1:
                    case 2:
                    case 3:
                        return ImageIO.read(new ByteArrayInputStream(data));
                    case 4:
                    case 5:
                        return ImageDecoder.decode(data, imageFormat);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] encodeToPng(BufferedImage img, byte[] fallbackData) {
        if (img != null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                return baos.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fallbackData;
    }

    /** 透光扫描缓存的标签，与解析缓存（crypto 版本 1/2/3）区分。 */
    private static final int SCAN_CACHE_TAG = 0x5953434E; // "YSCN"

    /**
     * 解析透光扫描缓存的键。
     *
     * <p>用 {@code raw.properties.sha256}（模型内容的稳定哈希）。它由模型文件
     * 本身算出，只要模型没变就一直稳定，改动模型后自动失效。
     *
     * @return 键字节；模型没有 sha256 时返回 null（调用方跳过缓存）
     */
    @Nullable
    private static byte[] resolveScanCacheKey(RawYsmModel raw) {
        try {
            String sha = raw.properties != null ? raw.properties.sha256 : null;
            if (sha == null || sha.isEmpty()) {
                return null;
            }
            return sha.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 把任意格式的贴图转成 PNG 字节。
     *
     * <p>走 {@link TextureCache}：非 PNG 输入的编码结果按"源数据哈希"缓存，
     * 命中时直接返回，跳过解码 + 重编码。作者头像、额外贴图等零散图片
     * 都通过这里处理，是启动耗时里仅次于透光扫描的一块。
     */
    public static byte[] toPng(byte[] data, int imageFormat, int width, int height) {
        if (imageFormat == 2) {
            return data;
        }
        byte[] cached = TextureCache.load(data, imageFormat, width, height);
        if (cached != null) {
            return cached;
        }
        BufferedImage img = decodeToImage(data, imageFormat, width, height);
        byte[] png = encodeToPng(img, data);
        TextureCache.store(data, imageFormat, width, height, png);
        return png;
    }

    public static ClientModelInfo buildParsedBundle(RawYsmModel raw, String modelId) {
        long __t0 = System.currentTimeMillis();
        Map<String, OuterFileTexture> mainTextures = new LinkedHashMap<>();
        int textureCount = Math.max(1, raw.mainEntity.textures.size());

        // 透光扫描结果缓存。
        //
        // TranslucencyScanner 要按每个面的 UV 范围逐像素采样贴图 alpha，是
        // buildParsedBundle 里最贵的一步（46 个模型实测约 7.6 秒）。
        // 它的输入（贴图像素 + 面的 UV）在 RawYsmModel 里已稳定，
        // 输出（每个面的状态 + 每张贴图的半透明标记）也与模型内容一一对应，
        // 因此整体缓存下来，下次启动直接重放，完全跳过解码。
        //
        // 键用 properties.sha256（模型内容的稳定哈希）。为空时退回不缓存。
        byte[] cacheKey = resolveScanCacheKey(raw);
        ModelParseCache.ScanResult cachedScan =
                cacheKey != null ? ModelParseCache.loadScan(cacheKey, SCAN_CACHE_TAG) : null;

        // 重放模式下不需要 BufferedImage，imagesList 留空以跳过解码
        List<BufferedImage> imagesList = new ArrayList<>();
        boolean replay = cachedScan != null && (cachedScan.hasMain() || cachedScan.hasArm());

        for (RawYsmModel.RawTexture rt : raw.mainEntity.textures.values()) {
            BufferedImage img = null;
            if (!replay) {
                // 26.x 性能优化：缓存"处理后的 PNG"，跳过重复的编码（ImageIO.write）。
                //
                // 注意：decodeToImage 必须执行。原因是 TranslucencyScanner 依赖
                // BufferedImage 扫描像素来判定每个面的可见性/透明度状态；
                // 若传入 null，scan() 会因 hasValidImage=false 而一律返回 STATE_OPAQUE，
                // 导致全透明的面不再被剔除、半透明材质被当作不透明处理。
                //
                // 重放模式下已有扫描结果，跳过解码不会影响正确性。
                img = decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
            }
            imagesList.add(img);

            boolean isPng = rt.imageFormat == 2;
            byte[] cachedPng = isPng ? null : TextureCache.load(rt.data, rt.imageFormat, rt.width, rt.height);

            byte[] processedData;
            if (isPng) {
                // 原生 PNG：直接使用原数据
                processedData = rt.data;
            } else if (cachedPng != null) {
                // 非 PNG 且缓存命中：跳过重编码
                processedData = cachedPng;
            } else {
                // 走到这里说明 PNG 缓存未命中，必须解码一次才能重编码
                if (img == null) {
                    img = decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
                }
                processedData = encodeToPng(img, rt.data);
                TextureCache.store(rt.data, rt.imageFormat, rt.width, rt.height, processedData);
            }
            OuterFileTexture tex = new OuterFileTexture(processedData);

            Map<ShadersTextureType, OuterFileTexture> suffixTextures = new LinkedHashMap<>();
            for (RawYsmModel.RawTexture.SubTexture sub : rt.subTextures) {
                if (sub.data == null) continue;
                byte[] processedSubData;
                byte[] cachedSub = TextureCache.load(sub.data, sub.imageFormat, sub.width, sub.height);
                if (cachedSub != null) {
                    processedSubData = cachedSub;
                } else {
                    processedSubData = toPng(sub.data, sub.imageFormat, sub.width, sub.height);
                    TextureCache.store(sub.data, sub.imageFormat, sub.width, sub.height, processedSubData);
                }
                if (sub.specularType == 1) {
                    suffixTextures.put(ShadersTextureType.NORMAL, new OuterFileTexture(processedSubData));
                } else if (sub.specularType == 2) {
                    suffixTextures.put(ShadersTextureType.SPECULAR, new OuterFileTexture(processedSubData));
                }
            }
            tex.setSuffixTextures(suffixTextures);
            mainTextures.put(rt.name, tex);
        }
        Map<String, OuterFileTexture> avatarTextures = new LinkedHashMap<>();
        for (RawYsmModel.RawMetadata.Author author : raw.metadata.authors) {
            if (author.avatarImage == null) continue;
            byte[] processedAvatarData = toPng(author.avatarImage.data, author.avatarImage.format, author.avatarImage.width, author.avatarImage.height);
            OuterFileTexture tex = new OuterFileTexture(processedAvatarData);
            avatarTextures.put(author.avatarImage.name, tex);
        }
        OrderedStringMap<String, OuterFileTexture> textureMap = buildTextureMap(mainTextures);

        GeometryDescription context = buildContext(raw.mainEntity.mainModel);

        BufferedImage[] imagesArray = imagesList.toArray(new BufferedImage[0]);
        boolean hasMainModel = raw.mainEntity.mainModel != null;
        boolean hasArmModel = raw.mainEntity.armModel != null;
        // main 与 arm 各自独立重放：两者的扫描序列长度和内容都不同
        int[] mainReplay = replay && cachedScan.hasMain() ? cachedScan.mainStates() : null;
        int[] armReplay = replay && cachedScan.hasArm() ? cachedScan.armStates() : null;
        boolean[] mainCachedFlags = replay && cachedScan.hasMain() ? cachedScan.mainFlags() : null;
        boolean[] armCachedFlags = replay && cachedScan.hasArm() ? cachedScan.armFlags() : null;
        TranslucencyScanner mainScanner = hasMainModel
                ? new TranslucencyScanner(imagesArray, textureCount, mainReplay, mainCachedFlags) : null;
        TranslucencyScanner armScanner = hasArmModel
                ? new TranslucencyScanner(imagesArray, textureCount, armReplay, armCachedFlags) : null;

        GeoModel mainMesh = buildMesh(raw.mainEntity.mainModel, context, textureCount, mainScanner, raw.properties.allCutout);
        GeoModel armMesh = hasArmModel ? buildMesh(raw.mainEntity.armModel, context, textureCount, armScanner, raw.properties.allCutout) : mainMesh;

        // 首次解析（非重放）时把扫描结果写回缓存，供下次启动重放。
        if (!replay && cacheKey != null) {
            int[] mainStates = mainScanner != null ? mainScanner.getRecordedStates() : null;
            int[] armStates = armScanner != null ? armScanner.getRecordedStates() : null;
            boolean[] mainFlags = mainScanner != null ? mainScanner.getResults() : null;
            boolean[] armFlags = armScanner != null ? armScanner.getResults() : null;
            boolean anyMain = mainStates != null && mainStates.length > 0;
            boolean anyArm = armStates != null && armStates.length > 0;
            if (anyMain || anyArm) {
                ModelParseCache.storeScan(cacheKey, SCAN_CACHE_TAG,
                        new ModelParseCache.ScanResult(mainStates, mainFlags, armStates, armFlags));
            }
        }

//        System.out.println(modelId + Arrays.toString(mainMesh.translucentTexture));

        GeoModel[] meshes = new GeoModel[]{mainMesh, armMesh};

        Map<String, AnimationFile> animations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : raw.mainEntity.animationFiles.entrySet()) {
            animations.put(entry.getKey(), new AnimationFile(buildAnimations(entry.getValue(), raw.properties.mergeMultilineExpr)));
        }

        List<AnimationControllerFile> controllersList = new ArrayList<>();
        if (raw.mainEntity.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : raw.mainEntity.animationControllerFiles) {
                Map<String, AnimationController> controllerMap = buildControllers(file.controllers, raw.properties.mergeMultilineExpr);
                if (!controllerMap.isEmpty()) {
                    controllersList.add(new AnimationControllerFile(controllerMap));
                }
            }
        }

        MainModelData mainModelData = new MainModelData(meshes, animations, controllersList.toArray(new AnimationControllerFile[0]), textureMap);

        ServerModelInfo modelInfo = buildModelInfo(raw);
        ModelExtraResourcesFile extraResources = buildExtraResources(raw);
        ProjectileModelFiles[] extraItemModels = buildExtraItemModels(raw, context, raw.properties.mergeMultilineExpr);
        VehicleModelFiles[] extraEntityModels = buildExtraEntityModels(raw, context, raw.properties.mergeMultilineExpr);
        Map<String, OuterFileTexture> extraTextures = buildExtraTextures(raw);

        return new ClientModelInfo(mainModelData, extraItemModels, extraEntityModels, extraResources, modelInfo, avatarTextures, extraTextures);
    }

    private static GeoModel buildMesh(RawYsmModel.RawGeometry rawGeo, GeometryDescription context, int textureCount, TranslucencyScanner scanner, boolean allCutout) {
        if (rawGeo == null || rawGeo.bones.isEmpty()) {
            boolean[] fallbackArray = scanner != null ? scanner.getResults() : new boolean[Math.max(1, textureCount)];
            return buildMesh(new GeoBone[0], new HashMap<>(), context, fallbackArray);
        }

        List<GeoBone> geoBones = new ArrayList<>();
        List<GeoModel.BakedBone> bakedBones = new ArrayList<>();
        Map<String, String> parentMap = new HashMap<>();

        for (RawYsmModel.RawBone rb : rawGeo.bones) {
            parentMap.put(rb.name, rb.parentName);
            geoBones.add(new GeoBone(rb.name, false, false, false, rb.pivot[0], rb.pivot[1], rb.pivot[2], rb.rotation[0], rb.rotation[1], rb.rotation[2]));

            GeoModel.BakedBone bb = new GeoModel.BakedBone();
            bb.name = rb.name;
            if (rb.name.startsWith("ysmGlow")) bb.glow = true;
            bb.pivotX = rb.pivot[0];
            bb.pivotY = rb.pivot[1];
            bb.pivotZ = rb.pivot[2];
            bb.rotX = rb.rotation[0];
            bb.rotY = rb.rotation[1];
            bb.rotZ = rb.rotation[2];
            bb.parentIdx = -1;

            // TODO: 优化算法

            boolean forceCull = allCutout;

            for (RawYsmModel.RawCube rc : rb.cubes) {
                GeoModel.BakedCube bc = new GeoModel.BakedCube();

                int validFaceCount = 0;
                boolean hasTranslucentFace = false;

                for (RawYsmModel.RawFace rf : rc.faces) {
                    int faceState = scanner != null ? scanner.scan(rf) : TranslucencyScanner.STATE_OPAQUE;

                    if (faceState == TranslucencyScanner.STATE_INVISIBLE) {
                        continue;
                    }

                    if (faceState == TranslucencyScanner.STATE_TRANSLUCENT) {
                        hasTranslucentFace = true;
                    }

                    if (!forceCull && isNegativeSizedFace(rf)) {
                        forceCull = true;
                    }

                    GeoModel.BakedQuad bq = new GeoModel.BakedQuad();
                    bq.normal = new Vector3f(rf.normal[0], rf.normal[1], rf.normal[2]);
                    bq.positions = new Vector3f[4];
                    bq.uvs = new Vector2f[4];
                    for (int i = 0; i < 4; i++) {
                        float px = rf.positions[i][0];
                        float py = rf.positions[i][1];
                        float pz = rf.positions[i][2];

                        bq.positions[i] = new Vector3f(px, py, pz);
                        bq.uvs[i] = new Vector2f(rf.u[i], rf.v[i]);
                    }
                    bc.quads.add(bq);
                    validFaceCount++;
                }

                boolean isZeroThickness = true;
                if (!bc.quads.isEmpty()) {
                    Vector3f baseNormal = bc.quads.get(0).normal;
                    Vector3f basePos = bc.quads.get(0).positions[0];

                    for (GeoModel.BakedQuad q : bc.quads) {
                        for (int i = 0; i < 4; i++) {
                            Vector3f pos = q.positions[i];
                            float dx = pos.x - basePos.x;
                            float dy = pos.y - basePos.y;
                            float dz = pos.z - basePos.z;

                            float distance = dx * baseNormal.x + dy * baseNormal.y + dz * baseNormal.z;

                            if (Math.abs(distance) > 1e-3f) {
                                isZeroThickness = false;
                                break;
                            }
                        }
                        if (!isZeroThickness) break;
                    }
                } else {
                    isZeroThickness = false;
                }

                if (forceCull) {
                    bc.cullable = true;
                } else if (hasTranslucentFace) {
                    bc.cullable = false;
                } else if (isZeroThickness && validFaceCount > 1) {
                    bc.cullable = true;
                } else {
                    bc.cullable = validFaceCount >= 5;
                }

                if (!bc.quads.isEmpty()) {
                    bb.cubes.add(bc);
                }
            }
            bakedBones.add(bb);
        }

        // 回填父级索引
        for (GeoModel.BakedBone b : bakedBones) {
            String parentName = parentMap.get(b.name);
            if (parentName != null && !parentName.isEmpty()) {
                for (int i = 0; i < bakedBones.size(); i++) {
                    if (bakedBones.get(i).name.equals(parentName)) {
                        b.parentIdx = i;
                        break;
                    }
                }
            }
            if (b.name.equals("LeftArm")) b.partMask = 1;
            else if (b.name.equals("RightArm")) b.partMask = 2;
            else if (b.name.equals("Background")) b.partMask = 3;
            else if (b.parentIdx != -1) b.partMask = bakedBones.get(b.parentIdx).partMask;
            else b.partMask = 0;
        }

        boolean[] translucencyArray = scanner != null ? scanner.getResults() : new boolean[Math.max(1, textureCount)];
        GeoModel mesh = buildMesh(geoBones.toArray(new GeoBone[0]), parentMap, context, translucencyArray);

        mesh.bakedBones = bakedBones;
        if (NativeLibLoader.isLoaded()) mesh.buildNativeCache();
        return mesh;
    }

    private static Map<String, Animation> buildAnimations(RawYsmModel.RawAnimationFile animFile, boolean mergeMultilineExpr) {
        Map<String, Animation> result = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimation ra : animFile.animations.values()) {
            ILoopType loopMode = ILoopType.EDefaultLoopTypes.PLAY_ONCE;
            if (ra.loopMode == 1) loopMode = ILoopType.EDefaultLoopTypes.LOOP;
            else if (ra.loopMode == 3) loopMode = ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME;

            List<BoneAnimation> boneAnims = new ArrayList<>();
            for (RawYsmModel.RawBoneAnimation rba : ra.boneAnimations) {
//                if (rba.boneName.equals("gun")) {
//                    "".hashCode();
//                }
                List<BoneKeyFrame> rotFrames = parseKeyframes(rba.rotation, true);
                List<BoneKeyFrame> posFrames = parseKeyframes(rba.position, false);
                List<BoneKeyFrame> scaleFrames = parseKeyframes(rba.scale, false);
                boneAnims.add(new BoneAnimation(rba.boneName, rotFrames, posFrames, scaleFrames));
            }

            List<EventKeyFrame<String>> soundEffects = new ArrayList<>();
            for (RawYsmModel.RawSoundEffect rse : ra.soundEffects) {
                soundEffects.add(new EventKeyFrame<>(rse.timestamp * 20.0f, rse.effectName));
            }

            List<EventKeyFrame<IValue[]>> timelineEvents = new ArrayList<>();
            for (RawYsmModel.RawTimelineEvent rte : ra.timelineEvents) {
                List<IValue> values = parse(rte.events, mergeMultilineExpr);
                timelineEvents.add(new EventKeyFrame<>(rte.timestamp * 20.0f, values.toArray(new IValue[0])));
            }

            IValue blendWeight;
            if (ra.blendWeight instanceof Float)
                blendWeight = FloatValue.of((Float) ra.blendWeight);
            else if (ra.blendWeight instanceof String)
                try {
                    blendWeight = parse((String) ra.blendWeight);
                } catch (Exception e) {
                    blendWeight = null;
                }
            else blendWeight = null;

            Animation anim = new Animation(ra.name, ra.length * 20.0f, loopMode, blendWeight, null, null, null, boneAnims.toArray(new BoneAnimation[0]), soundEffects.toArray(new EventKeyFrame[0]), new ParticleEventKeyFrame[0], timelineEvents.toArray(new EventKeyFrame[0]));
            result.put(ra.name, anim);
        }
        return result;
    }

    private static List<BoneKeyFrame> parseKeyframes(List<RawYsmModel.RawKeyframe> frames, boolean isRotation) {
        List<RawBoneKeyFrame> builders = new ArrayList<>();
        for (RawYsmModel.RawKeyframe rk : frames) {
            RawBoneKeyFrame builder = new RawBoneKeyFrame();
            builder.startTick = rk.timestamp * 20.0f;
            builder.easingType = rk.interpolationMode == 2 ? EasingType.CATMULLROM : EasingType.LINEAR;
            builder.contiguous = !rk.hasPreData;

            if (rk.hasPreData) {
                assignToBuilder(builder, rk.preData, true);
                assignToBuilder(builder, rk.postData, false);
            } else {
                assignToBuilder(builder, rk.postData, true);
            }
            builders.add(builder);
        }
        return BoneKeyFrameProcessor.process(builders, isRotation);
    }

    private static void assignToBuilder(RawBoneKeyFrame builder, Object[] data, boolean isPre) {
        for (int axis = 0; axis < 3; axis++) {
            double dVal = 0.0;
            IValue iVal = null;
            Object val = data[axis];
            if (val instanceof Float) dVal = (Float) val;
            else if (val instanceof String) {
                try {
                    iVal = parse((String) val);
                } catch (Exception ignore) {
                }
            }
            if (isPre) {
                if (axis == 0) {
                    builder.preX = dVal;
                    builder.preXValue = iVal;
                } else if (axis == 1) {
                    builder.preY = dVal;
                    builder.preYValue = iVal;
                } else if (axis == 2) {
                    builder.preZ = dVal;
                    builder.preZValue = iVal;
                }
            } else {
                if (axis == 0) {
                    builder.postX = dVal;
                    builder.postXValue = iVal;
                } else if (axis == 1) {
                    builder.postY = dVal;
                    builder.postYValue = iVal;
                } else if (axis == 2) {
                    builder.postZ = dVal;
                    builder.postZValue = iVal;
                }
            }
        }
    }

    private static Map<String, AnimationController> buildControllers(Map<String, RawYsmModel.RawAnimationController> rawControllers, boolean mergeMultilineExpr) {
        Map<String, AnimationController> result = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationController rac : rawControllers.values()) {
            List<AnimationState> states = new ArrayList<>();
            for (RawYsmModel.RawControllerState rs : rac.states) {
                List<Pair<String, IValue>> animations = new ArrayList<>();
                for (Map.Entry<String, String> e : rs.animations.entrySet()) {
                    IValue blend = null;
                    if (!e.getValue().isEmpty()) {
                        try {
                            blend = parse(e.getValue());
                        } catch (Exception ignore) {
                        }
                    }
                    animations.add(Pair.of(e.getKey(), blend));
                }

                List<Pair<String, IValue>> transitions = new ArrayList<>();
                for (Map.Entry<String, String> e : rs.transitions.entrySet()) {
                    IValue condition = parse(e.getValue());
                    transitions.add(Pair.of(e.getKey(), condition));
                }

                List<IValue> onEntry = parse(rs.onEntry, mergeMultilineExpr);
                List<IValue> onExit = parse(rs.onExit, mergeMultilineExpr);

                IInterpolable blendTransition;
                if (!rs.blendTransitions.isEmpty()) {
                    float[] keys = new float[rs.blendTransitions.size()];
                    float[] values = new float[rs.blendTransitions.size()];
                    int i = 0;
                    for (Map.Entry<Float, Float> e : rs.blendTransitions.entrySet()) {
                        keys[i] = e.getKey();
                        values[i] = e.getValue();
                        i++;
                    }
                    blendTransition = new LinearKeyframeInterpolator(keys, values);
                } else {
                    blendTransition = new TicksInterpolator(rs.blendTransitionValue);
                }

                states.add(new AnimationState(rs.name, animations.toArray(new Pair[0]), transitions.toArray(new Pair[0]), rs.soundEffects.toArray(new String[0]), onEntry.toArray(new IValue[0]), onExit.toArray(new IValue[0]), blendTransition, rs.blendViaShortestPath));
            }
            result.put(rac.animationName,
                    new AnimationController(
                            rac.initialState.isEmpty() ? "default" : rac.initialState,
                            states.toArray(new AnimationState[0])
                    )
            );
        }
        return result;
    }

    public static ServerModelInfo buildModelInfo(RawYsmModel raw/*, String modelId*/) {
        RawYsmModel.RawMetadata rm = raw.metadata;
        List<AuthorInfo> authors = new ArrayList<>();
        for (RawYsmModel.RawMetadata.Author a : rm.authors) {
            authors.add(new AuthorInfo(a.name, a.role, new OrderedStringMap<>(new Object2ObjectArrayMap<>(a.contacts)), a.comment));
        }

        Metadata extraInfo = new Metadata(rm.name, rm.tips, new StringPair(rm.licenseType, rm.licenseDescription), authors.toArray(new AuthorInfo[0]), new OrderedStringMap<>(new Object2ObjectArrayMap<>(rm.links)));

        RawYsmModel.RawProperties rp = raw.properties;
        List<StringMapPair> classifyList = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationClassify rCls : rp.extraAnimationClassifies) {
            classifyList.add(new StringMapPair(rCls.id, new OrderedStringMap<>(new Object2ObjectArrayMap<>(rCls.extras))));
        }

        List<ExtraAnimationButtons> buttonsList = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationButton rBtn : rp.extraAnimationButtons) {
            List<AbstractConfig> metaList = new ArrayList<>();
            for (RawYsmModel.ConfigForm form : rBtn.forms) {
                if ("checkbox".equals(form.type)) {
                    metaList.add(new CheckboxConfig(form.title, form.description, form.defaultValue));
                } else if ("radio".equals(form.type)) {
                    metaList.add(new RadioConfig(form.title, form.description, form.defaultValue, new OrderedStringMap<>(new Object2ObjectArrayMap<>(form.labels))));
                } else if ("range".equals(form.type)) {
                    metaList.add(new RangeConfig(form.title, form.description, form.defaultValue, form.step, form.min, form.max));
                }
            }
            buttonsList.add(new ExtraAnimationButtons(rBtn.id, rBtn.name, rBtn.description, metaList.toArray(new AbstractConfig[0])));
        }
        ModelProperties properties = new ModelProperties(rp.heightScale, rp.widthScale, rp.defaultTexture, rp.previewAnimation, new OrderedStringMap<>(new Object2ObjectArrayMap<>(rp.extraAnimations)), buttonsList.toArray(new ExtraAnimationButtons[0]), classifyList.toArray(new StringMapPair[0]), rp.isFree, rp.renderLayersFirst, rp.disablePreviewRotation);

        int bones = 0;
        int cubes = 0;
        int faces = 0;
        if (raw.mainEntity.mainModel != null) {
            bones = raw.mainEntity.mainModel.bones.size();
            for (RawYsmModel.RawBone bone : raw.mainEntity.mainModel.bones) {
                cubes += bone.cubes.size();
                for (RawYsmModel.RawCube cube : bone.cubes) {
                    faces += cube.faces.size();
                }
            }
        }
        MainModelInfo stats = new MainModelInfo(bones, cubes, faces);

        RawYsmModel.RawFooter footer = raw.footer;
        return new ServerModelInfo(extraInfo,
                properties,
                stats,
                footer.version,
                rp.sha256 != null ? rp.sha256 : "",
                footer.extra, footer.time, footer.rand);
    }

    private static ModelExtraResourcesFile buildExtraResources(RawYsmModel raw) {
        Map<String, AudioTrackData> sounds = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.soundFiles.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue().data;
            AudioTrackData track = parseAudioTrackData(data);
            if (track != null) sounds.put(name, track);
        }

        Map<String, IValue> functions = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.functionFiles.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue().data;
            String molangScript = new String(data, StandardCharsets.UTF_8);
            try {
                functions.put(name, GeckoLibCache.getMolangParser().parseExpression(molangScript, true));
            } catch (Exception e) {
            }
        }

        Map<String, Map<String, String>> translations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawLanguageFile> entry : raw.languageFiles.entrySet()) {
            translations.put(entry.getKey(), entry.getValue().data);
        }

        return new ModelExtraResourcesFile(sounds, functions, translations);
    }

    private static AudioTrackData parseAudioTrackData(byte[] oggData) {
        if (oggData == null || oggData.length < 8) return null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(oggData);
            OggFile oggFile = new OggFile(bais);
            String header = new String(oggData, 0, Math.min(oggData.length, 100), StandardCharsets.US_ASCII);
            boolean isOpus = header.contains("OpusHead");

            AudioCodec codec = isOpus ? AudioCodec.OPUS : AudioCodec.VORBIS;
            int sampleRate;
            if (isOpus) {
                OpusFile opus = new OpusFile(oggFile);
                sampleRate = (int) opus.getInfo().getRate();
            } else {
                VorbisFile vorbis = new VorbisFile(oggFile);
                sampleRate = (int) vorbis.getInfo().getRate();
            }

            OggPacketReader reader = oggFile.getPacketReader();
            long durationSamples = 0;
            var packet = reader.getNextPacket();
            while (packet != null) {
                long granule = packet.getGranulePosition();
                if (granule > 0) durationSamples = granule;
                packet = reader.getNextPacket();
            }

            ByteBuffer directBuf = ByteBuffer.allocateDirect(oggData.length);
            directBuf.put(oggData);
            directBuf.flip();

            return new AudioTrackData(directBuf, codec.ordinal(), sampleRate, durationSamples);
        } catch (Exception e) {
            return null;
        }
    }

    private static ProjectileModelFiles[] buildExtraItemModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<ProjectileModelFiles> list = new ArrayList<>();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> entry : raw.projectiles.entrySet()) {
            RawYsmModel.RawSubEntity sub = entry.getValue();
            ProjectileModelFiles holder = buildSubEntityHolder(sub, context, 1, mergeMultilineExpr);
            list.add(holder);
        }
        return list.toArray(new ProjectileModelFiles[0]);
    }

    private static VehicleModelFiles[] buildExtraEntityModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<VehicleModelFiles> list = new ArrayList<>();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> entry : raw.vehicles.entrySet()) {
            RawYsmModel.RawSubEntity sub = entry.getValue();
            VehicleModelFiles wrapper = buildSubEntityWrapper(sub, context, 1, mergeMultilineExpr);
            list.add(wrapper);
        }
        return list.toArray(new VehicleModelFiles[0]);
    }

    private static ProjectileModelFiles buildSubEntityHolder(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        OuterFileTexture texture = null;
        TranslucencyScanner subScanner = null;

        if (!sub.textures.isEmpty()) {
            List<BufferedImage> imgList = new ArrayList<>();
            for(RawYsmModel.RawTexture rt : sub.textures.values()) {
                BufferedImage img = decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
                imgList.add(img);
                // 走 TextureCache：命中时跳过解码+重编码
                byte[] processedData = toPng(rt.data, rt.imageFormat, rt.width, rt.height);
                if (texture == null) {
                    texture = new OuterFileTexture(processedData);
                }
            }
            if (sub.model != null) {
                subScanner = new TranslucencyScanner(imgList.toArray(new BufferedImage[0]), textureCount);
            }
        }

        GeoModel mesh = buildMesh(sub.model, context, textureCount, subScanner, true);

        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : sub.animationFiles.entrySet()) {
            Map<String, Animation> fileAnims = buildAnimations(entry.getValue(), mergeMultilineExpr);
            allAnimations.putAll(fileAnims);
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);

        Map<String, AnimationController> controllerMap = new LinkedHashMap<>();
        if (sub.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : sub.animationControllerFiles) {
                if (file.controllers != null && !file.controllers.isEmpty()) {
                    controllerMap.putAll(buildControllers(file.controllers, mergeMultilineExpr));
                }
            }
        }
        AnimationControllerFile controllers = new AnimationControllerFile(controllerMap);

        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new ProjectileModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static VehicleModelFiles buildSubEntityWrapper(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        OuterFileTexture texture = null;
        TranslucencyScanner subScanner = null;

        if (!sub.textures.isEmpty()) {
            List<BufferedImage> imgList = new ArrayList<>();
            for(RawYsmModel.RawTexture rt : sub.textures.values()) {
                BufferedImage img = decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
                imgList.add(img);
                // 走 TextureCache：命中时跳过解码+重编码
                byte[] processedData = toPng(rt.data, rt.imageFormat, rt.width, rt.height);
                if (texture == null) {
                    texture = new OuterFileTexture(processedData);
                }
            }
            if (sub.model != null) {
                subScanner = new TranslucencyScanner(imgList.toArray(new BufferedImage[0]), textureCount);
            }
        }

        GeoModel mesh = buildMesh(sub.model, context, textureCount, subScanner, true);

        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationFile animFile : sub.animationFiles.values()) {
            Map<String, Animation> fileAnims = buildAnimations(animFile, mergeMultilineExpr);
            allAnimations.putAll(fileAnims);
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);

        Map<String, AnimationController> controllerMap = new LinkedHashMap<>();
        if (sub.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : sub.animationControllerFiles) {
                if (file.controllers != null && !file.controllers.isEmpty()) {
                    controllerMap.putAll(buildControllers(file.controllers, mergeMultilineExpr));
                }
            }
        }
        AnimationControllerFile controllers = new AnimationControllerFile(controllerMap);

        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new VehicleModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static Map<String, OuterFileTexture> buildExtraTextures(RawYsmModel raw) {
        Map<String, OuterFileTexture> result = new LinkedHashMap<>();
        for (RawYsmModel.RawImage img : raw.properties.backgroundImages) {
            if (img.name != null && !img.name.isEmpty()) {
                byte[] processedData = toPng(img.data, img.format, img.width, img.height);
                result.put(img.name, new OuterFileTexture(processedData));
            }
        }
        return result;
    }

    public static List<IValue> parse(List<String> array, boolean mergeMultilineExpr) {
        List<IValue> values = new ArrayList<>();

        if (!mergeMultilineExpr) {
            for (String expr : array) values.add(parse(expr));
            return values;
        }

        try {
            StringBuilder parserText = new StringBuilder();

            for (int i = 0; i < array.size(); i++) {
                parserText.append(array.get(i));
                if (i < array.size() - 1) {
                    parserText.append("\n");
                }
            }

            values.add(parse(parserText.toString()));
        } catch (Throwable ex) {
            values.add(FloatValue.ZERO);
        }
        return values;
    }

    /** Molang 表达式解析失败计数，用于发现绑定丢失等系统性问题。 */
    private static final java.util.concurrent.atomic.AtomicLong PARSE_FAIL_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /** 读取并清零解析失败计数（供扫描结束时汇总输出）。 */
    public static long drainParseFailCount() {
        return PARSE_FAIL_COUNT.getAndSet(0);
    }

    public static IValue parse(String str) {
        try {
            return GeckoLibCache.getMolangParser().parseExpression(str, false);
        } catch (Throwable ex) {
            // 原实现静默吞掉异常并返回 0，导致"动画全部静止"这类问题无从排查。
            // 这里记数，并在前几次打印具体表达式。
            long n = PARSE_FAIL_COUNT.incrementAndGet();
            if (n <= 5) {
                com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.warn(
                        "[YSM] Molang parse failed ({}): {}", n, str, ex);
            }
            return FloatValue.ZERO;
        }
    }

    private static String[] buildPath(String targetBone, Map<String, String> parentMap) {
        if (!parentMap.containsKey(targetBone)) {
            return new String[0];
        }
        List<String> path = new ArrayList<>();
        String current = targetBone;
        while (current != null && !current.isEmpty()) {
            path.add(current);
            current = parentMap.get(current);
        }
        Collections.reverse(path);
        return path.toArray(new String[0]);
    }

    private static String[][] buildBoneNameArrays(Map<String, String> parentMap) {
        String[][] arrays = new String[35][];

        // 模型骨骼大全
        String[] targetLocators = new String[]{
                "LeftHandLocator",
                "RightHandLocator",
                "ElytraLocator",
                "PistolLocator",
                "RifleLocator",
                "LeftWaistLocator",
                "RightWaistLocator",
                "LeftShoulderLocator",
                "RightShoulderLocator",
                "BladeLocator",
                "SheathLocator",
                "Head",
                "BackpackLocator",
                "LeftHandLocator2",
                "LeftHandLocator3",
                "LeftHandLocator4",
                "LeftHandLocator5",
                "LeftHandLocator6",
                "LeftHandLocator7",
                "LeftHandLocator8",
                "RightHandLocator2",
                "RightHandLocator3",
                "RightHandLocator4",
                "RightHandLocator5",
                "RightHandLocator6",
                "RightHandLocator7",
                "RightHandLocator8",
                "PassengerLocator",
                "PassengerLocator2",
                "PassengerLocator3",
                "PassengerLocator4",
                "PassengerLocator5",
                "PassengerLocator6",
                "PassengerLocator7",
                "PassengerLocator8"
        };

        for (int i = 0; i < arrays.length; i++) {
            if (targetLocators[i] != null && !targetLocators[i].isEmpty()) {
                arrays[i] = buildPath(targetLocators[i], parentMap);
            } else {
                arrays[i] = new String[0];
            }
        }

        return arrays;
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, boolean[] translucencyArray) {
        String[][] boneNameArrays = buildBoneNameArrays(parentMap);
        boolean[] flags = new boolean[]{parentMap.containsKey("LeftArm"), parentMap.containsKey("RightArm"), parentMap.containsKey("Background")};
        return new GeoModel(bones, boneNameArrays, flags, context, translucencyArray);
    }

    public static OrderedStringMap<String, OuterFileTexture> buildTextureMap(Map<String, OuterFileTexture> textures) {
        if (textures.isEmpty()) {
            return new OrderedStringMap<>(new String[0], new OuterFileTexture[0]);
        }
        String[] keys = textures.keySet().toArray(new String[0]);
        OuterFileTexture[] values = textures.values().toArray(new OuterFileTexture[0]);
        return new OrderedStringMap<>(keys, values);
    }

    public static GeometryDescription buildContext(RawYsmModel.RawGeometry model) {
        return new GeometryDescription(
                model.identifier,
                model.textureWidth, // default texture width ratio
                model.textureHeight, // default texture height ratio
                model.visibleBoundsWidth, // offset X
                model.visibleBoundsHeight, // offset Y
                IntStream.range(0, model.visibleBoundsOffset.length)
                        .mapToDouble(i -> model.visibleBoundsOffset[i])
                        .toArray()
        );
    }

    private static boolean isNegativeSizedFace(RawYsmModel.RawFace f) {
        float[] p0 = f.positions[0];
        float[] p1 = f.positions[1];
        float[] p2 = f.positions[2];

        float ax = p1[0] - p0[0];
        float ay = p1[1] - p0[1];
        float az = p1[2] - p0[2];

        float bx = p2[0] - p0[0];
        float by = p2[1] - p0[1];
        float bz = p2[2] - p0[2];

        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        float len2 = nx * nx + ny * ny + nz * nz;
        if (len2 <= 1e-10f) {
            float[] p3 = f.positions[3];

            bx = p3[0] - p0[0];
            by = p3[1] - p0[1];
            bz = p3[2] - p0[2];

            nx = ay * bz - az * by;
            ny = az * bx - ax * bz;
            nz = ax * by - ay * bx;

            len2 = nx * nx + ny * ny + nz * nz;
            if (len2 <= 1e-10f) {
                return false;
            }
        }

        float dot = nx * f.normal[0] + ny * f.normal[1] + nz * f.normal[2];
        return dot < -1e-5f;
    }
}
