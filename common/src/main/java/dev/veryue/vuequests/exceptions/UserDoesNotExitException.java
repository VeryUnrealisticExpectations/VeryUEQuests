package dev.veryue.vuequests.exceptions;

import java.util.UUID;

public class UserDoesNotExitException extends Exception {

    public UserDoesNotExitException(UUID uuid, Throwable cause) {
        super("No quest data exists for player " + uuid, cause);
    }
}
