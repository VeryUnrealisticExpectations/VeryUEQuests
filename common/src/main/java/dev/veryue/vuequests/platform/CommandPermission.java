package dev.veryue.vuequests.platform;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public enum CommandPermission {
    QUEST_PROGRESS("progress.quest"),
    OBJECTIVE_PROGRESS("progress.objective"),
    DEBUG("debug"),
    RELOAD("reload"),
    WEBEDITOR("webeditor");

    public static final int DEFAULT_LEVEL = 4;

    private final String path;

    CommandPermission(String commandPath) {
        this.path = "commands." + commandPath;
    }

    public String path() {
        return path;
    }

    public String nodeName() {
        return MOD_ID + "." + path;
    }
}
