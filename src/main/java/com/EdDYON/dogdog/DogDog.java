package com.EdDYON.dogdog;

import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.registry.ModCreativeModeTabs;
import com.EdDYON.dogdog.common.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(DogDog.MODID)
public class DogDog {
    public static final String MODID = "dog_dog"; // 必须是 public static final
    public static final Logger LOGGER = LogUtils.getLogger();

    public DogDog(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachmentTypes.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
    }
}