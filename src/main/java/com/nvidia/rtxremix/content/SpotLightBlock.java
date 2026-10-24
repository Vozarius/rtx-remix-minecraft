/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.content;



import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

/**
 * Base for aimable light sources, the lamp room of a lighthouse.
 *
 * <p>Emits a lens-sized Remix disk light with cone shaping, i.e. a
 * <em>spot</em> light.
 *
 * <p>Concrete subclasses differ only in registry id, lens texture, and the
 * colour of the Remix spot light they emit, mirroring how vanilla's
 * {@code BulbBlock} holds the shared behaviour while each copper-oxidation
 * block supplies its own settings.
 *
 * <p>Carries the vanilla {@link net.minecraft.state.property.Properties#FACING}
 * property (all six directions), set to the opposite of the placing player's
 * look direction so the lens faces them, matching vanilla dispensers and
 * observers.
 *
 * <p>Using {@code Properties.FACING} rather than a custom property is
 * deliberate: {@code BlockLightRuleManager}'s {@code StateVectorSpec} reads that
 * exact property for its {@code "facing"}, {@code "facing_opposite"}, and
 * {@code "facing_axis"} vector sources, so a block light rule can aim a Remix
 * light along this block's facing with no additional code.
 *
 * <p>Implements {@link OverridesItemLight}, which suppresses the <em>generic</em>
 * item light {@link LightManager} would otherwise attach to any light-emitting
 * {@code BlockItem}: a beam at world radiance, held at arm's length, would white
 * the whole view out. A held beacon is not dark, though: it gets a dedicated
 * beam from {@link LightManager}, sharing this block's base cone shape but scaled
 * by {@link BeaconBeam#HELD_INTENSITY_SCALE}. The problem was the radiance, not
 * the beam, so it is solved by scaling rather than exclusion.
 * A <em>dropped</em> beacon does stay dark, an item entity having no aim worth
 * pointing a beam along.
 */
public abstract class SpotLightBlock extends FacingBlock {

    public record BeamColor(float r, float g, float b) {}

    /** Full block light level, matching a lantern. */
    protected static final int LUMINANCE = 15;

    private final BeamColor beamColor;

    protected SpotLightBlock(AbstractBlock.Settings settings, BeamColor beamColor) {
        super(settings);
        this.beamColor = beamColor;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    /** Shared settings for every beacon variant. */
    protected static AbstractBlock.Settings beaconSettings() {
        return AbstractBlock.Settings.create()
                .strength(3.0f)
                .sounds(BlockSoundGroup.METAL)
                .luminance(state -> LUMINANCE);
    }

    /**
     * Colour of the Remix spot light this variant emits. Read by
     * {@code BlockLightDefaults} so the light rule does not have to enumerate
     * variants.
     */
    public BeamColor beamColor() {
        return beamColor;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getPlayerLookDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(FACING, mirror.apply(state.get(FACING)));
    }
}
