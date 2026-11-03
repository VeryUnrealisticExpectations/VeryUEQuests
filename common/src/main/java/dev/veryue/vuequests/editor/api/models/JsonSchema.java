package dev.veryue.vuequests.editor.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class JsonSchema {
    public String type;
    public String format;
    public String description;
    public Integer minimum;
    public Integer maximum;
    public Object additionalProperties;
    public Map<String, JsonSchema> properties;
    public List<String> required;
    @SerializedName("enum") public List<String> values;

    public static JsonSchema type(String type) {
        JsonSchema schema = new JsonSchema();
        schema.type = type;
        return schema;
    }

    public static JsonSchema object(Map<String, JsonSchema> properties, List<String> required) {
        JsonSchema schema = type("object");
        schema.properties = properties;
        schema.required = required;
        return schema;
    }

    public JsonSchema description(String description) { this.description = description; return this; }
    public JsonSchema format(String format) { this.format = format; return this; }
    public JsonSchema minimum(int minimum) { this.minimum = minimum; return this; }
    public JsonSchema maximum(int maximum) { this.maximum = maximum; return this; }
    public JsonSchema values(String value) { this.values = List.of(value); return this; }
    public JsonSchema additionalProperties(boolean allowed) { this.additionalProperties = allowed; return this; }
}
