package io.github.qazcto7.velonet.mod.shared.network.util;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.nio.channels.ClosedChannelException;

/**
 * Shared Netty pipeline safety and buffer compatibility utilities.
 */
public final class PipelineUtil {
    private PipelineUtil() {
    }

    /**
     * Releases {@code msg} and fails a non-void {@code promise} when the channel is inactive,
     * preventing {@code MessageToMessageEncoder} from throwing {@code EncoderException} on empty output.
     *
     * @return {@code true} if the write was dropped because the channel is inactive
     */
    public static boolean dropWriteIfInactive(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (ctx.channel().isActive()) {
            return false;
        }
        ReferenceCountUtil.release(msg);
        if (!promise.isVoid()) {
            promise.tryFailure(new ClosedChannelException());
        }
        return true;
    }

    /**
     * Ensures {@code in} is compatible with {@code cipher} and exclusively owned ({@code refCnt() == 1})
     * so in-place encryption/decryption never mutates shared packet buffers.
     */
    public static ByteBuf ensureMutableCompatibleBuffer(
            ByteBufAllocator alloc,
            VelocityCipher cipher,
            ByteBuf in
    ) {
        if (in.refCnt() == 1) {
            return MoreByteBufUtils.ensureCompatible(alloc, cipher, in);
        }
        ByteBuf copy = MoreByteBufUtils.preferredBuffer(alloc, cipher, in.readableBytes());
        try {
            copy.writeBytes(in, in.readerIndex(), in.readableBytes());
            return copy;
        } catch (Throwable t) {
            copy.release();
            throw t;
        }
    }

    /**
     * Ensures {@code in} is compatible with {@code compressor} and backed by a single NIO buffer,
     * which {@code JavaVelocityCompressor} requires via {@code nioBufferCount() == 1}.
     */
    public static ByteBuf ensureCompatibleSingleBuffer(
            ByteBufAllocator alloc,
            VelocityCompressor compressor,
            ByteBuf in
    ) {
        ByteBuf compatible = MoreByteBufUtils.ensureCompatible(alloc, compressor, in);
        if (compatible.nioBufferCount() == 1) {
            return compatible;
        }
        try {
            ByteBuf contiguous = MoreByteBufUtils.preferredBuffer(alloc, compressor, compatible.readableBytes());
            try {
                contiguous.writeBytes(compatible, compatible.readerIndex(), compatible.readableBytes());
                return contiguous;
            } catch (Throwable t) {
                contiguous.release();
                throw t;
            }
        } finally {
            compatible.release();
        }
    }
}
