package dev.veryue.vuequests.data;

import dev.veryue.vuequests.exceptions.PlayerDoesNotExistException;
import dev.veryue.vuequests.models.User;

import java.util.UUID;

public abstract class UserLoader {

    public abstract User loadUser(UUID uuid) throws PlayerDoesNotExistException, IllegalStateException;

    public abstract boolean saveUser(User user);

}
