package dev.veryue.vuequests.client.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientLanguage.class)
public interface ClientLanguageAccessor {
    @Accessor("storage") Map<String, String> vuequests$getTranslations();
}
