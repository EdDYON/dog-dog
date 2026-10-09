package com.EdDYON.dogdog.common.registry;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.item.DogHandbookItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DogDog.MODID);

    public static final DeferredItem<Item> GOLDEN_BONE = ITEMS.register("golden_bone",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> DOG_HANDBOOK = ITEMS.register("dog_handbook",
            () -> new DogHandbookItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
