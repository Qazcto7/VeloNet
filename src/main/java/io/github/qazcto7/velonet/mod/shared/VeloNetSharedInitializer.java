package io.github.qazcto7.velonet.mod.shared;

import com.velocitypowered.natives.compression.JavaVelocityCompressor;
import com.velocitypowered.natives.encryption.JavaVelocityCipher;
import com.velocitypowered.natives.util.Natives;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VeloNetSharedInitializer implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("VeloNet");

    @Override
    public void onInitialize() {
        String compVariant = Natives.compress.getLoadedVariant();
        String encVariant = Natives.cipher.getLoadedVariant();
        String compType = Natives.compress.get() == JavaVelocityCompressor.FACTORY ? "JVM zlib" : "Native libdeflate";
        String encType = Natives.cipher.get() == JavaVelocityCipher.FACTORY ? "JVM AES-NI" : "Native OpenSSL";

        LOGGER.info("VeloNet initialized: compression [{} / {}], encryption [{} / {}]",
                compVariant, compType, encVariant, encType);
    }
}
