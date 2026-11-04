package dev.veryue.vuequests.exceptions;

import java.util.UUID;

public class PlayerDoesNotExistException extends Exception {

    public PlayerDoesNotExistException(UUID uuid, Throwable cause) {
        super("No quest data exists for player " + uuid, cause);
    }
}
