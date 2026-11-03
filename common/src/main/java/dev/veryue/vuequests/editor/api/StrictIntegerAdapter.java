package dev.veryue.vuequests.editor.api;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;

public class StrictIntegerAdapter extends TypeAdapter<Integer> {
    @Override
    public Integer read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null; }
        if (reader.peek() != JsonToken.NUMBER) throw new JsonParseException("Expected an integer number.");
        try {
            return new BigDecimal(reader.nextString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new JsonParseException("Expected a 32-bit integer number.", exception);
        }
    }

    @Override
    public void write(JsonWriter writer, Integer value) throws IOException { writer.value(value); }
}
