package dev.veryue.vuequests.mixin;

import dev.veryue.vuequests.VeryUEMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "awardStat", at = @At("RETURN"))
    private void vuequests$afterStatistic(Stat<?> statistic, int amount, CallbackInfo callback) {
        if (VeryUEMod.INSTANCE != null) {
            VeryUEMod.INSTANCE.onStatisticChanged((ServerPlayer) (Object) this, statistic);
        }
    }
}
