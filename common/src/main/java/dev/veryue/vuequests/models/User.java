package dev.veryue.vuequests.models;

import dev.veryue.vuequests.models.quest.Quest;

import java.util.UUID;

public abstract class User {





    public abstract void grantQuestCompletion(Quest quest);

    public abstract void grantQuestCompletion(UUID uuid);



}
