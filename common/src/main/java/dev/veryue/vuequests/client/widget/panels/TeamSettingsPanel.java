package dev.veryue.vuequests.client.widget.panels;

import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.input.KeyEvent;

import com.mojang.authlib.GameProfile;
import dev.veryue.vuequests.client.ClientQuestData;
import dev.veryue.vuequests.client.ClientTeam;
import dev.veryue.vuequests.client.ClientTeamData;
import dev.veryue.vuequests.client.ClientTeamInvitation;
import dev.veryue.vuequests.client.widget.controls.TeamMemberRow;
import dev.veryue.vuequests.models.TeamSetting;
import dev.veryue.vuequests.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class TeamSettingsPanel extends SettingsPanel {

    private final Font font;
    private final Consumer<CustomPacketPayload> sendToServer;
    private UUID disbandConfirmation;
    private long revision = -1;
    private ClientTeam displayedTeam;
    private String nameDraft = "";
    private String inviteDraft = "";
    private EditBox nameBox;
    private EditBox inviteBox;
    private Button applyButton;
    private Button sendButton;

    public TeamSettingsPanel(Font font, Consumer<CustomPacketPayload> sendToServer) {
        super(text("teams"));
        this.font = font;
        this.sendToServer = sendToServer;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.vuequests.team." + key, args);
    }

    private void label(Component message, int y, int color) {
        addControl(new StringWidget(message.copy().withStyle(style -> style.withColor(color)), font), 0, y, contentWidth(), 12);
    }

    @Override
    protected void rebuildControls() {
        ClientTeam team = ClientTeamData.getTeam();
        if (team == null || displayedTeam == null || !team.id().equals(displayedTeam.id())) {
            nameDraft = team == null ? "" : team.name();
            inviteDraft = "";
        } else if (nameDraft.equals(displayedTeam.name())) {
            nameDraft = team.name();
        }
        displayedTeam = team;
        revision = ClientTeamData.getRevision();
        clearControls();
        nameBox = null;
        inviteBox = null;
        applyButton = null;
        sendButton = null;
        if (!ClientQuestData.isDataReady()) {
            int y = feedback(ClientQuestData.getDataError(), 0, 0xFFFF7777);
            setContentHeight(y);
            return;
        }
        if (team == null) {
            buildTeamless();
        } else {
            buildTeam(team);
        }
        if (ClientTeamData.isPending()) {
            for (AbstractWidget control : children()) {
                if (!(control instanceof StringWidget)) {
                    control.active = false;
                }
                if (control instanceof TeamMemberRow row) {
                    row.setActionsEnabled(false);
                }
            }
        }
    }

    private void buildTeamless() {
        int width = contentWidth();
        label(text("teams").copy().withStyle(style -> style.withBold(true)), 0, 0xFFE0E0E0);
        Button create = addControl(Button.builder(text("create"), button -> createTeam()).build(), 0, 20, width, 20);
        create.active = Minecraft.getInstance().player != null;
        label(text("invitations").copy().withStyle(style -> style.withBold(true)), 54, 0xFFE0E0E0);
        int y = 74;
        if (ClientTeamData.getInvitations().isEmpty()) {
            label(text("no_invitations"), y, 0xFFAAAAAA);
            y += 16;
        }
        for (ClientTeamInvitation invitation : ClientTeamData.getInvitations()) {
            label(Component.literal(invitation.teamName()), y, 0xFFE0E0E0);
            label(text("invited_by", invitation.inviter().name()), y + 13, 0xFFAAAAAA);
            int buttonWidth = Math.max(1, (width - 4) / 2);
            addControl(Button.builder(text("accept"), button -> acceptInvitation(invitation)).build(),
                    0, y + 29, buttonWidth, 20);
            addControl(Button.builder(text("deny"), button -> denyInvitation(invitation)).build(),
                    buttonWidth + 4, y + 29, width - buttonWidth - 4, 20);
            y += 60;
        }
        setContentHeight(actionFeedback(y));
    }

    private void buildTeam(ClientTeam team) {
        Minecraft client = Minecraft.getInstance();
        boolean owner = client.player != null && team.isOwner(client.player.getUUID());
        int width = contentWidth();
        int y;
        if (owner) {
            label(text("name"), 0, 0xFFE0E0E0);
            int applyWidth = Math.min(width / 2, Math.max(48, font.width(text("apply")) + 12));
            int nameWidth = Math.max(1, width - applyWidth - 4);
            nameBox = new EditBox(font, nameWidth, 20, text("name"));
            nameBox.setMaxLength(64);
            nameBox.setValue(nameDraft);
            addControl(nameBox, 0, 16, nameWidth, 20);
            applyButton = addControl(Button.builder(text("apply"), button -> applyName()).build(),
                    width - applyWidth, 16, applyWidth, 20);
            nameBox.setResponder(value -> {
                nameDraft = value;
                ClientTeam current = ownedTeam();
                applyButton.active = current != null && !value.isBlank() && !value.strip().equals(current.name());
            });
            applyButton.active = !nameDraft.isBlank() && !nameDraft.strip().equals(team.name());
            CycleButton<Boolean> share = CycleButton.onOffBuilder(team.sharingProgressWithTeamMembers())
                    .create(0, 0, width, 20, text("share_progress"), (button, value) -> setSharingProgress(value));
            share.setTooltip(Tooltip.create(text("share_progress.description")));
            addControl(share, 0, 44, width, 20);
            CycleButton<Boolean> keep = CycleButton.onOffBuilder(team.keepProgressWhenLeavingTeam())
                    .create(0, 0, width, 20, text("keep_progress"), (button, value) -> setKeepProgress(value));
            keep.setTooltip(Tooltip.create(text("keep_progress.description")));
            addControl(keep, 0, 70, width, 20);
            label(text("invite_player"), 102, 0xFFE0E0E0);
            int sendWidth = Math.min(width / 2, Math.max(48, font.width(text("send")) + 12));
            int inviteWidth = Math.max(1, width - sendWidth - 4);
            inviteBox = new EditBox(font, inviteWidth, 20, text("invite_player"));
            inviteBox.setMaxLength(16);
            inviteBox.setHint(text("player_name"));
            inviteBox.setValue(inviteDraft);
            addControl(inviteBox, 0, 118, inviteWidth, 20);
            sendButton = addControl(Button.builder(text("send"), button -> sendInvitation()).build(),
                    width - sendWidth, 118, sendWidth, 20);
            inviteBox.setResponder(value -> {
                if (!value.matches("[A-Za-z0-9_]*")) {
                    inviteBox.setValue(inviteDraft);
                    return;
                }
                inviteDraft = value;
                sendButton.active = canInvite(value);
            });
            sendButton.active = canInvite(inviteDraft);
            y = 152;
        } else {
            label(Component.literal(team.name()).withStyle(style -> style.withBold(true)), 0, 0xFFE0E0E0);
            y = 24;
        }
        label(text("members").copy().withStyle(style -> style.withBold(true)), y, 0xFFE0E0E0);
        y += 18;
        List<GameProfile> members = team.members().stream()
                .sorted(Comparator.comparing((GameProfile member) -> !member.id().equals(team.ownerId()))
                        .thenComparing(GameProfile::name, String.CASE_INSENSITIVE_ORDER)).toList();
        for (GameProfile member : members) {
            boolean teamOwner = member.id().equals(team.ownerId());
            addControl(new TeamMemberRow(font, member, teamOwner, owner && !teamOwner, this::kickMember),
                    0, y, width, TeamMemberRow.HEIGHT);
            y += TeamMemberRow.HEIGHT;
        }
        y += 8;
        if (owner && team.id().equals(disbandConfirmation)) {
            y = feedback(text("disband_warning"), y, 0xFFFFAA77);
            int buttonWidth = Math.max(1, (width - 4) / 2);
            addControl(Button.builder(text("confirm_disband"), button -> request(id -> new LeaveTeamPayload(id, team.id()))).build(),
                    0, y, buttonWidth, 20);
            addControl(Button.builder(Component.translatable("gui.cancel"), button -> {
                disbandConfirmation = null;
                rebuildControls();
            }).build(), buttonWidth + 4, y, width - buttonWidth - 4, 20);
        } else {
            addControl(Button.builder(text(owner ? "disband" : "leave"), button -> {
                if (owner) {
                    disbandConfirmation = team.id();
                    rebuildControls();
                } else {
                    request(id -> new LeaveTeamPayload(id, team.id()));
                }
            }).build(), 0, y, width, 20);
        }
        setContentHeight(actionFeedback(y + 24));
    }

    private int feedback(Component message, int y, int color) {
        for (FormattedCharSequence line : font.split(message, contentWidth())) {
            StringBuilder value = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                value.appendCodePoint(codePoint);
                return true;
            });
            label(Component.literal(value.toString()), y, color);
            y += 12;
        }
        return y;
    }

    private int actionFeedback(int y) {
        Component message = ClientTeamData.isPending() ? text("pending") : ClientTeamData.getFeedback();
        return feedback(message, y + 4, ClientTeamData.hasError() ? 0xFFFF7777 : 0xFFAAAAAA);
    }

    private void request(Function<UUID, TeamRequestPayload> create) {
        ClientTeamData.request(create, sendToServer);
        rebuildControls();
    }

    private boolean canInvite(String name) {
        ClientTeam team = ClientTeamData.getTeam();
        return !ClientTeamData.isPending() && team != null && name.length() >= 3
                && team.members().stream().noneMatch(member -> member.name().equalsIgnoreCase(name));
    }

    private ClientTeam ownedTeam() {
        ClientTeam team = ClientTeamData.getTeam();
        Minecraft client = Minecraft.getInstance();
        return team != null && client.player != null && team.isOwner(client.player.getUUID()) ? team : null;
    }

    private void createTeam() {
        request(CreateTeamPayload::new);
    }

    private void applyName() {
        ClientTeam team = ownedTeam();
        if (team != null && !nameDraft.isBlank()) {
            request(id -> new RenameTeamPayload(id, team.id(), nameDraft.strip()));
        }
    }

    private void setSharingProgress(boolean value) {
        ClientTeam team = ownedTeam();
        if (team != null) {
            request(id -> new ChangeTeamSettingPayload(id, team.id(), TeamSetting.SHARING_PROGRESS, value));
        }
    }

    private void setKeepProgress(boolean value) {
        ClientTeam team = ownedTeam();
        if (team != null) {
            request(id -> new ChangeTeamSettingPayload(id, team.id(), TeamSetting.KEEP_PROGRESS, value));
        }
    }

    private void sendInvitation() {
        ClientTeam team = ownedTeam();
        String playerName = inviteDraft;
        if (team != null && canInvite(playerName)) {
            request(id -> new InviteTeamPlayerPayload(id, team.id(), playerName));
        }
    }

    private void acceptInvitation(ClientTeamInvitation invitation) {
        request(id -> new RespondTeamInvitationPayload(id, invitation.teamId(), true));
    }

    private void denyInvitation(ClientTeamInvitation invitation) {
        request(id -> new RespondTeamInvitationPayload(id, invitation.teamId(), false));
    }

    private void kickMember(UUID playerId) {
        ClientTeam team = ownedTeam();
        if (team != null && !team.ownerId().equals(playerId)) {
            request(id -> new KickTeamMemberPayload(id, team.id(), playerId));
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (revision != ClientTeamData.getRevision()) rebuildControls();
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();
        if (keyCode == SDLScancode.SDL_SCANCODE_RETURN || keyCode == SDLScancode.SDL_SCANCODE_KP_ENTER) {
            Button action = getFocused() == nameBox ? applyButton : getFocused() == inviteBox ? sendButton : null;
            if (action != null && action.active) {
                action.playDownSound(Minecraft.getInstance().getSoundManager());
                action.onPress(event);
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
