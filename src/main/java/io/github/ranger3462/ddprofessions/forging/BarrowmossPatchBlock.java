package io.github.ranger3462.ddprofessions.forging;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Barrowmoss as it grows: a patch on a wall, floor or ceiling, like glow lichen but dim. It grows only in undead
 * dungeons (Dungeon Delving places it), and breaking it gives Barrowmoss. Bone meal doesn't spread it, so it can't be
 * farmed.
 */
public class BarrowmossPatchBlock extends GlowLichenBlock {
    public BarrowmossPatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return false;
    }
}
