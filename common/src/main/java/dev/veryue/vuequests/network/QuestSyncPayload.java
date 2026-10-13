package dev.veryue.vuequests.network;

import dev.veryue.vuequests.models.quest.Section;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record QuestSyncPayload(List<Section> sections) implements CustomPacketPayload {

    public static final Type<QuestSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPayload> STREAM_CODEC = ByteBufCodecs
            .fromCodecWithRegistries(Section.CODEC.listOf()).map(QuestSyncPayload::new, QuestSyncPayload::sections);

    @Override
    public Type<QuestSyncPayload> type() {
        return TYPE;
    }
}
