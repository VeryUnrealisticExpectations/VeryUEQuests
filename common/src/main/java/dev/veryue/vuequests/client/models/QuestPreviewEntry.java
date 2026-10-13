package dev.veryue.vuequests.client.models;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record QuestPreviewEntry(Component text, ItemStack icon, int color) {
}
