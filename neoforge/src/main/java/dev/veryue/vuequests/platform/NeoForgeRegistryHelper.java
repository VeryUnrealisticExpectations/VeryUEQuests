package dev.veryue.vuequests.platform;

import dev.veryue.vuequests.VeryUERegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NeoForgeRegistryHelper implements RegistryHelper {

    private final Map<ResourceKey<? extends Registry<?>>, List<Consumer<RegisterEvent>>> pendingRegistrations = new HashMap<>();

    @Override
    public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> key) {
        return new RegistryBuilder<>(key).sync(true).create();
    }

    @Override
    public <T> T register(Registry<T> registry, ResourceLocation id, T value) {
        ResourceKey<? extends Registry<T>> key = registry.key();
        pendingRegistrations.computeIfAbsent(key, registryKey -> new ArrayList<>())
                .add(event -> event.register(key, id, () -> value));
        return value;
    }

    public void init(IEventBus eventBus) {
        eventBus.addListener(this::onNewRegistry);
        eventBus.addListener(this::onRegister);
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
