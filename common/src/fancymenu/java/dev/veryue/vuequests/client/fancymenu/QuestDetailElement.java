package dev.veryue.vuequests.client.fancymenu;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.util.properties.Property;
import de.keksuccino.fancymenu.util.properties.Property.BooleanProperty;
import de.keksuccino.fancymenu.util.properties.Property.FloatProperty;
import de.keksuccino.fancymenu.util.properties.Property.IntegerProperty;
import de.keksuccino.fancymenu.util.properties.Property.StringProperty;
import de.keksuccino.fancymenu.util.resource.ResourceSupplier;
import de.keksuccino.fancymenu.util.resource.resources.texture.ITexture;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPart;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetailPlacement;
import dev.veryue.vuequests.client.widget.panels.LinearQuestDetails;
import dev.veryue.vuequests.client.widget.panels.LinearQuestTextAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

public class QuestDetailElement extends AbstractElement {

    private final LinearQuestDetailPart part;
    private final StringProperty follow;
    private final StringProperty alignment;
    final IntegerProperty gap;
    final FloatProperty titleScale;
    final IntegerProperty spacing;
    final BooleanProperty showHeader;
    final BooleanProperty showDecoration;
    final StringProperty headerLabel;
    final Property<ResourceSupplier<ITexture>> decoration;
    Supplier<QuestPanelElement> editorParent = () -> null;

    public QuestDetailElement(QuestDetailElementBuilder builder, LinearQuestDetailPart part, int y, int height) {
        super(builder);
        this.part = part;
        baseWidth = 168;
        baseHeight = height;
        posOffsetX = 4;
        posOffsetY = y;
        follow = putProperty(Property.stringProperty("vuequests_follow", "", false, false, "fancymenu.vuequests.detail_part.follow"));
        gap = putProperty(Property.integerProperty("vuequests_follow_gap", 12, "fancymenu.vuequests.detail_part.gap"));
        alignment = part == LinearQuestDetailPart.TITLE || part == LinearQuestDetailPart.DESCRIPTION
                ? putProperty(Property.stringProperty("vuequests_text_alignment", defaultAlignment().name(), false, false,
                "fancymenu.vuequests.detail_part.alignment")) : null;
        titleScale = part == LinearQuestDetailPart.TITLE
                ? putProperty(Property.floatProperty("vuequests_title_scale", LinearQuestDetails.TITLE_SCALE,
                "fancymenu.vuequests.detail_part.scale")) : null;
        spacing = part == LinearQuestDetailPart.OBJECTIVES_LIST || part == LinearQuestDetailPart.REWARDS_LIST
                ? putProperty(Property.integerProperty("vuequests_item_spacing", part == LinearQuestDetailPart.OBJECTIVES_LIST ? 7 : 10,
                "fancymenu.vuequests.detail_part.spacing")) : null;
        showHeader = part.isHeader() ? putProperty(Property.booleanProperty("vuequests_show_header", true,
                "fancymenu.vuequests.detail_part.show_header")) : null;
        showDecoration = part.isHeader() ? putProperty(Property.booleanProperty("vuequests_show_decoration", true,
                "fancymenu.vuequests.detail_part.show_decoration")) : null;
        headerLabel = part.isHeader() ? putProperty(Property.stringProperty("vuequests_header_label", "", false, false,
                "fancymenu.vuequests.detail_part.header_label")) : null;
        decoration = part.isHeader() ? putProperty(Property.resourceSupplierProperty(ITexture.class, "vuequests_decoration_texture",
                null, "fancymenu.vuequests.detail_part.decoration", true, true, true, null)) : null;
        setSupportsRotation(false);
        setSupportsTilting(false);
    }

    LinearQuestDetailPart part() {
        return part;
    }

    LinearQuestDetailPart followPart() {
        String value = follow.tryGetNonNull();
        for (LinearQuestDetailPart candidate : LinearQuestDetailPart.values()) {
            if (candidate != part && candidate.id.equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    void setFollowPart(LinearQuestDetailPart part) {
        follow.set(part == null ? "" : part.id);
    }

    boolean hasAlignment() {
        return alignment != null;
    }

    LinearQuestTextAlignment textAlignment() {
        if (alignment != null) {
            for (LinearQuestTextAlignment candidate : LinearQuestTextAlignment.values()) {
                if (candidate.name().equals(alignment.tryGetNonNull())) {
                    return candidate;
                }
            }
        }
        return defaultAlignment();
    }

    void setTextAlignment(LinearQuestTextAlignment value) {
        alignment.set(value.name());
    }

    private LinearQuestTextAlignment defaultAlignment() {
        return part == LinearQuestDetailPart.TITLE ? LinearQuestTextAlignment.CENTER : LinearQuestTextAlignment.LEFT;
    }

    LinearQuestDetailPlacement placement(QuestPanelElement parent) {
        float scale = titleScale == null ? 1 : titleScale.getFloat();
        scale = Float.isFinite(scale) ? Mth.clamp(scale, .5F, 4) : LinearQuestDetails.TITLE_SCALE;
        String label = headerLabel == null ? "" : headerLabel.tryGetNonNull();
        ResourceSupplier<ITexture> texture = decoration == null ? null : decoration.get();
        return new LinearQuestDetailPlacement(relativeBounds(parent), followPart(), Mth.clamp(gap.getInteger(), 0, 128),
                textAlignment(), scale, spacing == null ? 0 : Mth.clamp(spacing.getInteger(), 0, 64),
                showHeader == null || showHeader.getBoolean(), showDecoration == null || showDecoration.getBoolean(),
                label.isBlank() ? null : buildComponent(label), texture == null ? null : new FancyMenuHeadingTexture(texture));
    }

    ScreenRectangle relativeBounds(QuestPanelElement parent) {
        int parentWidth = Math.max(1, parent.getAbsoluteWidth());
        int parentHeight = Math.max(1, parent.getAbsoluteHeight());
        int width = Mth.clamp(super.getAbsoluteWidth(), 1, parentWidth);
        int height = Mth.clamp(super.getAbsoluteHeight(), 1, parentHeight);
        int x = advancedX.isDefault() ? posOffsetX : advancedX.getInteger();
        int y = advancedY.isDefault() ? posOffsetY : advancedY.getInteger();
        return new ScreenRectangle(Mth.clamp(x, 0, parentWidth - width), Mth.clamp(y, 0, parentHeight - height), width, height);
    }

    @Override
    protected int getAbsoluteX(boolean includeParallax) {
        QuestPanelElement parent = editorParent.get();
        return parent == null ? 0 : parent.getAbsoluteX() + relativeBounds(parent).left();
    }

    @Override
    protected int getAbsoluteY(boolean includeParallax) {
        QuestPanelElement parent = editorParent.get();
        return parent == null ? 0 : parent.getAbsoluteY() + relativeBounds(parent).top();
    }

    @Override
    public int getAbsoluteWidth() {
        QuestPanelElement parent = editorParent.get();
        return parent == null ? 0 : relativeBounds(parent).width();
    }

    @Override
    public int getAbsoluteHeight() {
        QuestPanelElement parent = editorParent.get();
        return parent == null ? 0 : relativeBounds(parent).height();
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        QuestPanelElement parent = isEditor() ? editorParent.get() : null;
        if (parent != null) {
            ScreenRectangle bounds = relativeBounds(parent);
            ScreenRectangle marker = new ScreenRectangle(parent.getAbsoluteX() + bounds.left(), parent.getAbsoluteY() + bounds.top(),
                    bounds.width(), bounds.height());
            QuestPanelElement.renderMarker(graphics, marker, getDisplayName());
        }
    }
}
