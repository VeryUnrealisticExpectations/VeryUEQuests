package dev.veryue.vuequests.editor.api;

import dev.veryue.vuequests.editor.api.models.ListMode;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class ListModeAdapter extends TypeAdapter<ListMode> {
    @Override
    public ListMode read(JsonReader reader) throws IOException {
        return switch (reader.nextString()) {
            case "append" -> ListMode.APPEND;
            case "replace" -> ListMode.REPLACE;
            default -> throw new JsonParseException("mode must be append or replace.");
        };
    }

    @Override
    public void write(JsonWriter writer, ListMode value) throws IOException {
        writer.value(value == ListMode.REPLACE ? "replace" : "append");
    }
}
