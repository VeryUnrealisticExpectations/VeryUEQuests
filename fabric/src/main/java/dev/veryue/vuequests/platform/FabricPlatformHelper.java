package dev.veryue.vuequests.platform;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.List;

public class FabricPlatformHelper implements PlatformHelper {

    @Override
    public List<Path> assetRoots() {
        return FabricLoader.getInstance().getAllMods().stream().flatMap(mod -> mod.getRootPaths().stream())
                .distinct().toList();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) {
        return FabricRegistryBuilder.createSimple(key).buildAndRegister();
    }

    @Override
    public <T> T register(Registry<T> registry, ResourceLocation id, T value) {
        return Registry.register(registry, id, value);
    }
}
