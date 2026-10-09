package dev.veryue.vuequests.platform;

import dev.veryue.vuequests.VeryUEConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ServiceLoader;

public interface RegistryHelper {

    RegistryHelper INSTANCE = ServiceLoader.load(RegistryHelper.class, VeryUEConstants.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No registry helper is available for this mod platform"));

    <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key);

    <T> T register(Registry<T> registry, ResourceLocation id, T value);
}
