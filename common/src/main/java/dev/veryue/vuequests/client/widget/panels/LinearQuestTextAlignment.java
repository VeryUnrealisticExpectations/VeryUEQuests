package dev.veryue.vuequests.client.widget.panels;

public enum LinearQuestTextAlignment {
    LEFT,
    CENTER,
    RIGHT;

    public int offset(int availableWidth, int textWidth) {
        return switch (this) {
            case LEFT -> 0;
            case CENTER -> (availableWidth - textWidth) / 2;
            case RIGHT -> availableWidth - textWidth;
        };
    }
}
