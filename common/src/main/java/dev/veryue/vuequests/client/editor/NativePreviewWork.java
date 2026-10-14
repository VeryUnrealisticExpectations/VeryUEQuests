package dev.veryue.vuequests.client.editor;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public record NativePreviewWork(JsonObject request, CompletableFuture<JsonObject> response) { }
