package rip.ysm.api.network.fabric;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record YSMPayload(byte[] data) implements CustomPacketPayload {

    public static Type<YSMPayload> TYPE;

    public static StreamCodec<RegistryFriendlyByteBuf, YSMPayload> CODEC;

    public static void init(Identifier channelId) {
        TYPE = new Type<>(channelId);
        CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.data.length);
                    buf.writeBytes(payload.data);
                },
                buf -> {
                    int len = buf.readVarInt();
                    byte[] arr = new byte[len];
                    buf.readBytes(arr);
                    return new YSMPayload(arr);
                }
        );
    }

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(this.data));
        return buf;
    }

    public static YSMPayload fromBuf(FriendlyByteBuf buf) {
        int readable = buf.readableBytes();
        byte[] arr = new byte[readable];
        buf.readBytes(arr);
        return new YSMPayload(arr);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
