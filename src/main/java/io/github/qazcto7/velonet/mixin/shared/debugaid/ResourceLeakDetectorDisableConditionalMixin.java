package io.github.qazcto7.velonet.mixin.shared.debugaid;

import io.netty.util.ResourceLeakDetector;
import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SharedConstants.class)
public class ResourceLeakDetectorDisableConditionalMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lio/netty/util/ResourceLeakDetector;setLevel(Lio/netty/util/ResourceLeakDetector$Level;)V"))
    private static void clinit$resourceLeakDetectorDisableConditional(ResourceLeakDetector.Level level) {
        if (System.getProperty("io.netty.leakDetection.level") == null) {
            ResourceLeakDetector.setLevel(level);
        }
    }
}
