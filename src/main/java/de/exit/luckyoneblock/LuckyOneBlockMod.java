package de.exit.luckyoneblock;

import de.exit.luckyoneblock.logic.OneBlockManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuckyOneBlockMod implements ModInitializer {
    public static final String MOD_ID = "luckyoneblock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[LuckyOneBlock] Initializing Lucky OneBlock mod...");
        OneBlockManager.register();
    }
}
