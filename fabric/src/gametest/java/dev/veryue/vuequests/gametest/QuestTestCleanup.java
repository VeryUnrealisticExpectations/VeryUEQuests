package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.gametest.mixin.GameTestHelperAccessor;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

public class QuestTestCleanup implements GameTestListener {
    private final Runnable action;

    QuestTestCleanup(Runnable action) {
        this.action = action;
    }

    static void add(GameTestHelper helper, Runnable action) {
        ((GameTestHelperAccessor) helper).vuequests$getTestInfo().addListener(new QuestTestCleanup(action));
    }

    @Override
    public void testStructureLoaded(GameTestInfo info) {
    }

    @Override
    public void testPassed(GameTestInfo info, GameTestRunner runner) {
        action.run();
    }

    @Override
    public void testFailed(GameTestInfo info, GameTestRunner runner) {
        action.run();
    }

    @Override
    public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
    }
}
