package dev.veryue.vuequests.editor.api;

import dev.veryue.vuequests.editor.QuestEditorService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class QuestApiTestService extends QuestApiService {
    public QuestApiTestService() { super(null, null); }
    @Override public CompletableFuture<String> write(String path, String body) {
        if (body.contains("conflict")) return CompletableFuture.failedFuture(new QuestEditorService.ConflictException("Test conflict"));
        if (body.contains("missing")) return CompletableFuture.failedFuture(new QuestApiException(404, "Test missing quest"));
        if (body.contains("timeout")) return CompletableFuture.failedFuture(new TimeoutException());
        return CompletableFuture.completedFuture("{\"data\":{},\"revision\":\"test\"}");
    }
    @Override public CompletableFuture<String> read(String path, Map<String, String> parameters) {
        return CompletableFuture.completedFuture("[]");
    }
}
