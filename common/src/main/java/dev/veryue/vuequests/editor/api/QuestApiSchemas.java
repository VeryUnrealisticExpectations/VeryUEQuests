package dev.veryue.vuequests.editor.api;

import dev.veryue.vuequests.editor.api.models.JsonSchema;
import java.util.ArrayList;
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

    public static JsonSchema objective(String type, Map<String, JsonSchema> fields) {
        return entry(type, "objective_id", fields);
    }

    public static JsonSchema countedObjective(String type, String field, JsonSchema value, String countDescription) {
        JsonSchema schema = objective(type, field, value);
        schema.properties.put("count", JsonSchema.type("integer").format("int32")
                .minimum(1).maximum(Integer.MAX_VALUE).description(countDescription));
        return schema;
    }

    public static JsonSchema reward(String type, String field, JsonSchema value) {
        return entry(type, "reward_id", field, value);
    }

    public static JsonSchema reward(String type, Map<String, JsonSchema> fields) {
        return entry(type, "reward_id", fields);
    }

    private static JsonSchema entry(String type, String idField, String field, JsonSchema value) {
        return entry(type, idField, Map.of(field, value));
    }

    private static JsonSchema entry(String type, String idField, Map<String, JsonSchema> fields) {
        Map<String, JsonSchema> properties = new LinkedHashMap<>();
        properties.put("type", JsonSchema.type("string").values(type));
        properties.put(idField, JsonSchema.type("string").format("uuid").description("Generated when omitted in an API request."));
        properties.putAll(fields);
        List<String> required = new ArrayList<>();
        required.add("type");
        required.addAll(fields.keySet());
        return JsonSchema.object(properties, required);
    }
}
