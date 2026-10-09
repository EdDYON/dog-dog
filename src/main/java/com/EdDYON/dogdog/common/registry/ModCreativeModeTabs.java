package com.EdDYON.dogdog.common.registry;

import com.EdDYON.dogdog.DogDog;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DogDog.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DOG_TAB = CREATIVE_MODE_TABS.register("dog_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.dog_dog.title"))
                    .icon(() -> new ItemStack(ModItems.GOLDEN_BONE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DOG_HANDBOOK.get());
                        output.accept(ModItems.GOLDEN_BONE.get());
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
