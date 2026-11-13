package dev.veryue.vuequests.client;

import dev.veryue.vuequests.client.screens.QuestScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public enum QuestCanvasKeyConflictContext implements IKeyConflictContext {
    INSTANCE;

    @Override
    public boolean isActive() {
        return Minecraft.getInstance().gui.screen() instanceof QuestScreen;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this || other == KeyConflictContext.GUI || other == KeyConflictContext.UNIVERSAL;
    }
}
