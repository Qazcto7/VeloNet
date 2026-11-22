package io.github.qazcto7.velonet.mixin.shared.network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.github.qazcto7.velonet.mod.shared.network.compression.VeloNetCompressDecoder;
import io.github.qazcto7.velonet.mod.shared.network.util.WellKnownExceptions;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.VarInt;
import net.minecraft.network.Varint21FrameDecoder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Varint21FrameDecoder.class)
public class Varint21FrameDecoderMixin {
    @Shadow
    @Nullable
    private BandwidthDebugMonitor monitor;

    @Mutable
    @Shadow
    @Nullable
    private ByteBuf helperBuf;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void velonet$releaseUnusedHelperBuf(BandwidthDebugMonitor monitor, CallbackInfo ci) {
        // VeloNet decodes VarInt frames in-place without helperBuf; release vanilla's direct buffer immediately.
        // Also prevents leaking the constructor's buffer when PacketFixer overwrites helperBuf at HEAD of decode().
        this.velonet$releaseHelperBuf();
    }

    @Overwrite
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // PACKETFIXER COMPATIBILITY:
        // PacketFixer injects at HEAD of decode() and allocates a fresh Unpooled.directBuffer(...) into helperBuf
        // on every invocation. Release it immediately since VeloNet decodes VarInt frames in-place.
        this.velonet$releaseHelperBuf();

        if (!ctx.channel().isActive()) {
            in.clear();
            return;
        }

        in.markReaderIndex();
        int preIndex = in.readerIndex();
        int length = readRawVarInt21(in);
        if (preIndex == in.readerIndex()) {
            return;
        }

        if (length < 0) {
            throw WellKnownExceptions.BAD_LENGTH_CACHED;
        }
        if (length > VeloNetCompressDecoder.getMaxFrameCap()) {
            throw WellKnownExceptions.FRAME_TOO_LARGE_CACHED;
        }
        if (length == 0) {
            throw WellKnownExceptions.ZERO_LENGTH_CACHED;
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        if (this.monitor != null) {
            this.monitor.onReceive(length + VarInt.getByteSize(length));
        }

        out.add(in.readRetainedSlice(length));
    }

    @Overwrite
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        this.velonet$releaseHelperBuf();
    }

    @Unique
    private void velonet$releaseHelperBuf() {
        if (this.helperBuf != null) {
            if (this.helperBuf.refCnt() > 0) {
                this.helperBuf.release();
            }
            this.helperBuf = null;
        }
    }

    @Unique
    private static int readRawVarInt21(ByteBuf buffer) {
        if (buffer.readableBytes() < 4) {
            return readRawVarintSmallBuf(buffer);
        }
        int wholeOrMore = buffer.getIntLE(buffer.readerIndex());
        int atStop = ~wholeOrMore & 0x808080;
        if (atStop == 0) {
            // First 3 bytes have continuation bit set. Handles 4-byte and 5-byte VarInts
            // (e.g. packets > 2MB allowed by PacketFixer).
            return readRawVarintLarge(buffer, wholeOrMore);
        }
        int bitsToKeep = Integer.numberOfTrailingZeros(atStop) + 1;
        buffer.skipBytes(bitsToKeep >> 3);
        int preservedBytes = wholeOrMore & (atStop ^ (atStop - 1));
        preservedBytes = (preservedBytes & 0x7F007F) | ((preservedBytes & 0x7F00) >> 1);
        preservedBytes = (preservedBytes & 0x3FFF) | ((preservedBytes & 0x3FFF0000) >> 2);
        return preservedBytes;
    }

    @Unique
    private static int readRawVarintLarge(ByteBuf buffer, int wholeOrMore) {
        // Decode byte 0, 1, 2
        int result = (wholeOrMore & 0x7F)
                | (((wholeOrMore >> 8) & 0x7F) << 7)
                | (((wholeOrMore >> 16) & 0x7F) << 14);

        // Check byte 3 (4th byte)
        if ((wholeOrMore & 0x80000000) == 0) {
            // 4-byte VarInt terminates at byte 3
            result |= (wholeOrMore >>> 24) << 21;
            buffer.skipBytes(4);
            return result;
        }

        // 5-byte VarInt: needs the 5th byte
        if (buffer.readableBytes() < 5) {
            // Wait for 5th byte to arrive without advancing readerIndex
            return 0;
        }

        byte byte4 = buffer.getByte(buffer.readerIndex() + 4);
        if ((byte4 & ~0x07) != 0) {
            throw WellKnownExceptions.VARINT_BIG_CACHED;
        }

        result |= ((wholeOrMore >>> 24) & 0x7F) << 21;
        result |= ((int) byte4 & 0x7F) << 28;
        buffer.skipBytes(5);
        return result;
    }

    @Unique
    private static int readRawVarintSmallBuf(ByteBuf buffer) {
        if (!buffer.isReadable()) {
            return 0;
        }
        buffer.markReaderIndex();
        byte tmp = buffer.readByte();
        if (tmp >= 0) {
            return tmp;
        }
        int result = tmp & 0x7F;
        if (!buffer.isReadable()) {
            buffer.resetReaderIndex();
            return 0;
        }
        tmp = buffer.readByte();
        if (tmp >= 0) {
            return result | (tmp << 7);
        }
        result |= (tmp & 0x7F) << 7;
        if (!buffer.isReadable()) {
            buffer.resetReaderIndex();
            return 0;
        }
        tmp = buffer.readByte();
        if (tmp >= 0) {
            return result | (tmp << 14);
        }
        // Incomplete VarInt: byte 3 has continuation bit set, but buffer has < 4 bytes available.
        // Wait for more bytes to arrive.
        buffer.resetReaderIndex();
        return 0;
    }
}
