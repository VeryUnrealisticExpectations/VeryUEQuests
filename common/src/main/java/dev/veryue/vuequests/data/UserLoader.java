package dev.veryue.vuequests.data;

import dev.veryue.vuequests.models.User;

import java.util.UUID;

public abstract class UserLoader {

    public abstract User loadUser(UUID uuid);

    public abstract boolean saveUser(User user);

}
