package dev.veryue.vuequests.gametest;

import net.minecraft.server.level.ServerLevel;

public class TeamTestEnvironment {
    static final ThreadLocal<ServerLevel> level = new ThreadLocal<>();
}
