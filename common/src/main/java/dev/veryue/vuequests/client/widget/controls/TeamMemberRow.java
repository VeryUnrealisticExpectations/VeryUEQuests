package dev.veryue.vuequests.client.widget.controls;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import java.util.function.Supplier;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class TeamMemberRow extends AbstractContainerWidget {

    public static final int HEIGHT = 26;

    private final Font font;
    private final GameProfile profile;
    private final Supplier<PlayerSkin> skinLookup;
    private final boolean owner;
    private final List<Button> controls;
    private final Button kick;
    private final Button confirm;
    private boolean confirming;
    private boolean actionsEnabled = true;

    public TeamMemberRow(Font font, GameProfile profile, boolean owner, boolean canKick, Consumer<UUID> onKick) {
        super(0, 0, 1, HEIGHT, Component.literal(profile.name()), defaultSettings(0));
        this.font = font;
        this.profile = profile;
        skinLookup = Minecraft.getInstance().getSkinManager().createLookup(profile, false);
        this.owner = owner;
        kick = Button.builder(Component.literal("X").withStyle(ChatFormatting.RED), button -> {
            confirming = !confirming;
            positionButtons();
        }).build();
        kick.setTooltip(Tooltip.create(Component.translatable("screen.vuequests.team.kick_player", profile.name())));
        confirm = Button.builder(Component.translatable("screen.vuequests.team.confirm_kick"),
                button -> onKick.accept(profile.id())).build();
        controls = canKick ? List.of(kick, confirm) : List.of();
        positionButtons();
    }

    public void setActionsEnabled(boolean enabled) {
        actionsEnabled = enabled;
        kick.active = enabled;
        confirm.active = enabled && confirming;
    }

    @Override
    public List<Button> children() {
        return controls;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) {
            setFocused(null);
        }
    }

    @Override
    public void setRectangle(int width, int height, int x, int y) {
        super.setRectangle(width, height, x, y);
        positionButtons();
    }

    private void positionButtons() {
        int confirmWidth = Math.min(Math.max(40, font.width(confirm.getMessage()) + 12), Math.max(1, getWidth() / 2));
        confirm.visible = confirming;
        confirm.active = confirming && actionsEnabled;
        confirm.setRectangle(confirmWidth, 20, getRight() - confirmWidth, getY() + 3);
        kick.setRectangle(20, 20, confirming ? confirm.getX() - 24 : getRight() - 20, getY() + 3);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        PlayerInfo info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(profile.id());
        PlayerSkin skin = info == null ? skinLookup.get() : info.getSkin();
        PlayerFaceExtractor.extractRenderState(graphics, skin.body().texturePath(), getX(), getY() + 5, 16, true, false, -1);
        Component label = owner ? Component.translatable("screen.vuequests.team.owner", profile.name()) : getMessage();
        int textRight = controls.isEmpty() ? getRight() : kick.getX() - 4;
        graphics.text(font, Language.getInstance().getVisualOrder(
                        font.substrByWidth(label, Math.max(1, textRight - getX() - 22))),
                getX() + 22, getY() + 9, owner ? 0xFFE5CB7D : 0xFFE0E0E0);
        for (Button control : controls) control.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (getFocused() instanceof Button control) {
            control.updateNarration(output.nest());
        }
    }
}
