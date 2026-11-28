package io.github.qazcto7.velonet.mixin.shared.network.pipeline.prepender;

import io.netty.channel.ChannelOutboundHandler;
import io.github.qazcto7.velonet.mod.shared.network.pipeline.MinecraftVarintPrepender;
import net.minecraft.network.Connection;
import net.minecraft.network.LocalFrameEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "createFrameEncoder", at = @At("HEAD"), cancellable = true)
    private static void velonet$createFrameEncoder(boolean local, CallbackInfoReturnable<ChannelOutboundHandler> cir) {
        cir.setReturnValue(local ? new LocalFrameEncoder() : MinecraftVarintPrepender.INSTANCE);
    }
}
