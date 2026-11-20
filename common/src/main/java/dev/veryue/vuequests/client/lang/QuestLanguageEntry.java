package dev.veryue.vuequests.client.lang;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

record QuestLanguageEntry(JsonElement json, Component component) { }
