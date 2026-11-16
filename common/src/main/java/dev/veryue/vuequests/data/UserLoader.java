package dev.veryue.vuequests.data;

import dev.veryue.vuequests.exceptions.UserDoesNotExitException;
import dev.veryue.vuequests.models.User;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public abstract class UserLoader<T extends User> {
    public abstract T loadUser(UUID uuid) throws UserDoesNotExitException;

    public abstract void saveUser(T user);

    public abstract void deleteUser(UUID uuid);

    public abstract Map<UUID, T> loadAllUsers();

    public abstract Path userPath(UUID uuid);
}
