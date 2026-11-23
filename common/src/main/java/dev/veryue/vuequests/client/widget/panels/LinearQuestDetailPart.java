package dev.veryue.vuequests.client.widget.panels;

public enum LinearQuestDetailPart {
    TITLE("quest_title"),
    DESCRIPTION("quest_description"),
    OBJECTIVES_HEADER("objectives_header"),
    OBJECTIVES_LIST("objectives_list"),
    REWARDS_HEADER("rewards_header"),
    REWARDS_LIST("rewards_list");

    public final String id;

    LinearQuestDetailPart(String id) {
        this.id = id;
    }

    public boolean isHeader() {
        return this == OBJECTIVES_HEADER || this == REWARDS_HEADER;
    }

    public LinearQuestDetailPart previous() {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }
}
