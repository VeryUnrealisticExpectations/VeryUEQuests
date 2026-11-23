package dev.veryue.vuequests.client.screens;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public abstract class LinearQuestCustomization {

    public static final String FANCY_MENU_MOD_ID = "fancymenu";
    private static final String FANCY_MENU_ADAPTER = "dev.veryue.vuequests.client.fancymenu.FancyMenuQuestCustomization";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static LinearQuestCustomization adapter;

    public static void initialize() {
        if (adapter != null) {
            return;
        }
        try {
            adapter = Class.forName(FANCY_MENU_ADAPTER).asSubclass(LinearQuestCustomization.class)
                    .getConstructor().newInstance();
        } catch (ClassNotFoundException ignored) {
            // Builds can omit the adapter with the FancyMenu Gradle property.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            LOGGER.warn("Could not initialize VUEQuests' FancyMenu integration", exception);
        }
    }

    static LinearQuestLayout getLayout(LinearQuestScreen screen) {
        if (adapter != null) {
            try {
                return adapter.resolveLayout(screen);
            } catch (LinkageError | RuntimeException exception) {
                adapter = null;
                LOGGER.warn("Disabled VUEQuests' FancyMenu integration after a layout error", exception);
            }
        }
        return LinearQuestLayout.DEFAULT;
    }

    protected abstract LinearQuestLayout resolveLayout(LinearQuestScreen screen);
}
