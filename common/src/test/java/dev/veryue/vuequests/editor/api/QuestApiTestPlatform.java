package dev.veryue.vuequests.editor.api;

import com.mojang.serialization.Lifecycle;
import dev.veryue.vuequests.platform.CommandPermission;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

public class QuestApiTestPlatform implements PlatformHelper {
    @Override public boolean isModLoaded(String modId) { return false; }
    @Override public boolean hasPermission(CommandSourceStack source, CommandPermission permission) { return source.permissions().hasPermission(Permissions.COMMANDS_OWNER); }
    @Override public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) { return new MappedRegistry<>(key, Lifecycle.stable()); }
    @Override public <T> T register(Registry<T> registry, Identifier id, T value) { return Registry.register(registry, id, value); }
}
