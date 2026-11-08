package dev.veryue.vuequests.network;

import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.ObjectiveContext;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.registry.QuestObjectiveTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record QuestScreenOpenedPayload() implements CustomPacketPayload {

    public static final Type<QuestScreenOpenedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_screen_opened"));
    public static final QuestScreenOpenedPayload INSTANCE = new QuestScreenOpenedPayload();
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestScreenOpenedPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public Type<QuestScreenOpenedPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        LOGGER.debug("Player {} opened the quest screen", player.getGameProfile().getName());

        UUID uuid = player.getGameProfile().getId();
        // todo; REFACTOR - FUTURE
        // todo; technically a bug - what if the player logs out without checking?
        Optional<User> user = VeryUEMod.INSTANCE.getUser(uuid);
        if (user.isPresent()) {
            QuestData questData = QuestReloadDelegate.INSTANCE.getData();

            ObjectiveContext build = ObjectiveContext.createContext(player.serverLevel())
                    .withParameter(ObjectiveContext.PLAYER, player)
                    .build();

            questData.handleQuestTrigger(user.get(), QuestObjectiveTypes.COLLECT_ITEM, build);
            questData.handleQuestTrigger(user.get(), QuestObjectiveTypes.ADVANCEMENT, build);
        } else {
            // todo; logging?
        }

    }
}
