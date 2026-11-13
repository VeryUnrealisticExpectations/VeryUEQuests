package dev.veryue.vuequests.platform;

import dev.veryue.vuequests.VeryUERegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.nio.file.Path;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class NeoForgePlatformHelper implements PlatformHelper {

    private final Map<ResourceKey<? extends Registry<?>>, List<Consumer<RegisterEvent>>> pendingRegistrations = new HashMap<>();
    private final Map<CommandPermission, PermissionNode<Boolean>> permissionNodes = new EnumMap<>(CommandPermission.class);

    @Override
    public List<Path> assetRoots() {
        return ModList.get().getModFiles().stream()
                .flatMap(info -> info.getFile().getContents().getContentRoots().stream())
                .distinct().toList();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, CommandPermission permission) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return PermissionAPI.getPermission(player, permissionNodes.get(permission));
        }
        return source.permissions().hasPermission(Permissions.COMMANDS_OWNER);
    }

    @Override
    public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) {
        return new RegistryBuilder<>(key).sync(true).create();
    }

    @Override
    public <T> T register(Registry<T> registry, Identifier id, T value) {
        ResourceKey<? extends Registry<T>> key = registry.key();
        pendingRegistrations.computeIfAbsent(key, registryKey -> new ArrayList<>())
                .add(event -> event.register(key, id, () -> value));
        return value;
    }

    public void init(IEventBus eventBus) {
        eventBus.addListener(this::onNewRegistry);
        eventBus.addListener(this::onRegister);
        for (CommandPermission permission : CommandPermission.values()) {
            permissionNodes.put(permission, new PermissionNode<>(MOD_ID, permission.path(), PermissionTypes.BOOLEAN,
                    (player, playerId, context) -> player != null && player.permissions().hasPermission(Permissions.COMMANDS_OWNER)));
        }
        NeoForge.EVENT_BUS.addListener(this::registerPermissions);
    }

    private void registerPermissions(PermissionGatherEvent.Nodes event) {
        permissionNodes.values().forEach(event::addNodes);
    }

    private void onNewRegistry(NewRegistryEvent event) {
        event.register(VeryUERegistries.REWARD_TYPES);
        event.register(VeryUERegistries.QUEST_OBJECTIVE_TYPES);
    }

    private void onRegister(RegisterEvent event) {
        // i just dont want consumers sitting around after registry phase
        List<Consumer<RegisterEvent>> registrations = pendingRegistrations.remove(event.getRegistryKey());
        if (registrations != null) {
            registrations.forEach(registration -> registration.accept(event));
        }
    }
}
