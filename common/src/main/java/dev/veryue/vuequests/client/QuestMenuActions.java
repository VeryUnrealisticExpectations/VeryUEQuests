package dev.veryue.vuequests.client;

import java.util.UUID;
import java.util.function.BiConsumer;

public record QuestMenuActions(Runnable opened, BiConsumer<UUID, UUID> interactWithObjective,
                               BiConsumer<UUID, UUID> claimReward) {
}
