package io.github.qazcto7.velonet.mod.shared.network.pipeline;

import com.velocitypowered.natives.encryption.JavaVelocityCipher;
import com.velocitypowered.natives.util.Natives;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.util.List;
import io.github.qazcto7.velonet.mod.shared.network.util.PipelineUtil;
import net.minecraft.network.VarInt;

@ChannelHandler.Sharable
public class MinecraftVarintPrepender extends MessageToMessageEncoder<ByteBuf> {
    public static final MinecraftVarintPrepender INSTANCE = new MinecraftVarintPrepender();
    private static final boolean IS_JAVA_CIPHER = Natives.cipher.get() == JavaVelocityCipher.FACTORY;
    private static final int SMALL_PACKET_COALESCE_THRESHOLD = 128;
    private static final AttributeKey<Boolean> HAS_VIALEGACY = AttributeKey.valueOf("velonet:has_vialegacy");

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (PipelineUtil.dropWriteIfInactive(ctx, msg, promise)) {
            return;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        int length = msg.readableBytes();
        int varintLength = VarInt.getByteSize(length);

        // VIAFABRICPLUS PRE-NETTY COMPATIBILITY (1.6.4 and older):
        // PreNettyLengthRemover sits before prepender and expects a single unified buffer
        // containing [VarInt length][packet payload]. Emitting two buffers causes an IndexOutOfBoundsException.
        Attribute<Boolean> viaAttr = ctx.channel().attr(HAS_VIALEGACY);
        Boolean hasViaLegacy = viaAttr.get();
        if (hasViaLegacy == null) {
            hasViaLegacy = ctx.pipeline().context("vialegacy-pre-netty-length-remover") != null;
            viaAttr.set(hasViaLegacy);
        }

        // Single-buffer path for ViaLegacy pre-netty compatibility or tiny packets (avoids double cipher/buffer overhead).
        boolean viaLegacy = Boolean.TRUE.equals(hasViaLegacy);
        if (viaLegacy || length <= SMALL_PACKET_COALESCE_THRESHOLD) {
            int totalLength = varintLength + length;
            ByteBuf outBuf = viaLegacy
                    ? ctx.alloc().buffer(totalLength)
                    : (IS_JAVA_CIPHER ? ctx.alloc().heapBuffer(totalLength) : ctx.alloc().directBuffer(totalLength));
            boolean added = false;
            try {
                VarInt.write(outBuf, length);
                outBuf.writeBytes(msg);
                out.add(outBuf);
                added = true;
            } catch (Throwable t) {
                if (!added) {
                    outBuf.release();
                }
                throw t;
            }
            return;
        }

        // Standard high-performance zero-copy scatter/gather write:
        ByteBuf lenBuf = IS_JAVA_CIPHER ? ctx.alloc().heapBuffer(varintLength) : ctx.alloc().directBuffer(varintLength);
        boolean addedLen = false;
        boolean retainedMsg = false;
        try {
            VarInt.write(lenBuf, length);
            msg.retain();
            retainedMsg = true;
            out.add(lenBuf);
            addedLen = true;
            out.add(msg);
        } catch (Throwable t) {
            if (!addedLen) {
                lenBuf.release();
            }
            if (retainedMsg) {
                msg.release();
            }
            throw t;
        }
    }
}
