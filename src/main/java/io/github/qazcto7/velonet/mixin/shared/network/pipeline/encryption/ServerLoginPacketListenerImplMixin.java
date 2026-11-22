package io.github.qazcto7.velonet.mixin.shared.network.pipeline.encryption;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.qazcto7.velonet.mod.shared.network.ClientConnectionEncryptionExtension;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginPacketListenerImplMixin {
    @Unique
    private static final Logger velonet$LOGGER = LoggerFactory.getLogger("VeloNet");

    @WrapOperation(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;setEncryptionKey(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"
            )
    )
    private void wrapSetEncryptionKey(Connection connection, Cipher decryptCipher, Cipher encryptCipher,
                                      Operation<Void> original, @Local SecretKey secretKey) {
        // e4mc compatibility: e4mc sets ciphers to null on Dialtone connections to disable packet-level encryption.
        if (decryptCipher == null || encryptCipher == null) {
            original.call(connection, decryptCipher, encryptCipher);
            return;
        }

        if (secretKey != null && connection instanceof ClientConnectionEncryptionExtension ext) {
            try {
                ext.setupEncryption(secretKey);
                return;
            } catch (Exception | LinkageError e) {
                // Smoothly fall through to vanilla Java Cipher if native library linking or setup fails.
                // Fatal VirtualMachineErrors (e.g. OutOfMemoryError) are intentionally not caught.
                velonet$LOGGER.warn("Failed to initialize native encryption on login, falling back to vanilla Java encryption: {}", e.toString());
            }
        }

        // Fallback to standard pipeline if native setup is not possible
        original.call(connection, decryptCipher, encryptCipher);
    }
}
