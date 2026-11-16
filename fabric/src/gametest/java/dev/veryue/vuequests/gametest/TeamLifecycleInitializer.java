package dev.veryue.vuequests.gametest;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.data.FlatFileUserLoader;
import dev.veryue.vuequests.models.PlayerUser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class TeamLifecycleInitializer implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!System.getProperty("vuequests.tests.lifecycle", "").equals("native-crash")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!server.isDedicatedServer()) throw new AssertionError("Native failure probe requires a dedicated server");
            UUID id = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "NativeFailure"), false);
            var player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
            var manager = VeryUEMod.INSTANCE.getUserManager();
            manager.loadPlayer(player);
            Path file = new FlatFileUserLoader<>(server, "users", PlayerUser.CODEC).userPath(id).toAbsolutePath();
            try {
                Files.createDirectories(file);
                Path manifest = Path.of(System.getProperty("vuequests.tests.nativeManifest"));
                Files.createDirectories(manifest.getParent());
                Files.writeString(manifest, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                        "process", ProcessHandle.current().pid(), "player", id.toString(), "file", file.toString(), "dedicated", true)));
            } catch (Exception exception) { throw new IllegalStateException(exception); }
            manager.saveAllUsers();
        });
    }
}
