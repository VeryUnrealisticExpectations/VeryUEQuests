package dev.veryue.vuequests.models;

import com.google.common.collect.Maps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public class ObjectiveContext {

    public static final ObjectiveContextParameter<ServerPlayer> PLAYER = ObjectiveContextParameter.<ServerPlayer>builder("player").build();
    public static final ObjectiveContextParameter<Entity> ENTITY = ObjectiveContextParameter.<Entity>builder("entity").build();
    public static final ObjectiveContextParameter<Identifier> ADVANCEMENT = ObjectiveContextParameter.<Identifier>builder("advancement").build();
    public static final ObjectiveContextParameter<UUID> INTERACTION = ObjectiveContextParameter.<UUID>builder("interaction").build();
    public static final ObjectiveContextParameter<Stat<?>> STATISTIC = ObjectiveContextParameter.<Stat<?>>builder("statistic").build();

    private final ServerLevel level;
    private final Map<ObjectiveContextParameter<?>, Object> params;

    public ObjectiveContext(ServerLevel level, Map<ObjectiveContextParameter<?>, Object> params) {
        this.level = level;
        this.params = params;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public boolean hasParam(ObjectiveContextParameter<?> param) {
        return this.params.containsKey(param);
    }

    @Nullable
    public <T> T getParamOrNull(ObjectiveContextParameter<T> param) {
        return (T) this.params.get(param);
    }

    public static Builder createContext(ServerLevel level) {
        return new Builder(level);
    }

    public static class Builder {

        private final ServerLevel level;
        private final Map<ObjectiveContextParameter<?>, Object> params = Maps.newIdentityHashMap();

        public Builder(ServerLevel level) {
            this.level = level;
        }

        public <T> Builder withParameter(ObjectiveContextParameter<T> parameter, T value) {
            this.params.put(parameter, value);
            return this;
        }

        public ObjectiveContext build() {
            return new ObjectiveContext(level, params);
        }

    }

}
