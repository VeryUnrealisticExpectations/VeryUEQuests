package dev.veryue.vuequests.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.veryue.vuequests.client.lang.QuestLanguageCatalog;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TranslatableContents.class)
public class TranslatableContentsMixin {
    @ModifyExpressionValue(method = "decompose", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getInstance()Lnet/minecraft/locale/Language;"))
    private Language vuequests$editorLanguage(Language language) {
        return QuestLanguageCatalog.languageOr(language);
    }
}
