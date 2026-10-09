package com.EdDYON.dogdog.common.registry;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.attachment.PetData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.attachment.IAttachmentHolder; // 必须导入这个
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DogDog.MODID);

    public static final Supplier<AttachmentType<PetData>> PET_DATA = ATTACHMENT_TYPES.register(
            "pet_data",
            () -> AttachmentType.builder(PetData::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, PetData>() {

                        // 1. 读取 (Read): 修正！增加了 IAttachmentHolder 参数
                        @Override
                        public PetData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                            PetData data = new PetData();
                            data.deserializeNBT(provider, tag);
                            return data;
                        }

                        // 2. 写入 (Write): 这个签名是正确的，保持不变
                        @Override
                        public CompoundTag write(PetData attachment, HolderLookup.Provider provider) {
                            return attachment.serializeNBT(provider);
                        }
                    })
                    .build()
    );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}