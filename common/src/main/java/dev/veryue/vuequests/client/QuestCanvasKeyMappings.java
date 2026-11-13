package dev.veryue.vuequests.client;

import org.lwjgl.sdl.SDLScancode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestCanvasKeyMappings {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "quest_canvas"));

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
        recenter = createMapping(translationKey("recenter"), InputConstants.KEY_HOME);
        zoomIn = createMapping(translationKey("zoom_in"), InputConstants.KEY_EQUALS);
        zoomOut = createMapping(translationKey("zoom_out"), InputConstants.KEY_MINUS);
        panLeft = createPanMapping.apply(translationKey("pan_left"), InputConstants.KEY_LEFT);
        panRight = createPanMapping.apply(translationKey("pan_right"), InputConstants.KEY_RIGHT);
        panUp = createPanMapping.apply(translationKey("pan_up"), InputConstants.KEY_UP);
        panDown = createPanMapping.apply(translationKey("pan_down"), InputConstants.KEY_DOWN);
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
        if (matchesDefaultZoom(zoomIn, key, InputConstants.KEY_ADD)) {
            return zoomIn;
        }
        if (matchesDefaultZoom(zoomOut, key, SDLScancode.SDL_SCANCODE_KP_MINUS)) {
            return zoomOut;
        }
        return null;
    }

    private static boolean matchesDefaultZoom(KeyMapping mapping, InputConstants.Key key, int keypadKey) {
        return mapping.isDefault() && (key.equals(mapping.getDefaultKey())
                || key.equals(InputConstants.Type.KEYBOARD.getOrCreate(keypadKey)));
    }

    private static boolean matchesVanilla(KeyMapping mapping, InputConstants.Key key) {
        return mapping.matches(key) && (!mapping.isDefault() || !isArrow(key.getValue())
                || InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
    }

    private static boolean isArrow(int keyCode) {
        return switch (keyCode) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_UP, InputConstants.KEY_DOWN -> true;
            default -> false;
        };
    }

    private static String translationKey(String name) {
        return "key." + MOD_ID + ".canvas." + name;
    }

    private static KeyMapping createMapping(String name, int keyCode) {
        return new KeyMapping(name, InputConstants.Type.KEYBOARD, keyCode, CATEGORY);
    }
}
