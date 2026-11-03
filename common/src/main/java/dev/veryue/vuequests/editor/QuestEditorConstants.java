package dev.veryue.vuequests.editor;

import java.util.List;
import java.util.Set;

public class QuestEditorConstants {
    public static final String API = "/api";

    public static final String CREATE = "/create/";
    public static final String MODIFY = "/modify/";
    public static final String CHECK = "/check/";

    public static final String CREATE_QUEST = CREATE + "quest";
    public static final String CREATE_SECTION = CREATE + "section";
    public static final String CREATE_DEPENDENCY = CREATE + "dependency";
    public static final String CREATE_QUESTLINE = CREATE + "questline";
    public static final String MODIFY_QUEST_TEXT = MODIFY + "quest/text";
    public static final String MODIFY_QUEST_OBJECTIVES = MODIFY + "quest/objectives";
    public static final String MODIFY_QUEST_REWARDS = MODIFY + "quest/rewards";
    public static final String MODIFY_QUEST_ICON = MODIFY + "quest/icon";
    public static final String MODIFY_QUEST_POSITION = MODIFY + "quest/position";
    public static final String CHECK_OBJECTIVES = CHECK + "objectives";
    public static final String CHECK_REWARDS = CHECK + "rewards";
    public static final String CHECK_REGISTRIES = CHECK + "registries";
    public static final String CHECK_SECTIONS = CHECK + "sections";
    public static final String CHECK_QUEST = CHECK + "quest";
    public static final String CHECK_QUEST_POSITION = CHECK_QUEST + "/position";
    public static final String SEARCH = "/search";
    public static final String OPENAPI = "/openapi.json";
    public static final String OPENAPI_RESOURCE = "/api/vuequests-openapi.json";

    public static final Set<String> WRITE_ROUTES = Set.of(CREATE_QUEST, CREATE_SECTION, CREATE_DEPENDENCY,
            CREATE_QUESTLINE, MODIFY_QUEST_TEXT, MODIFY_QUEST_OBJECTIVES, MODIFY_QUEST_REWARDS,
            MODIFY_QUEST_ICON, MODIFY_QUEST_POSITION);
    public static final Set<String> READ_ROUTES = Set.of(CHECK_OBJECTIVES, CHECK_REWARDS, CHECK_REGISTRIES,
            CHECK_SECTIONS, CHECK_QUEST, CHECK_QUEST_POSITION, SEARCH, OPENAPI);
    public static final List<String> API_CONTEXTS = List.of(API + CREATE, API + MODIFY, API + CHECK,
            API + SEARCH, API + OPENAPI);

    public static final String EDITOR = "/editor";
    public static final String EDITOR_RESOURCES = EDITOR + "/resources";
    public static final String EDITOR_COMPONENTS = EDITOR + "/components/";
    public static final String EDITOR_COMPONENT_PREVIEW = EDITOR_COMPONENTS + "preview";
    public static final String EDITOR_COMPONENT_VALIDATE = EDITOR_COMPONENTS + "validate";
    public static final String ASSETS_FILE = "/assets/file";
    public static final String ASSETS_LIST = "/assets/list";
}
