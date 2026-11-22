package io.github.qazcto7.velonet.mixin.shared.network.pipeline.prepender;

import io.netty.channel.ChannelOutboundHandler;
import io.github.qazcto7.velonet.mod.shared.network.pipeline.MinecraftVarintPrepender;
import net.minecraft.network.Connection;
import net.minecraft.network.LocalFrameEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Overwrite
    private static ChannelOutboundHandler createFrameEncoder(boolean local) {
        if (local) {
            return new LocalFrameEncoder();
        }
        return MinecraftVarintPrepender.INSTANCE;
    }
}
