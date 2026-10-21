package com.elfmcys.yesstevemodel.audio;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OggVorbisAudioStream implements IAudioStreamSupport {

    private static final ByteBuffer EMPTY_BUFFER = BufferUtils.createByteBuffer(0);

    private final JOrbisAudioStream oggStream;

    private final AudioFormat audioFormat;

    private final int channels;

    private final FloatArrayList pendingSamples = new FloatArrayList();

    @Nullable
    private final AudioCacheBuilder cacheBuilder;

    private volatile boolean isClosed;

    private boolean isEndOfStream;

    public OggVorbisAudioStream(ByteBuffer byteBuffer, @Nullable AudioCacheBuilder cacheBuilder) throws UnsupportedAudioFileException, IOException {
        this.oggStream = new JOrbisAudioStream(new ByteBufInputStream(Unpooled.wrappedBuffer(byteBuffer)));
        AudioFormat sourceFormat = this.oggStream.getFormat();
        this.channels = sourceFormat.getChannels();
        if (this.channels != 1 && this.channels != 2) {
            throw new UnsupportedAudioFileException();
        }
        this.audioFormat = new AudioFormat(sourceFormat.getSampleRate(), 16, 1, true, false);
        this.cacheBuilder = cacheBuilder;
    }

    @NotNull
    public AudioFormat getFormat() {
        return this.audioFormat;
    }

    @NotNull
    public ByteBuffer read(int requestedBytes) throws IOException {
        if (this.isEndOfStream || this.isClosed) {
            return EMPTY_BUFFER;
        }
        int requestedMonoSamples = requestedBytes / 2;
        int neededFloatSamples = requestedMonoSamples * this.channels;
        while (this.pendingSamples.size() < neededFloatSamples) {
            boolean more = this.oggStream.readChunk(this.pendingSamples::add);
            if (!more) {
                this.isEndOfStream = true;
                break;
            }
        }
        int availableFloats = Math.min(this.pendingSamples.size(), neededFloatSamples);
        int outMonoSamples = availableFloats / this.channels;
        int outBytes = outMonoSamples * 2;
        if (outBytes <= 0) {
            if (this.cacheBuilder != null && this.isEndOfStream) {
                this.cacheBuilder.flushToCache();
            }
            return EMPTY_BUFFER;
        }
        ByteBuffer out = BufferUtils.createByteBuffer(outBytes).order(ByteOrder.nativeOrder());
        if (this.channels == 1) {
            for (int i = 0; i < outMonoSamples; i++) {
                float v = this.pendingSamples.getFloat(i);
                out.putShort(floatToPcm16(v));
            }
        } else {
            for (int i = 0; i < outMonoSamples; i++) {
                float l = this.pendingSamples.getFloat(i * 2);
                float r = this.pendingSamples.getFloat((i * 2) + 1);
                out.putShort(floatToPcm16((l + r) * 0.5f));
            }
        }
        this.pendingSamples.removeElements(0, outMonoSamples * this.channels);
        out.flip();
        if (this.cacheBuilder != null) {
            this.cacheBuilder.appendAudio(out.duplicate());
            if (this.isEndOfStream) {
                this.cacheBuilder.flushToCache();
            }
        }
        return out;
    }

    private static short floatToPcm16(float v) {
        if (v > 1.0f) {
            v = 1.0f;
        } else if (v < -1.0f) {
            v = -1.0f;
        }
        return (short) Math.round(v * 32767.0f);
    }

    public void close() throws IOException {
        if (!this.isClosed) {
            this.oggStream.close();
            this.isClosed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return this.isClosed;
    }
}
