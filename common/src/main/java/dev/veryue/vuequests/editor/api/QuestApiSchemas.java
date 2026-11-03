package dev.veryue.vuequests.editor.api;

import dev.veryue.vuequests.editor.api.models.JsonSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuestApiSchemas {
    public static JsonSchema itemStack() {
        return JsonSchema.object(Map.of(
                "id", JsonSchema.type("string").description("Loaded minecraft:item registry ID; air is not a valid stack."),
                "count", JsonSchema.type("integer").minimum(1).maximum(99).description("Defaults to 1; Minecraft's ItemStack codec validates this count."),
                "components", JsonSchema.type("object").additionalProperties(true)
                        .description("Minecraft data component patch, validated by the loaded registry-aware ItemStack codec.")), List.of("id"));
    }

    public static JsonSchema objective(String type, String field, JsonSchema value) {
        return entry(type, "objective_id", field, value);
    }

    public static JsonSchema reward(String type, String field, JsonSchema value) {
        return entry(type, "reward_id", field, value);
    }

    private static JsonSchema entry(String type, String idField, String field, JsonSchema value) {
        Map<String, JsonSchema> properties = new LinkedHashMap<>();
        properties.put("type", JsonSchema.type("string").values(type));
        properties.put(idField, JsonSchema.type("string").format("uuid").description("Generated when omitted in an API request."));
        properties.put(field, value);
        return JsonSchema.object(properties, List.of("type", field));
    }
}
