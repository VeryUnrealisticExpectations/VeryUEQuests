package dev.veryue.vuequests.models.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record ObjectiveArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final MapCodec<ObjectiveArea> CODEC = RecordCodecBuilder.<ObjectiveArea>mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("min_x").forGetter(ObjectiveArea::minX),
            Codec.INT.fieldOf("min_y").forGetter(ObjectiveArea::minY),
            Codec.INT.fieldOf("min_z").forGetter(ObjectiveArea::minZ),
            Codec.INT.fieldOf("max_x").forGetter(ObjectiveArea::maxX),
            Codec.INT.fieldOf("max_y").forGetter(ObjectiveArea::maxY),
            Codec.INT.fieldOf("max_z").forGetter(ObjectiveArea::maxZ)
    ).apply(instance, ObjectiveArea::new)).validate(area -> area.minX <= area.maxX && area.minY <= area.maxY && area.minZ <= area.maxZ
            ? DataResult.success(area) : DataResult.error(() -> "Minimum coordinates must not exceed maximum coordinates."));

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
}
