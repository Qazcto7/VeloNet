package io.github.qazcto7.velonet.mod.shared.network.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import java.lang.reflect.Method;
import java.util.List;
import io.github.qazcto7.velonet.mod.shared.network.util.PipelineUtil;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.VarInt;

public class VeloNetCompressDecoder extends CompressionDecoder {
    private static final int MAXIMUM_UNCOMPRESSED_LENGTH = 0x800000;
    private static final int HARD_MAXIMUM_UNCOMPRESSED_LENGTH = 0x8000000;
    private static final int ABSOLUTE_MAX_CAP = 0x10000000; // 256 MiB
    private static final int COMPRESSION_FRAME_OVERHEAD = 64 * 1024; // +64 KiB margin for incompressible zlib expansion

    private static final int CACHED_MAX_UNCOMPRESSED_CAP;
    private static final int CACHED_MAX_FRAME_CAP;

    static {
        Method packetFixerDecoderSizeMethod = null;
        try {
            Class<?> configCls = Class.forName("dev.tonimatas.packetfixer.util.Config");
            packetFixerDecoderSizeMethod = configCls.getMethod("getDecoderSize");
        } catch (Throwable ignored) {
        }

        int defaultCap = (Boolean.getBoolean("velonet.permit-oversized-packets")
                || Boolean.getBoolean("krypton.permit-oversized-packets"))
                ? HARD_MAXIMUM_UNCOMPRESSED_LENGTH
                : MAXIMUM_UNCOMPRESSED_LENGTH;
        int cap = defaultCap;
        if (packetFixerDecoderSizeMethod != null) {
            try {
                Object result = packetFixerDecoderSizeMethod.invoke(null);
                if (result instanceof Integer intVal) {
                    // PacketFixer validation & clamping:
                    // If intVal <= 0 (e.g. -1 for unlimited), clamp to 256 MiB.
                    // Otherwise clamp between [8 MiB, 256 MiB] to prevent rogue Integer.MAX_VALUE or zero.
                    if (intVal <= 0) {
                        cap = ABSOLUTE_MAX_CAP;
                    } else {
                        cap = Math.clamp(intVal, MAXIMUM_UNCOMPRESSED_LENGTH, ABSOLUTE_MAX_CAP);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        CACHED_MAX_UNCOMPRESSED_CAP = cap;
        CACHED_MAX_FRAME_CAP = cap + COMPRESSION_FRAME_OVERHEAD;
    }

    private int threshold;
    private boolean validateDecompressed;
    private final VelocityCompressor compressor;

    public VeloNetCompressDecoder(int threshold, boolean validateDecompressed, VelocityCompressor compressor) {
        super(threshold, validateDecompressed);
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
        this.compressor = compressor;

        // Native zlib cleanup: vanilla's CompressionDecoder instantiates a native Inflater that is never used.
        // End it immediately to reclaim native zlib memory.
        if (this.inflater != null) {
            this.inflater.end();
        }
    }

    public static int getMaxUncompressedCap() {
        return CACHED_MAX_UNCOMPRESSED_CAP;
    }

    public static int getMaxFrameCap() {
        return CACHED_MAX_FRAME_CAP;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }

        int claimedUncompressedLength = VarInt.read(in);
        if (claimedUncompressedLength == 0) {
            out.add(in.readRetainedSlice(in.readableBytes()));
            return;
        }

        if (claimedUncompressedLength < 0) {
            throw new DecoderException("Badly compressed packet - negative uncompressed length of " + claimedUncompressedLength);
        }

        if (!in.isReadable()) {
            throw new DecoderException("Badly compressed packet - missing compressed payload for length " + claimedUncompressedLength);
        }

        int maxCap = getMaxUncompressedCap();
        if (this.validateDecompressed) {
            if (claimedUncompressedLength < this.threshold) {
                throw new DecoderException("Badly compressed packet - size of " + claimedUncompressedLength + " is below server threshold of " + this.threshold);
            }
            if (claimedUncompressedLength > maxCap) {
                throw new DecoderException("Badly compressed packet - size of " + claimedUncompressedLength + " is larger than protocol maximum of " + maxCap);
            }
        } else {
            int unvalidatedCap = Math.max(HARD_MAXIMUM_UNCOMPRESSED_LENGTH, maxCap);
            if (claimedUncompressedLength > unvalidatedCap) {
                throw new DecoderException("Badly compressed packet - size of " + claimedUncompressedLength + " exceeds hard limit of " + unvalidatedCap);
            }
        }

        // DoS & Zip-bomb guard: DEFLATE theoretical maximum compression ratio is ~1032:1.
        // Reject packets claiming an impossible expansion ratio before allocating uncompressed buffers.
        if (claimedUncompressedLength > (long) in.readableBytes() * 1032L + 1024L) {
            throw new DecoderException("Badly compressed packet - claimed size " + claimedUncompressedLength
                    + " exceeds theoretical DEFLATE maximum for " + in.readableBytes() + " compressed bytes");
        }

        ByteBuf compatibleIn = PipelineUtil.ensureCompatibleSingleBuffer(ctx.alloc(), this.compressor, in);
        try {
            ByteBuf uncompressed = MoreByteBufUtils.preferredBuffer(ctx.alloc(), this.compressor, claimedUncompressedLength);
            boolean added = false;
            try {
                this.compressor.inflate(compatibleIn, uncompressed, claimedUncompressedLength);
                out.add(uncompressed);
                added = true;
                in.skipBytes(in.readableBytes());
            } finally {
                if (!added) {
                    uncompressed.release();
                }
            }
        } finally {
            compatibleIn.release();
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        this.compressor.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        this.compressor.close();
    }

    @Override
    public void setThreshold(int threshold, boolean validateDecompressed) {
        super.setThreshold(threshold, validateDecompressed);
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
    }
}
