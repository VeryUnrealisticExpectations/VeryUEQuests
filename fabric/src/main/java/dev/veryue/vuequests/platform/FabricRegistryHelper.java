package dev.veryue.vuequests.platform;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class FabricRegistryHelper implements RegistryHelper {

    @Override
    public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) {
        return FabricRegistryBuilder.createSimple(key).buildAndRegister();
    }

    @Override
    public <T> T register(Registry<T> registry, ResourceLocation id, T value) {
        return Registry.register(registry, id, value);
    }
}
