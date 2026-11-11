package dev.veryue.vuequests.platform;

import dev.veryue.vuequests.VeryUEConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ServiceLoader;
import java.nio.file.Path;
import java.util.List;

public interface PlatformHelper {

    PlatformHelper INSTANCE = ServiceLoader.load(PlatformHelper.class, VeryUEConstants.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No platform helper is available for this mod platform"));

    boolean isModLoaded(String modId);

    boolean hasPermission(CommandSourceStack source, CommandPermission permission);

    default List<Path> assetRoots() { return List.of(); }

    <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key);

    <T> T register(Registry<T> registry, ResourceLocation id, T value);
}
