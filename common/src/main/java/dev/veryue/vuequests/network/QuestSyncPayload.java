package dev.veryue.vuequests.network;

import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.PlayerUser;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record QuestSyncPayload(List<Section> sections, PlayerUser user) implements CustomPacketPayload {

    public static final Type<QuestSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodecWithRegistries(Section.CODEC.listOf()), QuestSyncPayload::sections,
            ByteBufCodecs.fromCodecWithRegistries(PlayerUser.CODEC), QuestSyncPayload::user,
            QuestSyncPayload::new);

    public static QuestSyncPayload create(ServerPlayer player) {
        User source = VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseGet(() -> new PlayerUser(player.getUUID()));
        if (!(source instanceof PlayerUser user)) {
            throw new IllegalStateException("Quest sync currently requires a PlayerUser.");
        }

        // Detach typed progress values before the network thread encodes the payload.
        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        PlayerUser snapshot = PlayerUser.CODEC.encodeStart(ops, user)
                .flatMap(encoded -> PlayerUser.CODEC.parse(ops, encoded)).getOrThrow();
        List<Section> sections = player.server.isSingleplayerOwner(player.getGameProfile())
                ? List.of()
                : List.copyOf(QuestReloadDelegate.INSTANCE.getData().sections().values());
        return new QuestSyncPayload(sections, snapshot);
    }

    @Override
    public Type<QuestSyncPayload> type() {
        return TYPE;
    }
}
