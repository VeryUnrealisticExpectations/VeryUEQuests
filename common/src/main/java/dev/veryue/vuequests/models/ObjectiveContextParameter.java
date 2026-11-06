package dev.veryue.vuequests.models;

import dev.veryue.vuequests.VeryUEConstants;
import net.minecraft.resources.ResourceLocation;

public record ObjectiveContextParameter<T>(ResourceLocation name) {

    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    public static class Builder<T> {

        private final String name;

        public Builder(String name) {
            this.name = name;
        }

        public ObjectiveContextParameter<T> build() {
            return new ObjectiveContextParameter<>(ResourceLocation.fromNamespaceAndPath(VeryUEConstants.MOD_ID, name));
        }
    }
}
