package dev.veryue.vuequests.editor.api;

public class QuestApiException extends IllegalArgumentException {
    private final int status;

    public QuestApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
}
