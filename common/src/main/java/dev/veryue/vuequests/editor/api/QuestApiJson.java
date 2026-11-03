package dev.veryue.vuequests.editor.api;

import dev.veryue.vuequests.editor.api.models.ListMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import dev.veryue.vuequests.models.objective.QuestObjective;
import dev.veryue.vuequests.models.quest.Quest;
import dev.veryue.vuequests.models.quest.Section;
import dev.veryue.vuequests.models.reward.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import java.util.UUID;

public class QuestApiJson {
    public static Gson create(RegistryOps<JsonElement> ops) {
        return new GsonBuilder().disableHtmlEscaping()
                .registerTypeAdapter(ListMode.class, new ListModeAdapter())
                .registerTypeAdapter(UUID.class, new UuidJsonAdapter().nullSafe())
                .registerTypeAdapter(int.class, new StrictIntegerAdapter())
                .registerTypeAdapter(Integer.class, new StrictIntegerAdapter())
                .registerTypeHierarchyAdapter(Component.class, new CodecJsonAdapter<>(ComponentSerialization.CODEC, ops, null).nullSafe())
                .registerTypeHierarchyAdapter(QuestObjective.class, new CodecJsonAdapter<>(QuestObjective.CODEC, ops, "objective_id"))
                .registerTypeHierarchyAdapter(RewardType.class, new CodecJsonAdapter<>(RewardType.CODEC, ops, "reward_id"))
                .registerTypeAdapter(Quest.class, new CodecJsonAdapter<>(Quest.CODEC, ops, null))
                .registerTypeAdapter(Section.class, new CodecJsonAdapter<>(Section.CODEC, ops, null))
                .create();
    }
}
