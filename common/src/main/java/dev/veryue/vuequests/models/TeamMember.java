package dev.veryue.vuequests.models;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record TeamMember(String lastKnownName, Set<UUID> claimedRewards) {
    public static final Codec<TeamMember> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("lastKnownName").forGetter(TeamMember::lastKnownName),
            User.UUID_SET_CODEC.fieldOf("claimedRewards").forGetter(TeamMember::claimedRewards)
    ).apply(instance, TeamMember::new));

    public TeamMember {
        if (lastKnownName == null || lastKnownName.isBlank()) {
            throw new IllegalArgumentException("A team member must have a last-known name");
        }
        claimedRewards = new HashSet<>(claimedRewards);
    }
}
