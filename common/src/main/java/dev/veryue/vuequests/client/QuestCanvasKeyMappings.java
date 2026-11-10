package dev.veryue.vuequests.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestCanvasKeyMappings {

    public static final String CATEGORY = "key.categories." + MOD_ID + ".quest_canvas";

    public final KeyMapping recenter;
    public final KeyMapping zoomIn;
    public final KeyMapping zoomOut;
    public final KeyMapping panLeft;
    public final KeyMapping panRight;
    public final KeyMapping panUp;
    public final KeyMapping panDown;

    private final BiPredicate<KeyMapping, InputConstants.Key> matches;

    public QuestCanvasKeyMappings() {
        this(QuestCanvasKeyMappings::createMapping, QuestCanvasKeyMappings::matchesVanilla);
    }

    public QuestCanvasKeyMappings(BiFunction<String, Integer, KeyMapping> createPanMapping,
                                  BiPredicate<KeyMapping, InputConstants.Key> matches) {
        recenter = createMapping(translationKey("recenter"), GLFW.GLFW_KEY_HOME);
        zoomIn = createMapping(translationKey("zoom_in"), GLFW.GLFW_KEY_EQUAL);
        zoomOut = createMapping(translationKey("zoom_out"), GLFW.GLFW_KEY_MINUS);
        panLeft = createPanMapping.apply(translationKey("pan_left"), GLFW.GLFW_KEY_LEFT);
        panRight = createPanMapping.apply(translationKey("pan_right"), GLFW.GLFW_KEY_RIGHT);
        panUp = createPanMapping.apply(translationKey("pan_up"), GLFW.GLFW_KEY_UP);
        panDown = createPanMapping.apply(translationKey("pan_down"), GLFW.GLFW_KEY_DOWN);
        this.matches = matches;
    }

    public void register(Consumer<KeyMapping> register) {
        register.accept(recenter);
        register.accept(zoomIn);
        register.accept(zoomOut);
        register.accept(panLeft);
        register.accept(panRight);
        register.accept(panUp);
        register.accept(panDown);
    }

    public KeyMapping matching(InputConstants.Key key) {
        if (key == InputConstants.UNKNOWN) {
            return null;
        }
        if (matches.test(recenter, key)) {
            return recenter;
        }
        if (matches.test(zoomIn, key)) {
            return zoomIn;
        }
        if (matches.test(zoomOut, key)) {
            return zoomOut;
        }
        if (matches.test(panLeft, key)) {
            return panLeft;
        }
        if (matches.test(panRight, key)) {
            return panRight;
        }
        if (matches.test(panUp, key)) {
            return panUp;
        }
        if (matches.test(panDown, key)) {
            return panDown;
        }
        // Check the default shortcuts after configured modifier combinations. Plus is Shift+= on US keyboards.
        if (recenter.isDefault() && key.equals(recenter.getDefaultKey())) {
            return recenter;
        }
        if (matchesDefaultZoom(zoomIn, key, GLFW.GLFW_KEY_KP_ADD)) {
            return zoomIn;
        }
        if (matchesDefaultZoom(zoomOut, key, GLFW.GLFW_KEY_KP_SUBTRACT)) {
            return zoomOut;
        }
        return null;
    }

    private static boolean matchesDefaultZoom(KeyMapping mapping, InputConstants.Key key, int keypadKey) {
        return mapping.isDefault() && (key.equals(mapping.getDefaultKey())
                || key.equals(InputConstants.Type.KEYSYM.getOrCreate(keypadKey)));
    }

    private static boolean matchesVanilla(KeyMapping mapping, InputConstants.Key key) {
        return switch (key.getType()) {
            case KEYSYM -> mapping.matches(key.getValue(), -1)
                    && (!mapping.isDefault() || !isArrow(key.getValue()) || Screen.hasShiftDown());
            case SCANCODE -> mapping.matches(InputConstants.UNKNOWN.getValue(), key.getValue());
            case MOUSE -> mapping.matchesMouse(key.getValue());
        };
    }

    private static boolean isArrow(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> true;
            default -> false;
        };
    }

    private static String translationKey(String name) {
        return "key." + MOD_ID + ".canvas." + name;
    }

    private static KeyMapping createMapping(String name, int keyCode) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }
}
