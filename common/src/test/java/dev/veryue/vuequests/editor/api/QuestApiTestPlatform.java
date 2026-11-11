package dev.veryue.vuequests.editor.api;

import com.mojang.serialization.Lifecycle;
import dev.veryue.vuequests.platform.CommandPermission;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class QuestApiTestPlatform implements PlatformHelper {
    @Override public boolean isModLoaded(String modId) { return false; }
    @Override public boolean hasPermission(CommandSourceStack source, CommandPermission permission) { return source.hasPermission(CommandPermission.DEFAULT_LEVEL); }
    @Override public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) { return new MappedRegistry<>(key, Lifecycle.stable()); }
    @Override public <T> T register(Registry<T> registry, ResourceLocation id, T value) { return Registry.register(registry, id, value); }
}
