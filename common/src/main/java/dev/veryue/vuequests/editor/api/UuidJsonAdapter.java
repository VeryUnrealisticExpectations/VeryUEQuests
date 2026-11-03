package dev.veryue.vuequests.editor.api;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.UUID;

public class UuidJsonAdapter extends TypeAdapter<UUID> {
    @Override
    public UUID read(JsonReader reader) throws IOException {
        String text = reader.nextString();
        try {
            UUID value = UUID.fromString(text);
            if (!value.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Supply a canonical UUID string.", exception);
        }
    }

    @Override
    public void write(JsonWriter writer, UUID value) throws IOException { writer.value(value.toString()); }
}
