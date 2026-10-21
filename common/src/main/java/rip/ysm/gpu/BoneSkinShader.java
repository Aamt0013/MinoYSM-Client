package rip.ysm.gpu;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.util.log.ChatLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;

public final class BoneSkinShader {
    public static final int ssbo = 0;
    public static final int lightUboBinding = 1;
    public static final int projUboBinding = 2;
    private static int program = 0;
    private static int locModelView = -1;
    private static int locColor = -1;
    private static int locOverlay = -1;
    private static int locFogStart = -1;
    private static int locFogEnd = -1;
    private static int locFogColor = -1;
    private static int locFogShape = -1;
    private static int locAlphaMode = -1;
    private static boolean failed = false;

    public static synchronized boolean ensureCompiled() {
        if (program != 0) return true;
        if (failed) return false;
        RenderSystem.assertOnRenderThread();

        try {
            int vs = ShaderUtil.compileShaderFromResource(GL20.GL_VERTEX_SHADER, "/bone_skin.vsh");
            int fs = ShaderUtil.compileShaderFromResource(GL20.GL_FRAGMENT_SHADER, "/bone_skin.fsh");
            int prog = ShaderUtil.linkProgramWith(p -> {
                GL20.glBindAttribLocation(p, 0, "a_position");
                GL20.glBindAttribLocation(p, 1, "a_uv");
                GL20.glBindAttribLocation(p, 2, "a_normal");
                GL20.glBindAttribLocation(p, 3, "a_boneId");
                GL20.glBindAttribLocation(p, 4, "a_cullable");
            }, vs, fs);

            int ssboBlock = GL43.glGetProgramResourceIndex(prog, GL43.GL_SHADER_STORAGE_BLOCK, "BoneBlock");
            if (ssboBlock != GL43.GL_INVALID_INDEX) {
                GL43.glShaderStorageBlockBinding(prog, ssboBlock, ssbo);
            }

            locModelView = GL20.glGetUniformLocation(prog, "u_modelView");
            locColor = GL20.glGetUniformLocation(prog, "u_color");
            locOverlay = GL20.glGetUniformLocation(prog, "u_packedOverlay");
            locFogStart = GL20.glGetUniformLocation(prog, "u_fogStart");
            locFogEnd = GL20.glGetUniformLocation(prog, "u_fogEnd");
            locFogColor = GL20.glGetUniformLocation(prog, "u_fogColor");
            locFogShape = GL20.glGetUniformLocation(prog, "u_fogShape");
            locAlphaMode = GL20.glGetUniformLocation(prog, "u_alphaMode");

            int lightBlockIdx = GL31.glGetUniformBlockIndex(prog, "LightingBlock");
            if (lightBlockIdx != GL31.GL_INVALID_INDEX) {
                GL31.glUniformBlockBinding(prog, lightBlockIdx, lightUboBinding);
            }

            int projBlockIdx = GL31.glGetUniformBlockIndex(prog, "ProjectionBlock");
            if (projBlockIdx != GL31.GL_INVALID_INDEX) {
                GL31.glUniformBlockBinding(prog, projBlockIdx, projUboBinding);
            }

            int locSampler0 = GL20.glGetUniformLocation(prog, "Sampler0");
            int locSampler1 = GL20.glGetUniformLocation(prog, "Sampler1");
            int locSampler2 = GL20.glGetUniformLocation(prog, "Sampler2");
            GL20.glUseProgram(prog);
            if (locSampler0 >= 0) GL20.glUniform1i(locSampler0, 0);
            if (locSampler1 >= 0) GL20.glUniform1i(locSampler1, 1);
            if (locSampler2 >= 0) GL20.glUniform1i(locSampler2, 2);
            GL20.glUseProgram(0);

            program = prog;
            return true;
        } catch (Throwable t) {
            ChatLogger.INSTANCE.logFormatted("Failed to compile shader program, please check the log");
            YesSteveModel.LOGGER.error("Failed to compile shader program.", t);
            failed = true;
            return false;
        }
    }

    public static int program() {
        return program;
    }

    public static int locModelView() {
        return locModelView;
    }

    public static int locColor() {
        return locColor;
    }

    public static int locOverlay() {
        return locOverlay;
    }

    public static int locFogStart() {
        return locFogStart;
    }

    public static int locFogEnd() {
        return locFogEnd;
    }

    public static int locFogColor() {
        return locFogColor;
    }

    public static int locFogShape() {
        return locFogShape;
    }

    public static int locAlphaMode() {
        return locAlphaMode;
    }

}
