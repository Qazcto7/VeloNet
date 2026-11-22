package io.github.qazcto7.velonet.mixin.shared.network.pipeline.encryption;

import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.encryption.VelocityCipherFactory;
import com.velocitypowered.natives.util.Natives;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import me.steinborn.krypton.mod.shared.misc.KryptonPipelineEvent;
import io.github.qazcto7.velonet.mod.shared.network.ClientConnectionEncryptionExtension;
import io.github.qazcto7.velonet.mod.shared.network.pipeline.MinecraftCipherDecoder;
import io.github.qazcto7.velonet.mod.shared.network.pipeline.MinecraftCipherEncoder;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

@Mixin(Connection.class)
public class ConnectionMixin implements ClientConnectionEncryptionExtension {
    @Shadow
    private Channel channel;
    @Unique
    private boolean velonetEncryptionEnabled = false;

    @Override
    public void setupEncryption(SecretKey key) throws GeneralSecurityException {
        if (this.velonetEncryptionEnabled || this.channel == null || key == null) {
            return;
        }
        VelocityCipher decryption = ((VelocityCipherFactory) Natives.cipher.get()).forDecryption(key);
        VelocityCipher encryption;
        try {
            encryption = ((VelocityCipherFactory) Natives.cipher.get()).forEncryption(key);
        } catch (Throwable t) {
            decryption.close();
            throw t;
        }

        ChannelPipeline pipeline = this.channel.pipeline();
        try {
            if (pipeline.context("decrypt") != null) {
                pipeline.remove("decrypt");
            }
            if (pipeline.context("splitter") != null) {
                pipeline.addBefore("splitter", "decrypt", new MinecraftCipherDecoder(decryption));
            } else {
                pipeline.addFirst("decrypt", new MinecraftCipherDecoder(decryption));
            }

            if (pipeline.context("encrypt") != null) {
                pipeline.remove("encrypt");
            }
            if (pipeline.context("prepender") != null) {
                pipeline.addBefore("prepender", "encrypt", new MinecraftCipherEncoder(encryption));
            } else {
                pipeline.addFirst("encrypt", new MinecraftCipherEncoder(encryption));
            }

            pipeline.fireUserEventTriggered(KryptonPipelineEvent.ENCRYPTION_ENABLED);
            this.velonetEncryptionEnabled = true;
        } catch (Throwable t) {
            if (pipeline.context("decrypt") != null) {
                pipeline.remove("decrypt");
            }
            if (pipeline.context("encrypt") != null) {
                pipeline.remove("encrypt");
            }
            decryption.close();
            encryption.close();
            throw t;
        }
    }
}
