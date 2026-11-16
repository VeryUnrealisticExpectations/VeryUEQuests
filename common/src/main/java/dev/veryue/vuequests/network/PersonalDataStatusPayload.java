package dev.veryue.vuequests.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public record PersonalDataStatusPayload(boolean ready, Component message) implements CustomPacketPayload {
    public static final Type<PersonalDataStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "personal_data_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PersonalDataStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PersonalDataStatusPayload::ready,
            ComponentSerialization.TRUSTED_STREAM_CODEC, PersonalDataStatusPayload::message,
            PersonalDataStatusPayload::new);
    @Override
    public Type<PersonalDataStatusPayload> type() { return TYPE; }
}
