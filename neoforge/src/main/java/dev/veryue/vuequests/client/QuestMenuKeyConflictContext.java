package dev.veryue.vuequests.client;

import dev.veryue.vuequests.client.screens.QuestMenuScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public enum QuestMenuKeyConflictContext implements IKeyConflictContext {
    INSTANCE;

    @Override
    public boolean isActive() {
        return Minecraft.getInstance().gui.screen() instanceof QuestMenuScreen;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this || other == QuestCanvasKeyConflictContext.INSTANCE
                || other == KeyConflictContext.GUI || other == KeyConflictContext.UNIVERSAL;
    }
}
