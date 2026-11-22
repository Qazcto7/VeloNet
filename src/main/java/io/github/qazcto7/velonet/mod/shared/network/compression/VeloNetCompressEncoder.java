package io.github.qazcto7.velonet.mod.shared.network.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.github.qazcto7.velonet.mod.shared.network.util.PipelineUtil;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.VarInt;

public class VeloNetCompressEncoder extends CompressionEncoder {
    private int threshold;
    private final VelocityCompressor compressor;

    public VeloNetCompressEncoder(int threshold, VelocityCompressor compressor) {
        super(threshold);
        this.threshold = threshold;
        this.compressor = compressor;

        // Native zlib cleanup: vanilla's CompressionEncoder instantiates a native Deflater that is never used.
        // End it immediately to reclaim native zlib memory.
        if (this.deflater != null) {
            this.deflater.end();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (PipelineUtil.dropWriteIfInactive(ctx, msg, promise)) {
            return;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int uncompressedLength = msg.readableBytes();
        int maxCap = VeloNetCompressDecoder.getMaxUncompressedCap();
        if (uncompressedLength > maxCap) {
            throw new IllegalArgumentException("Packet too big (is " + uncompressedLength + ", should be less than " + maxCap + ")");
        }
        if (uncompressedLength == 0 || uncompressedLength < this.threshold) {
            VarInt.write(out, 0);
            out.writeBytes(msg);
        } else {
            VarInt.write(out, uncompressedLength);
            ByteBuf compatibleIn;
            try {
                compatibleIn = PipelineUtil.ensureCompatibleSingleBuffer(ctx.alloc(), this.compressor, msg);
            } catch (Exception e) {
                throw new EncoderException("Failed to ensure compatible buffer for compression", e);
            }
            try {
                this.compressor.deflate(compatibleIn, out);
            } catch (Exception e) {
                throw new EncoderException("Failed to deflate packet payload", e);
            } finally {
                compatibleIn.release();
            }
        }
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) throws Exception {
        int initialBufferSize = msg.readableBytes() + 32;
        return MoreByteBufUtils.preferredBuffer(ctx.alloc(), this.compressor, initialBufferSize);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        this.compressor.close();
    }

    @Override
    public void setThreshold(int threshold) {
        super.setThreshold(threshold);
        this.threshold = threshold;
    }
}
