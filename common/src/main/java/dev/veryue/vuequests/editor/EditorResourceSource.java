package dev.veryue.vuequests.editor;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EditorResourceSource {
    CompletableFuture<JsonObject> describe(String locale);
    CompletableFuture<byte[]> read(String path);
    CompletableFuture<List<String>> list(String path);
    CompletableFuture<JsonObject> preview(JsonObject request);
    CompletableFuture<JsonObject> listQuestLanguages();
    CompletableFuture<JsonObject> readQuestLanguage(String locale);
    CompletableFuture<JsonObject> saveQuestLanguage(String locale, JsonObject request);
}
