package com.EdDYON.dogdog.common.item;

import com.EdDYON.dogdog.DogDog;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DogDog.MODID);

    public static final DeferredHolder<Item, Item> GOLDEN_BONE = ITEMS.register("golden_bone",
            () -> new Item(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}