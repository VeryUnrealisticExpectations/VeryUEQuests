package dev.veryue.vuequests.gametest.mixin;

import dev.veryue.vuequests.gametest.TeamFailureCapture;
import net.minecraft.CrashReport;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockableEventLoop.class)
public class TeamCrashCaptureMixin {
    @Inject(method = "delayCrash", at = @At("HEAD"), cancellable = true)
    private void captureTeamFixtureFailure(CrashReport report, CallbackInfo callback) {
        if (TeamFailureCapture.capture(report)) callback.cancel();
    }
}
