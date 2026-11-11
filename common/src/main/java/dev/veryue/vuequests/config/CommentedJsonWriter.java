package dev.veryue.vuequests.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.Map;

public class CommentedJsonWriter {

    private static final Gson JSON = new Gson();

    public static String write(JsonElement value, Map<String, String> comments) {
        StringBuilder output = new StringBuilder();
        write(value, comments, "", 0, output);
        return output.toString();
    }

    private static void write(JsonElement value, Map<String, String> comments, String path, int depth, StringBuilder output) {
        String indent = "  ".repeat(depth + 1);
        if (value.isJsonObject()) {
            output.append('{');
            int index = 0;
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (index++ > 0) {
                    output.append(',');
                }
                output.append('\n');
                String fieldPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                String comment = comments.get(fieldPath);
                if (comment != null) {
                    comment.lines().forEach(line -> output.append(indent).append("// ").append(line).append('\n'));
                }
                output.append(indent).append(JSON.toJson(entry.getKey())).append(": ");
                write(entry.getValue(), comments, fieldPath, depth + 1, output);
            }
            if (index > 0) {
                output.append('\n').append("  ".repeat(depth));
            }
            output.append('}');
        } else if (value.isJsonArray()) {
            output.append('[');
            var array = value.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                output.append('\n').append(indent);
                write(array.get(index), comments, path + "[" + index + "]", depth + 1, output);
            }
            if (!array.isEmpty()) {
                output.append('\n').append("  ".repeat(depth));
            }
            output.append(']');
        } else {
            output.append(JSON.toJson(value));
        }
    }
}
