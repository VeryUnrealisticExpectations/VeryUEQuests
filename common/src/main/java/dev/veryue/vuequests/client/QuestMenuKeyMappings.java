package dev.veryue.vuequests.client;

import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestMenuKeyMappings {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "quest_menu"));

    public final KeyMapping previous;
    public final KeyMapping next;

    private final BiPredicate<KeyMapping, InputConstants.Key> matches;

    public QuestMenuKeyMappings() {
        this((name, keyCode) -> new KeyMapping(name, InputConstants.Type.KEYBOARD, keyCode, CATEGORY),
                QuestMenuKeyMappings::matchesVanilla);
    }

    public QuestMenuKeyMappings(BiFunction<String, Integer, KeyMapping> createMapping,
                                BiPredicate<KeyMapping, InputConstants.Key> matches) {
        previous = createMapping.apply("key." + MOD_ID + ".menu.previous", InputConstants.KEY_Q);
        next = createMapping.apply("key." + MOD_ID + ".menu.next", InputConstants.KEY_E);
        this.matches = matches;
    }

    public void register(Consumer<KeyMapping> register) {
        register.accept(previous);
        register.accept(next);
    }

    public KeyMapping matching(InputConstants.Key key) {
        if (key.equals(InputConstants.UNKNOWN)) {
            return null;
        }
        if (matches.test(previous, key)) {
            return previous;
        }
        if (matches.test(next, key)) {
            return next;
        }
        return null;
    }

    private static boolean matchesVanilla(KeyMapping mapping, InputConstants.Key key) {
        return mapping.matches(key);
    }
}
