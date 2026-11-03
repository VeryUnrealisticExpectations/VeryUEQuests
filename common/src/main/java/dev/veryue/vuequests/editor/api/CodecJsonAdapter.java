package dev.veryue.vuequests.editor.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import net.minecraft.resources.RegistryOps;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

public class CodecJsonAdapter<T> extends TypeAdapter<T> {
    private static final Gson JSON = new Gson();
    private final Codec<T> codec;
    private final RegistryOps<JsonElement> ops;
    private final String generatedId;

    public CodecJsonAdapter(Codec<T> codec, RegistryOps<JsonElement> ops, String generatedId) {
        this.codec = codec;
        this.ops = ops;
        this.generatedId = generatedId;
    }

    @Override
    public T read(JsonReader reader) throws IOException {
        JsonElement input;
        if (generatedId == null) {
            input = JSON.getAdapter(JsonElement.class).read(reader);
        } else {
            Map<String, JsonElement> fields = JSON.fromJson(reader, TypeToken.getParameterized(LinkedHashMap.class, String.class, JsonElement.class).getType());
            if (fields == null) throw new JsonParseException("Objective/reward entries must be objects.");
            fields.putIfAbsent(generatedId, JSON.toJsonTree(UUID.randomUUID().toString()));
            input = JSON.toJsonTree(fields);
        }
        if (generatedId != null) validateEntry(input);
        return codec.parse(ops, input).getOrThrow(JsonParseException::new);
    }

    private void validateEntry(JsonElement input) {
        var fields = input.getAsJsonObject();
        if (!fields.has("type") || !fields.get("type").isJsonPrimitive() || !fields.get("type").getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Supply the registered objective/reward type ID.");
        }
        JsonElement identifier = fields.get(generatedId);
        if (identifier == null || !identifier.isJsonPrimitive() || !identifier.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Supply a canonical " + generatedId + " UUID string or omit it.");
        }
        String id = identifier.getAsString();
        if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new JsonParseException("Supply a canonical " + generatedId + " UUID.");
        String type = fields.get("type").getAsString();
        if (type.equals("vuequests:experience") && fields.has("experience")) {
            strictInteger(fields.get("experience"), "experience", Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if ((type.equals("vuequests:collect_item") || type.equals("vuequests:item")) && fields.has("item") && fields.get("item").isJsonObject()) {
            var stack = fields.getAsJsonObject("item");
            if (stack.has("count")) {
                // ItemStack.CODEC defaults invalid counts to one, so reject them before native decoding.
                strictInteger(stack.get("count"), "item.count", 1, 99);
            }
        }
    }

    private static void strictInteger(JsonElement input, String name, int minimum, int maximum) {
        try {
            if (!input.isJsonPrimitive() || !input.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            int value = new BigDecimal(input.getAsString()).intValueExact();
            if (value < minimum || value > maximum) throw new ArithmeticException();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new JsonParseException(name + " must be an integer between " + minimum + " and " + maximum + ".", exception);
        }
    }

    @Override
    public void write(JsonWriter writer, T value) throws IOException {
        JSON.getAdapter(JsonElement.class).write(writer, codec.encodeStart(ops, value).getOrThrow(JsonParseException::new));
    }
}
