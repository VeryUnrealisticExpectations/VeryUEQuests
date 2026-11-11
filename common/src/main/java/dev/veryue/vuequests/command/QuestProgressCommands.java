package dev.veryue.vuequests.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.veryue.vuequests.VeryUEMod;
import dev.veryue.vuequests.data.quest.QuestData;
import dev.veryue.vuequests.data.quest.QuestReloadDelegate;
import dev.veryue.vuequests.models.User;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;
import static dev.veryue.vuequests.platform.CommandPermission.OBJECTIVE_PROGRESS;
import static dev.veryue.vuequests.platform.CommandPermission.QUEST_PROGRESS;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class QuestProgressCommands {

    private static final DynamicCommandExceptionType UNKNOWN_QUEST = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.vuequests.progress.unknown_quest", id.toString()));
    private static final DynamicCommandExceptionType UNKNOWN_OBJECTIVE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.vuequests.progress.unknown_objective", id.toString()));
    private static final DynamicCommandExceptionType AMBIGUOUS_OBJECTIVE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.vuequests.progress.ambiguous_objective", id.toString()));
    private static final SimpleCommandExceptionType USER_UNAVAILABLE = new SimpleCommandExceptionType(
            Component.translatable("commands.vuequests.progress.user_unavailable"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Consumer<ServerPlayer> syncProgress) {
        PlatformHelper platform = PlatformHelper.INSTANCE;
        dispatcher.register(literal(MOD_ID)
                .then(literal("progress")
                        .requires(source -> platform.hasPermission(source, QUEST_PROGRESS)
                                || platform.hasPermission(source, OBJECTIVE_PROGRESS))
                        .then(literal("quest")
                                .requires(source -> platform.hasPermission(source, QUEST_PROGRESS))
                                .then(argument("quest_id", UuidArgument.uuid())
                                        .suggests(QuestProgressCommands::suggestQuestIds)
                                        .then(literal("reset").executes(context -> updateQuest(context, syncProgress, false)))
                                        .then(literal("complete").executes(context -> updateQuest(context, syncProgress, true)))))
                        .then(literal("objective")
                                .requires(source -> platform.hasPermission(source, OBJECTIVE_PROGRESS))
                                .then(argument("objective_id", UuidArgument.uuid())
                                        .suggests(QuestProgressCommands::suggestObjectiveIds)
                                        .then(literal("reset").executes(context -> updateObjective(context, syncProgress, false)))
                                        .then(literal("complete").executes(context -> updateObjective(context, syncProgress, true)))))));
    }

    public static CompletableFuture<Suggestions> suggestQuestIds(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(QuestReloadDelegate.INSTANCE.getData().quests().keySet().stream()
                .map(UUID::toString).sorted(), builder);
    }

    private static CompletableFuture<Suggestions> suggestObjectiveIds(CommandContext<CommandSourceStack> context,
                                                                       SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(QuestReloadDelegate.INSTANCE.getData().quests().values().stream()
                .flatMap(quest -> quest.objectives().stream())
                .map(objective -> objective.getObjectiveId().toString()).distinct().sorted(), builder);
    }

    private static int updateQuest(CommandContext<CommandSourceStack> context, Consumer<ServerPlayer> syncProgress,
                                   boolean complete) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID questId = UuidArgument.getUuid(context, "quest_id");
        QuestData data = QuestReloadDelegate.INSTANCE.getData();
        Quest quest = data.quests().get(questId);
        if (quest == null) {
            throw UNKNOWN_QUEST.create(questId);
        }

        User user = getUser(player);
        if (complete) {
            user.completeQuestProgress(quest);
        } else {
            user.resetQuestProgress(quest);
            data.restoreAttributeRewards(user, player);
        }
        syncProgress.accept(player);
        context.getSource().sendSuccess(() -> Component.translatable(complete
                ? "commands.vuequests.progress.quest.complete" : "commands.vuequests.progress.quest.reset",
                questId.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int updateObjective(CommandContext<CommandSourceStack> context, Consumer<ServerPlayer> syncProgress,
                                       boolean complete) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID objectiveId = UuidArgument.getUuid(context, "objective_id");
        List<Quest> owners = QuestReloadDelegate.INSTANCE.getData().quests().values().stream()
                .filter(quest -> quest.objectives().stream().anyMatch(objective -> objective.getObjectiveId().equals(objectiveId)))
                .limit(2).toList();
        if (owners.isEmpty()) {
            throw UNKNOWN_OBJECTIVE.create(objectiveId);
        }
        if (owners.size() > 1) {
            throw AMBIGUOUS_OBJECTIVE.create(objectiveId);
        }

        Quest quest = owners.getFirst();
        User user = getUser(player);
        if (complete) {
            QuestObjective<?, ?> objective = quest.objectives().stream()
                    .filter(candidate -> candidate.getObjectiveId().equals(objectiveId)).findFirst().orElseThrow();
            user.completeObjectiveProgress(quest, objective);
        } else {
            user.resetObjectiveProgress(quest, objectiveId);
        }
        syncProgress.accept(player);
        context.getSource().sendSuccess(() -> Component.translatable(complete
                ? "commands.vuequests.progress.objective.complete" : "commands.vuequests.progress.objective.reset",
                objectiveId.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static User getUser(ServerPlayer player) throws CommandSyntaxException {
        return VeryUEMod.INSTANCE.getUser(player.getUUID()).orElseThrow(USER_UNAVAILABLE::create);
    }
}
