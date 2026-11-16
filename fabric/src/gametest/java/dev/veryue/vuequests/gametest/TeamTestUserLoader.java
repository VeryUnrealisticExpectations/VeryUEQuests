package dev.veryue.vuequests.gametest;

import dev.veryue.vuequests.data.UserLoader;
import dev.veryue.vuequests.exceptions.UserDoesNotExitException;
import dev.veryue.vuequests.models.User;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TeamTestUserLoader<T extends User> extends UserLoader<T> {
    final UserLoader<T> delegate;
    final String kind;
    final List<String> writes;
    final Set<UUID> failedReads = ConcurrentHashMap.newKeySet();
    final Set<UUID> failedSaves = ConcurrentHashMap.newKeySet();
    final Set<UUID> failedDeletes = ConcurrentHashMap.newKeySet();
    volatile CountDownLatch entered;
    volatile CountDownLatch release;
    volatile Thread lastWriteThread;

    TeamTestUserLoader(UserLoader<T> delegate, String kind, List<String> writes) {
        this.delegate = delegate;
        this.kind = kind;
        this.writes = writes;
    }

    @Override
    public T loadUser(UUID id) throws UserDoesNotExitException {
        if (failedReads.contains(id)) throw new IllegalStateException("Unreadable fixture " + userPath(id));
        return delegate.loadUser(id);
    }

    @Override
    public void saveUser(T user) {
        lastWriteThread = Thread.currentThread();
        writes.add("save " + kind + " " + user.getUuid());
        CountDownLatch gate = release;
        if (gate != null) {
            entered.countDown();
            try {
                if (!gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture write timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        if (failedSaves.contains(user.getUuid())) throw new IllegalStateException("Failed fixture save " + userPath(user.getUuid()));
        delegate.saveUser(user);
    }

    @Override
    public void deleteUser(UUID id) {
        lastWriteThread = Thread.currentThread();
        writes.add("delete " + kind + " " + id);
        if (failedDeletes.contains(id)) throw new IllegalStateException("Failed fixture deletion " + userPath(id));
        delegate.deleteUser(id);
    }

    @Override
    public Map<UUID, T> loadAllUsers() { return delegate.loadAllUsers(); }
    @Override
    public Path userPath(UUID id) { return delegate.userPath(id); }
}
