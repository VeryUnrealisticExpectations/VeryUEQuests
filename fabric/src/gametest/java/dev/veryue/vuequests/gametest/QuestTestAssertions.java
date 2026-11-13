package dev.veryue.vuequests.gametest;

import java.util.Objects;

public class QuestTestAssertions {
    static void check(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }

    static void equal(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError(actual + " != " + expected);
        }
    }
}
