package io.github.qazcto7.velonet.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class VeloNetClientInitializer implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("VeloNet");

    @Override
    public void onInitializeClient() {
        LOGGER.info("VeloNet is accelerating Minecraft networking with ViaFabricPlus & Eunomia compatibility");
    }
}
