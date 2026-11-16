package dev.veryue.vuequests;

import net.minecraft.network.chat.Component;

public class TeamActionException extends RuntimeException {
    private final Component feedback;

    public TeamActionException(String key, Object... args) {
        feedback = Component.translatable("team.vuequests.error." + key, args);
    }

    public Component feedback() {
        return feedback;
    }
}
