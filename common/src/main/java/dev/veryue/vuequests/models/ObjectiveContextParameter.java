package dev.veryue.vuequests.models;

import dev.veryue.vuequests.VeryUEConstants;
import net.minecraft.resources.Identifier;

public record ObjectiveContextParameter<T>(Identifier name) {

    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    public static class Builder<T> {

        private final String name;

        public Builder(String name) {
            this.name = name;
        }

        public ObjectiveContextParameter<T> build() {
            return new ObjectiveContextParameter<>(Identifier.fromNamespaceAndPath(VeryUEConstants.MOD_ID, name));
        }
    }
}
