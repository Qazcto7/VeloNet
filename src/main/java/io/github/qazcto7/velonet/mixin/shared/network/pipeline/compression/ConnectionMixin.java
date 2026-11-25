package io.github.qazcto7.velonet.mixin.shared.network.pipeline.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.compression.VelocityCompressorFactory;
import com.velocitypowered.natives.util.Natives;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.util.List;
import java.util.function.Function;
import me.steinborn.krypton.mod.shared.misc.KryptonPipelineEvent;
import io.github.qazcto7.velonet.mod.shared.network.compression.VeloNetCompressDecoder;
import io.github.qazcto7.velonet.mod.shared.network.compression.VeloNetCompressEncoder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Unique
    private static final boolean HAS_VIAFABRICPLUS = isViaFabricPlusLoaded();

    @Shadow
    private Channel channel;

    @Inject(method = "setupCompression", at = @At("HEAD"), cancellable = true)
    public void setupCompression(int threshold, boolean validateDecompressed, CallbackInfo ci) {
        if (this.channel == null) {
            ci.cancel();
            return;
        }

        ChannelPipeline pipeline = this.channel.pipeline();

        if (threshold >= 0) {
            boolean updated = false;
            ChannelHandler decompressHandler = pipeline.get("decompress");
            if (decompressHandler instanceof VeloNetCompressDecoder kryptonDecoder) {
                kryptonDecoder.setThreshold(threshold, validateDecompressed);
                updated = true;
            } else {
                if (decompressHandler != null) {
                    pipeline.remove("decompress");
                }
                installCompressorHandler(
                        pipeline,
                        "splitter",
                        "decompress",
                        compressor -> new VeloNetCompressDecoder(threshold, validateDecompressed, compressor)
                );
            }

            ChannelHandler compressHandler = pipeline.get("compress");
            if (compressHandler instanceof VeloNetCompressEncoder kryptonEncoder) {
                kryptonEncoder.setThreshold(threshold);
                updated = true;
            } else {
                if (compressHandler != null) {
                    pipeline.remove("compress");
                }
                installCompressorHandler(
                        pipeline,
                        "prepender",
                        "compress",
                        compressor -> new VeloNetCompressEncoder(threshold, compressor)
                );
            }

            // Ensure pipeline ordering is 100% correct relative to ViaFabricPlus handlers
            reorderForViaFabricPlus(pipeline);

            // Fire pipeline events for ViaFabricPlus and any listeners.
            // Guard COMPRESSION_ENABLED when ViaFabricPlus is present on connections without Via handlers
            // (e.g. LAN server connections), since ViaFabricPlus's integration MixinConnection calls
            // ViaChannelInitializer.reorderPipeline() without checking for via-decoder/via-encoder.
            if (updated) {
                pipeline.fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_THRESHOLD_UPDATED);
            }
            boolean hasViaHandlers = pipeline.context("via-decoder") != null && pipeline.context("via-encoder") != null;
            if (!HAS_VIAFABRICPLUS || hasViaHandlers) {
                pipeline.fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
            }
        } else {
            if (pipeline.context("decompress") != null) {
                pipeline.remove("decompress");
            }
            if (pipeline.context("compress") != null) {
                pipeline.remove("compress");
            }
            pipeline.fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_DISABLED);
        }

        ci.cancel();
    }

    @Unique
    private static void installCompressorHandler(
            ChannelPipeline pipeline,
            String afterName,
            String handlerName,
            Function<VelocityCompressor, ChannelHandler> handlerFactory
    ) {
        VelocityCompressor compressor = ((VelocityCompressorFactory) Natives.compress.get()).create(4);
        boolean attached = false;
        try {
            ChannelHandler handler = handlerFactory.apply(compressor);
            if (pipeline.context(afterName) != null) {
                pipeline.addAfter(afterName, handlerName, handler);
            } else {
                pipeline.addFirst(handlerName, handler);
            }
            attached = true;
        } finally {
            if (!attached) {
                compressor.close();
            }
        }
    }

    @Unique
    private static void reorderForViaFabricPlus(ChannelPipeline pipeline) {
        if (pipeline.context("via-decoder") == null && pipeline.context("via-encoder") == null) {
            return;
        }
        // Inbound: "decompress" MUST sit BEFORE "via-decoder"
        ensureAfter(pipeline, "decompress", "via-decoder");
        // Outbound: In pipeline names list (head to tail), "compress" must appear before "via-encoder"
        ensureAfter(pipeline, "compress", "via-encoder");
        // ViaFabricPlus 5.1.3+ Flow Control: "via-flow-control" MUST sit AFTER "via-decoder"
        ensureAfter(pipeline, "via-decoder", "via-flow-control");
    }

    @Unique
    private static void ensureAfter(ChannelPipeline pipeline, String anchorName, String targetName) {
        ChannelHandlerContext targetCtx = pipeline.context(targetName);
        if (pipeline.context(anchorName) == null || targetCtx == null) {
            return;
        }
        List<String> names = pipeline.names();
        int anchorIdx = names.indexOf(anchorName);
        int targetIdx = names.indexOf(targetName);
        if (anchorIdx == -1 || targetIdx == -1 || targetIdx >= anchorIdx) {
            return;
        }

        ChannelHandler existing = targetCtx.handler();
        ChannelHandler toInsert = existing;
        // Non-@Sharable handlers (like ViaFabricPlus's NoReadFlowControlHandler extending FlowControlHandler)
        // cannot be re-added after removal in Netty; instantiate a fresh copy before removing when possible.
        if (existing instanceof ChannelHandlerAdapter adapter && !adapter.isSharable()) {
            try {
                toInsert = existing.getClass().getConstructor().newInstance();
            } catch (Throwable ignored) {
                return;
            }
        }

        if (pipeline.remove(targetName) != null && pipeline.context(anchorName) != null) {
            pipeline.addAfter(anchorName, targetName, toInsert);
        }
    }

    @Unique
    private static boolean isViaFabricPlusLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded("viafabricplus");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
