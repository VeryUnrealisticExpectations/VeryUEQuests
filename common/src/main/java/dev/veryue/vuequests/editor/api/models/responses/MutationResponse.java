package dev.veryue.vuequests.editor.api.models.responses;

public record MutationResponse<T>(T data, String revision) { }
