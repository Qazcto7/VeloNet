package io.github.qazcto7.velonet.mod.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VeloNetServerInitializer implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("VeloNet");

    @Override
    public void onInitializeServer() {
        LOGGER.info("VeloNet is accelerating Minecraft dedicated server networking");
    }
}
