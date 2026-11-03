package dev.veryue.vuequests.editor.api.models;

import dev.veryue.vuequests.models.quest.QuestPosition;

public record ApiPosition(Integer x, Integer y) {
    public QuestPosition toPosition() {
        if (x == null || y == null) throw new IllegalArgumentException("Supply both position.x and position.y.");
        return new QuestPosition(x, y);
    }
}
