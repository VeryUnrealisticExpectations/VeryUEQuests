package dev.veryue.vuequests.platform;

import dev.veryue.vuequests.VeryUEConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ServiceLoader;

public interface PlatformHelper {

    PlatformHelper INSTANCE = ServiceLoader.load(PlatformHelper.class, VeryUEConstants.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No platform helper is available for this mod platform"));

    boolean isModLoaded(String modId);

    <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key);

    <T> T register(Registry<T> registry, ResourceLocation id, T value);
}
