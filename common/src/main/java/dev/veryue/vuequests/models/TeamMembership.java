package dev.veryue.vuequests.models;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record TeamMembership(UUID owner, Map<UUID, TeamMember> members) {
    public static final Codec<TeamMembership> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(TeamMembership::owner),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, TeamMember.CODEC).fieldOf("members").forGetter(TeamMembership::members)
    ).apply(instance, TeamMembership::new));

    public TeamMembership {
        if (!members.containsKey(owner)) {
            throw new IllegalArgumentException("Team owner " + owner + " is not a member");
        }
        members = new HashMap<>(members);
    }
}
