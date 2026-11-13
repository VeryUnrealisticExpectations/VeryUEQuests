package dev.veryue.vuequests.platform;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

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
    public boolean hasPermission(CommandSourceStack source, CommandPermission permission) {
        return Permissions.check(source, permission.nodeName(), CommandPermission.DEFAULT_LEVEL);
    }

    @Override
    public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) {
        return FabricRegistryBuilder.create(key).buildAndRegister();
    }

    @Override
    public <T> T register(Registry<T> registry, Identifier id, T value) {
        return Registry.register(registry, id, value);
    }
}
