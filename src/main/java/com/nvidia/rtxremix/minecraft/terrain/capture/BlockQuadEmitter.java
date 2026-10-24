/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

import com.nvidia.rtxremix.minecraft.compat.SpatialFrames;
import com.nvidia.rtxremix.minecraft.compat.QuadColors;
import com.nvidia.rtxremix.minecraft.compat.SpatialFrame;
import com.nvidia.rtxremix.RemixAPI;
import com.nvidia.rtxremix.minecraft.light.BlockLightRuleManager;
import com.nvidia.rtxremix.minecraft.texture.BlockTextureManager;
import com.nvidia.rtxremix.minecraft.light.LightEntry;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-block mesh emission for RTX Remix, designed to be driven from
 * {@link com.nvidia.rtxremix.minecraft.mixin.SectionBuilderMixin} redirects
 * that piggyback on MC's own block iteration loop.
 *
 * <p>Lifecycle per section:
 * <ol>
 *   <li>{@link #beginSection}: reset state, called at HEAD of build()</li>
 *   <li>{@link #processBlock}: opacity/masks/lights, called per block from isOpaqueFullCube redirect</li>
 *   <li>{@link #emitBlock}: block model quads for MODEL blocks, called from the same
 *       isOpaqueFullCube redirect; vanilla's renderBlock never runs because captured
 *       blocks are reported invisible</li>
 *   <li>{@link #emitFluid}: fluid quads, called from renderFluid redirect</li>
 *   <li>{@link #finishSection}: build SectionBlockData, called at RETURN</li>
 * </ol>
 *
 * <p>One instance per worker thread via {@code ThreadLocal}.
 */
public final class BlockQuadEmitter {

    private static final RenderLayer[] CHUNK_LAYERS =
            RenderLayer.getBlockLayers().toArray(RenderLayer[]::new);

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    private static final Direction[] ALL_DIRECTIONS = {
        null, Direction.UP, Direction.DOWN, Direction.NORTH,
        Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private static final ThreadLocal<BlockQuadEmitter> INSTANCE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> threadNamed =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final Sprite[] fluidSprites = new Sprite[3];
    private final LocalRandom random = new LocalRandom(42);
    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();
    private final BlockPos.Mutable neighborPos = new BlockPos.Mutable();
    private final BlockPos.Mutable fluidPos = new BlockPos.Mutable();
    private final BlockPos.Mutable fluidCheckPos = new BlockPos.Mutable();
    private final DirectQuadWriter writer = new DirectQuadWriter();
    private final ModelSampling modelSampling = new ModelSampling();
    private final List<LightEntry> lights = new ArrayList<>();

    private static final int TINT_CACHE_SIZE = 8;
    private static final int SECTION_SIZE = 16;
    private static final int LOCAL_BLOCK_COUNT = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    private static final int WATER_COLOR_CACHE_SIZE = 4 * 2 * 4;
    private static final float WATER_CEILING_CULL_MIN_HEIGHT = 8.0f / 9.0f;
    private static final float WATER_HEIGHT_EPSILON = 0.0001f;
    private static final int WATER_TOP_CULL_RADIUS = 2;
    private static final int WATER_TOP_CULL_WIDTH = WATER_TOP_CULL_RADIUS * 2 + 1;
    private static final int WATER_TOP_CULL_CELL_COUNT = WATER_TOP_CULL_WIDTH * WATER_TOP_CULL_WIDTH;
    private static final int WATER_TOP_CACHE_WIDTH = SECTION_SIZE + WATER_TOP_CULL_RADIUS * 2;
    private static final int WATER_TOP_CACHE_CELL_COUNT =
            WATER_TOP_CACHE_WIDTH * SECTION_SIZE * WATER_TOP_CACHE_WIDTH;
    // BlockState render type is immutable; cache by raw state id to avoid a per-block virtual call.
    private static volatile BlockRenderType[] renderTypeByStateId = new BlockRenderType[0];

    private final float[] tempPositions = new float[12];
    private final float[] tempUvs = new float[8];
    private final int[] tempColors = new int[4];
    private final int[] tintCache = new int[TINT_CACHE_SIZE];
    private final int[] waterColorCache = new int[WATER_COLOR_CACHE_SIZE];
    private final byte[] waterTopCellCache = new byte[WATER_TOP_CACHE_CELL_COUNT];
    private final int[] waterTopCullStack = new int[WATER_TOP_CULL_CELL_COUNT];
    private final FluidTopOcclusion.CellClassifier waterTopCellClassifier =
            this::classifyWaterTopCellAtOffset;
    private final BlockState[] blockStateCache = new BlockState[LOCAL_BLOCK_COUNT];
    private final FluidState[] fluidStateCache = new FluidState[LOCAL_BLOCK_COUNT];

    /**
     * Results of {@link Block#shouldDrawSide}, keyed like vanilla's own cache by (state,
     * neighbour state, side) and therefore position-independent to the same degree. Vanilla's
     * cache allocates its lookup key on every opaque-neighbour face; this direct-mapped table
     * of packed state ids allocates nothing on a hit and defers to vanilla on a miss.
     */
    private static final int FACE_CULL_CACHE_BITS = 13;
    private static final byte FACE_CULL_UNKNOWN = 0;
    private static final byte FACE_CULL_HIDDEN = 1;
    private static final byte FACE_CULL_DRAWN = 2;
    private final long[] faceCullKeys = new long[1 << FACE_CULL_CACHE_BITS];
    private final byte[] faceCullResults = new byte[1 << FACE_CULL_CACHE_BITS];

    /**
     * Model-randomisation seed for the block being emitted, mirroring vanilla's
     * {@code state.getRenderingSeed(pos)}. Set once per block in {@link #emitBlock}.
     *
     * <p>A multi-variant block picks its variant from this, so a constant makes
     * every instance identical. Where the variants are rotations of one model
     * that is not merely a repeated texture but misplaced geometry: a sea
     * pickle cluster rotates its pickles around the block.
     */
    private long modelRandomSeed = 42L;

    // Per-section state set by beginSection / processBlock
    /**
     * Whether {@link #beginSection} has run without a matching {@link #finishSection}.
     * Capture is gated on it, and a build that never reaches its RETURN inject leaves it
     * set, so the next build on this thread releases the writer's grown buffers before
     * it starts.
     */
    private boolean active;
    private SpatialFrame frame;
    private BlockTextureManager texManager;
    private BlockRenderManager blockRenderManager;
    private BlockColors blockColors;
    private int baseX, baseY, baseZ;
    private long topMask, bottomMask, eastMask, westMask, southMask, northMask;
    private final long[] opacity = new long[64];
    private final long[] blockStateKnown = new long[64];
    private final long[] fluidStateKnown = new long[64];
    private int blockCount, fluidCount;
    private int tintValidMask;
    private int waterColorKnownMask;
    private FluidState waterTopCellCacheFluid;
    private BlockRenderView waterTopCullWorld;
    private FluidState waterTopCullReferenceFluid;
    private int waterTopCullOriginX, waterTopCullOriginY, waterTopCullOriginZ;

    private BlockQuadEmitter() {}

    /** Returns the per-thread instance. */
    public static BlockQuadEmitter threadLocal() {
        BlockQuadEmitter emitter = INSTANCE.get();
        if (emitter == null) {
            emitter = new BlockQuadEmitter();
            INSTANCE.set(emitter);
        }
        return emitter;
    }

    /** Returns the current per-thread instance without allocating one. */
    public static BlockQuadEmitter current() {
        return INSTANCE.get();
    }

    public static int getWaterTopOcclusionRadius() {
        return WATER_TOP_CULL_RADIUS;
    }

    // --- Section lifecycle (called from mixin) ---

    /**
     * Initialises emission state for a new section. Called from the
     * SectionBuilderMixin HEAD inject before MC's block loop starts.
     */
    public void beginSection(ChunkSectionPos sectionPos,
                             BlockTextureManager tex,
                             BlockRenderManager brm) {
        if (!threadNamed.get()) {
            RemixAPI.tracyThreadName("Worker " + Thread.currentThread().getName());
            threadNamed.set(Boolean.TRUE);
        }
        if (active) {
            writer.releaseBuffers();
        }
        this.active = true;
        this.texManager = tex;
        this.blockRenderManager = brm;
        this.baseX = sectionPos.getMinX();
        this.baseY = sectionPos.getMinY();
        this.baseZ = sectionPos.getMinZ();

        MinecraftClient client = MinecraftClient.getInstance();
        this.frame = SpatialFrames.find(
                client == null ? null : client.world, sectionPos.getX(), sectionPos.getZ());
        this.blockColors = (client != null) ? client.getBlockColors() : null;

        writer.beginSection(baseX, baseY, baseZ);
        lights.clear();
        topMask = bottomMask = eastMask = westMask = southMask = northMask = 0;
        Arrays.fill(opacity, 0);
        Arrays.fill(blockStateKnown, 0);
        Arrays.fill(fluidStateKnown, 0);
        // Clear the water classification cache lazily on the first eligible
        // surface so sections without covered water pay no cache-reset cost.
        waterTopCellCacheFluid = null;
        waterColorKnownMask = 0;
        blockCount = 0;
        fluidCount = 0;
    }

    /** Captures a renderer's finished quad without evaluating its model again. */
    public void stageRenderedQuad(float[] positions, float[] uvs, int[] colors,
                                  float nx, float ny, float nz, Sprite sprite, String layer) {
        Identifier id = sprite == null ? null : sprite.getContents().getId();
        if (isWaterSprite(id) || (texManager != null && texManager.fluidBucket(id) != null)) {
            System.arraycopy(positions, 0, tempPositions, 0, 12);
            emitBlockWaterQuad(nx, ny, nz, colors[0], layer, colors, id);
            return;
        }
        writer.stageQuad(positions, uvs, nx, ny, nz, colors[0], id,
                sprite == null ? 0 : sprite.getMinU(), sprite == null ? 0 : sprite.getMinV(),
                sprite == null ? 1 : sprite.getMaxU(), sprite == null ? 1 : sprite.getMaxV(), layer);
        writer.stageVertexColors(colors);
    }

    public void flushRenderedBlock() {
        writer.flushBlock(texManager);
    }

    /** Releases an interrupted build without publishing partial geometry. */
    public void abortSection() {
        active = false;
        writer.releaseBuffers();
        lights.clear();
    }

    public boolean isActive() { return active; }
    public int getBlockCount() { return blockCount; }
    public int getFluidCount() { return fluidCount; }

    public static BlockRenderType getRenderTypeCached(BlockState state) {
        int id = Block.getRawIdFromState(state);
        BlockRenderType[] cache = renderTypeByStateId;
        if (id < cache.length) {
            BlockRenderType renderType = cache[id];
            if (renderType != null) {
                return renderType;
            }
        }

        BlockRenderType renderType = state.getRenderType();
        cache = ensureRenderTypeCacheCapacity(id);
        cache[id] = renderType;
        return renderType;
    }

    private static BlockRenderType[] ensureRenderTypeCacheCapacity(int id) {
        BlockRenderType[] cache = renderTypeByStateId;
        if (id < cache.length) {
            return cache;
        }

        synchronized (BlockQuadEmitter.class) {
            cache = renderTypeByStateId;
            if (id >= cache.length) {
                int newSize = Math.max(id + 1, Math.max(1024, cache.length * 2));
                renderTypeByStateId = Arrays.copyOf(cache, newSize);
            }
            return renderTypeByStateId;
        }
    }

    /**
     * Captures opacity bitmap, boundary face masks, and light-emitting
     * blocks. Called once per block from the isOpaqueFullCube redirect.
     */
    public void processBlock(BlockState state, BlockView world, BlockPos pos, boolean opaque) {
        int lx = ChunkSectionPos.getLocalCoord(pos.getX());
        int ly = ChunkSectionPos.getLocalCoord(pos.getY());
        int lz = ChunkSectionPos.getLocalCoord(pos.getZ());

        int bitIdx = (ly << 8) | (lz << 4) | lx;
        cacheLocalBlockState(bitIdx, state);

        long bit = 1L << (bitIdx & 63);
        int word = bitIdx >> 6;
        if (opaque) {
            opacity[word] |= bit;
        } else {
            opacity[word] &= ~bit;
        }

        if (!opaque && (ly == 0 || ly == 15 || lx == 0 || lx == 15 || lz == 0 || lz == 15)) {
            if (ly == 15) topMask    |= 1L << ((lz >> 1) * 8 + (lx >> 1));
            if (ly == 0)  bottomMask |= 1L << ((lz >> 1) * 8 + (lx >> 1));
            if (lx == 15) eastMask   |= 1L << ((ly >> 1) * 8 + (lz >> 1));
            if (lx == 0)  westMask   |= 1L << ((ly >> 1) * 8 + (lz >> 1));
            if (lz == 15) southMask  |= 1L << ((ly >> 1) * 8 + (lx >> 1));
            if (lz == 0)  northMask  |= 1L << ((ly >> 1) * 8 + (lx >> 1));
        }

        if (state.isAir()) return;
        int luminance = state.getLightEmission(world, pos);
        if (!BlockLightRuleManager.hasMatchingRules(state, luminance)) return;

        // The sampler needs the same seed the mesh uses, so a rule reading
        // model geometry sees the variant this position actually draws.
        //
        // Lights resolve against the unoffset block position, and the sampler
        // reads the unoffset baked model, while emitBlock folds
        // state.getModelOffset into the geometry origin. No vanilla block
        // combines an AbstractBlock.OffsetType with a luminance, so the two
        // frames agree for every vanilla block; a modded or resource-pack rule
        // on an offset block would need the same offset passed into begin() and
        // into the LightEntry positions.
        modelSampling.begin(blockRenderManager, state, state.getRenderingSeed(pos), (BlockRenderView) world, pos);
        List<LightEntry> resolvedLights = BlockLightRuleManager.resolve(state, pos, modelSampling, luminance);
        lights.addAll(resolvedLights);
    }

    /**
     * Emits block model quads for the current block. Called from the
     * isOpaqueFullCube redirect for blocks vanilla would hand to renderBlock,
     * a call the build then skips by reporting the block invisible. Bypasses
     * the OpenGL renderer and writes directly to the DirectQuadWriter.
     */
    public void emitBlock(BlockState state, BlockPos pos, BlockRenderView world) {
        blockCount++;
        mutablePos.set(pos);
        tintValidMask = 0;

        // Vanilla derives model randomisation from the block position, in the
        // same BlockModelRenderer.render this emitter replaces. It also
        // re-seeds before every getQuads call, which the loops below match.
        modelRandomSeed = state.getRenderingSeed(pos);

        float lx = ChunkSectionPos.getLocalCoord(pos.getX());
        float ly = ChunkSectionPos.getLocalCoord(pos.getY());
        float lz = ChunkSectionPos.getLocalCoord(pos.getZ());

        // Vanilla applies the per-position model offset (AbstractBlock.OffsetType
        // XZ/XYZ: flowers, saplings, grass, dripstone) inside
        // BlockModelRenderer.render, which this emitter replaces wholesale.
        // Applying it here is what keeps Remix geometry on the same spot as
        // vanilla's.
        //
        // Folded into the section-local origin rather than applied per vertex:
        // both model paths already add that origin to every position, so the
        // offset rides along for free. hasModelOffset() is a bare null check on
        // the block's offsetter, while getModelOffset() allocates a Vec3d, so
        // the gate keeps that allocation off a path that runs for every block in
        // every section build.
        //
        // This moves geometry only. Block lights are resolved in processBlock
        // against the unoffset grid position; see the note there before adding a
        // light rule to a block that offsets.
        if (state.hasModelOffset()) {
            Vec3d modelOffset = state.getModelOffset(world, pos);
            lx += (float) modelOffset.x;
            ly += (float) modelOffset.y;
            lz += (float) modelOffset.z;
        }

        BakedModel model = blockRenderManager.getModel(state);

        ModelData data = model.getModelData(world, mutablePos, state, world.getModelData(mutablePos));
        random.setSeed(modelRandomSeed);
        var renderTypes = model.getRenderTypes(state, random, data);
        for (RenderLayer layer : CHUNK_LAYERS) {
            if (!renderTypes.contains(layer)) continue;
            String name = classifyBlockLayer(state, layer);
            emitVanillaBlock(world, state, model, name, lx, ly, lz, data, layer);
        }
    }

    /**
     * Emits fluid quads for the current block. Called from the
     * renderFluid redirect.
     */
    public void emitFluid(BlockPos pos, BlockRenderView world, FluidState fluidState) {
        fluidCount++;
        int lx = ChunkSectionPos.getLocalCoord(pos.getX());
        int ly = ChunkSectionPos.getLocalCoord(pos.getY());
        int lz = ChunkSectionPos.getLocalCoord(pos.getZ());
        cacheLocalFluidState(localIndex(lx, ly, lz), fluidState);
        emitFluidQuads(world, fluidState, lx, ly, lz);
    }

    /**
     * Finalises the section, producing a {@link SectionBlockData} with
     * all accumulated mesh/light/mask data. Deactivates the emitter.
     */
    public SectionBlockData finishSection(ChunkSectionPos sectionPos) {
        writer.flushBlock(texManager);
        try (ProfileScope finishScope = ProfileScope.begin("finishSection")) {
            finishScope.blockCount(blockCount).fluidCount(fluidCount);
            SectionMeshData meshData = writer.buildMeshData();
            SectionBlockData result = new SectionBlockData(
                    sectionPos, meshData, List.copyOf(lights), topMask, bottomMask,
                    eastMask, westMask, southMask, northMask,
                    Arrays.copyOf(opacity, opacity.length), frame);
            lights.clear();
            return result;
        } finally {
            active = false;
        }
    }

    private void emitVanillaBlock(BlockRenderView world, BlockState state,
                                   BakedModel model, String layerName,
                                   float lx, float ly, float lz, ModelData data, RenderLayer layer) {

        for (Direction dir : ALL_DIRECTIONS) {
            if (dir != null && !shouldDrawBlockSide(world, state, dir)) {
                continue;
            }

            random.setSeed(modelRandomSeed);
            List<BakedQuad> bakedQuads = model.getQuads(state, dir, random, data, layer);

            for (BakedQuad bq : bakedQuads) {
                stageBakedQuad(bq, layerName, state, world, lx, ly, lz);
            }
        }

        writer.flushBlock(texManager);
    }

    private void stageBakedQuad(BakedQuad bq, String layerName,
                                 BlockState state, BlockRenderView world,
                                 float lx, float ly, float lz) {
        int[] vd = bq.getVertexData();

        for (int v = 0; v < 4; v++) {
            int off = v * 8;
            tempPositions[v * 3]     = Float.intBitsToFloat(vd[off])     + lx;
            tempPositions[v * 3 + 1] = Float.intBitsToFloat(vd[off + 1]) + ly;
            tempPositions[v * 3 + 2] = Float.intBitsToFloat(vd[off + 2]) + lz;
            tempUvs[v * 2]     = Float.intBitsToFloat(vd[off + 4]);
            tempUvs[v * 2 + 1] = Float.intBitsToFloat(vd[off + 5]);
        }

        int packedNormal = vd[7];
        float nx = (byte) (packedNormal & 0xFF) / 127f;
        float ny = (byte) ((packedNormal >> 8) & 0xFF) / 127f;
        float nz = (byte) ((packedNormal >> 16) & 0xFF) / 127f;
        if (nx * nx + ny * ny + nz * nz < 0.01f) {
            float e1x = tempPositions[3] - tempPositions[0], e1y = tempPositions[4] - tempPositions[1], e1z = tempPositions[5] - tempPositions[2];
            float e2x = tempPositions[6] - tempPositions[0], e2y = tempPositions[7] - tempPositions[1], e2z = tempPositions[8] - tempPositions[2];
            nx = e1y * e2z - e1z * e2y;
            ny = e1z * e2x - e1x * e2z;
            nz = e1x * e2y - e1y * e2x;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0.001f) { nx /= len; ny /= len; nz /= len; }
        }

        int color = 0xFFFFFFFF;
        if (bq.getColorIndex() >= 0 && blockColors != null) {
            int tint = getBlockTint(state, world, bq.getColorIndex());
            int r = (tint >> 16) & 0xFF, g = (tint >> 8) & 0xFF, b = tint & 0xFF;
            color = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        Sprite sprite = bq.getSprite();
        Identifier spriteId = sprite != null ? sprite.getContents().getId() : null;
        if (isWaterSprite(spriteId) || (texManager != null && texManager.fluidBucket(spriteId) != null)) {
            emitBlockWaterQuad(nx, ny, nz, color, layerName, null, spriteId);
            return;
        }
        float su0 = sprite != null ? sprite.getMinU() : 0;
        float sv0 = sprite != null ? sprite.getMinV() : 0;
        float su1 = sprite != null ? sprite.getMaxU() : 1;
        float sv1 = sprite != null ? sprite.getMaxV() : 1;

        writer.stageQuad(tempPositions, tempUvs, nx, ny, nz, color,
                spriteId, su0, sv0, su1, sv1, layerName);
        for (int vertex = 0; vertex < 4; vertex++) {
            tempColors[vertex] = QuadColors.tintAbgr(vd[vertex * 8 + 3], color);
        }
        writer.stageVertexColors(tempColors);
    }

    private static final Identifier WATER_STILL_SPRITE = Identifier.ofVanilla("block/water_still");
    private static final Identifier WATER_OVERLAY_SPRITE = Identifier.ofVanilla("block/water_overlay");
    private static final Identifier WATER_FLOW_SPRITE = Identifier.ofVanilla("block/water_flow");

    /** A block-model face painted with the water texture: the cauldron's water level is the vanilla case. */
    static boolean isWaterSprite(Identifier spriteId) {
        return WATER_STILL_SPRITE.equals(spriteId) || WATER_FLOW_SPRITE.equals(spriteId)
                || WATER_OVERLAY_SPRITE.equals(spriteId);
    }

    /**
     * Emits the staged quad in {@code tempPositions} as RTX water instead of an
     * atlas-textured face, so a cauldron's water matches the lake outside. The
     * water material tiles in world space, so the UVs come from the vertex
     * positions along the face's dominant axes, exactly as the fluid path maps
     * its top and side faces.
     */
    private void emitBlockWaterQuad(float nx, float ny, float nz, int color, String layerName, int[] vertexColors, Identifier spriteId) {
        float tileSize = BlockTextureManager.getWaterTileSize();
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        for (int v = 0; v < 4; v++) {
            float wx = TiledTextureCoordinates.tile(baseX, tempPositions[v * 3], tileSize);
            float wy = TiledTextureCoordinates.tile(baseY, tempPositions[v * 3 + 1], tileSize);
            float wz = TiledTextureCoordinates.tile(baseZ, tempPositions[v * 3 + 2], tileSize);
            float u, w;
            if (ay >= ax && ay >= az) {
                u = wx; w = wz;
            } else if (ax >= az) {
                u = wz; w = wy;
            } else {
                u = wx; w = wy;
            }
            tempUvs[v * 2] = u;
            tempUvs[v * 2 + 1] = w;
        }
        writer.writeFluidQuad(tempPositions, tempUvs, nx, ny, nz, color,
                spriteId, 0, 0, 1, 1, layerName, texManager, vertexColors);
    }

    // --- Fluid geometry ---

    private void emitFluidQuads(BlockRenderView world, FluidState fluidState,
                                 int lx, int ly, int lz) {
        IClientFluidTypeExtensions handler = IClientFluidTypeExtensions.of(fluidState);
        if (handler == null) return;

        fluidPos.set(baseX + lx, baseY + ly, baseZ + lz);

        fluidSprites[0] = FluidSpriteCache.getSprite(handler.getStillTexture(fluidState, world, fluidPos));
        fluidSprites[1] = FluidSpriteCache.getSprite(handler.getFlowingTexture(fluidState, world, fluidPos));
        Identifier overlay = handler.getOverlayTexture(fluidState, world, fluidPos);
        fluidSprites[2] = overlay == null ? null : FluidSpriteCache.getSprite(overlay);
        if (fluidSprites == null || fluidSprites.length < 2) return;

        boolean isLava = fluidState.isIn(FluidTags.LAVA);
        boolean isWater = !isLava || (texManager != null && texManager.fluidBucket(fluidSprites[0].getContents().getId()) != null);
        String layer = isLava ? "solid" : "translucent";
        int fluidColor = isWater
                ? getCachedWaterColor(handler, world, fluidState, lx, ly, lz)
                : handler.getTintColor(fluidState, world, fluidPos);
        int r = (fluidColor >> 16) & 0xFF, g = (fluidColor >> 8) & 0xFF, b = fluidColor & 0xFF;
        int color = (fluidColor & 0xFF000000) | (r << 16) | (g << 8) | b;

        float h00 = getFluidHeight(world, fluidState, baseX + lx, baseY + ly, baseZ + lz);
        float h10 = getFluidHeight(world, fluidState, baseX + lx + 1, baseY + ly, baseZ + lz);
        float h01 = getFluidHeight(world, fluidState, baseX + lx, baseY + ly, baseZ + lz + 1);
        float h11 = getFluidHeight(world, fluidState, baseX + lx + 1, baseY + ly, baseZ + lz + 1);

        float tileSize = BlockTextureManager.getWaterTileSize();
        int wx = baseX + lx, wy = baseY + ly, wz = baseZ + lz;

        Sprite stillSprite = fluidSprites[0];
        Sprite flowSprite = fluidSprites[1];

        Identifier stillId = stillSprite.getContents().getId();
        float su0 = stillSprite.getMinU(), sv0 = stillSprite.getMinV();
        float su1 = stillSprite.getMaxU(), sv1 = stillSprite.getMaxV();

        Identifier flowId = flowSprite.getContents().getId();
        float fu0 = flowSprite.getMinU(), fv0 = flowSprite.getMinV();
        float fu1 = flowSprite.getMaxU(), fv1 = flowSprite.getMaxV();

        fluidCheckPos.set(fluidPos.getX(), fluidPos.getY() + 1, fluidPos.getZ());
        FluidState aboveFluid = getCachedFluidState(world,
                fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());
        boolean submerged = aboveFluid.getFluid().matchesType(fluidState.getFluid());
        boolean waterTopCovered = false;
        if (isWater && !submerged && fluidState.isStill()) {
            BlockState aboveState = getCachedBlockState(world,
                    fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());
            float topCoverageHeight = Math.min(Math.min(h00, h10), Math.min(h01, h11));
            if (isFluidTopCoveredByCeiling(world, topCoverageHeight, fluidCheckPos, aboveState)) {
                waterTopCovered = classifyWaterTopExposure(world, fluidState, wx, wy, wz)
                        == FluidTopOcclusion.Exposure.HIDDEN;
            }
        }

        // Top face
        if (!submerged && !waterTopCovered) {
            setPos4(tempPositions, lx, ly + h00, lz, lx + 1, ly + h10, lz,
                    lx + 1, ly + h11, lz + 1, lx, ly + h01, lz + 1);
            if (isWater) {
                float u0 = TiledTextureCoordinates.tile(baseX, lx, tileSize), v0 = TiledTextureCoordinates.tile(baseZ, lz, tileSize);
                float u1 = TiledTextureCoordinates.tile(baseX, lx + 1, tileSize), v1 = TiledTextureCoordinates.tile(baseZ, lz + 1, tileSize);
                setUv4(tempUvs, u0, v0, u1, v0, u1, v1, u0, v1);
                writer.writeFluidQuad(tempPositions, tempUvs, 0, 1, 0, color,
                        stillId, 0, 0, 1, 1, layer, texManager);
                setPos4(tempPositions, lx, ly + h01, lz + 1, lx + 1, ly + h11, lz + 1,
                        lx + 1, ly + h10, lz, lx, ly + h00, lz);
                writer.writeFluidQuad(tempPositions, tempUvs, 0, -1, 0, color,
                        stillId, 0, 0, 1, 1, layer, texManager);
            } else {
                // Vanilla FluidRenderer: a still surface shows the still sprite in
                // full; a moving one shows the centre quarter of the flowing sprite
                // (two tiles across, so a quarter is one tile) rotated to the flow.
                Vec3d flow = fluidState.getVelocity(world, fluidPos);
                if (flow.x == 0.0 && flow.z == 0.0) {
                    setUv4(tempUvs, su0, sv0, su1, sv0, su1, sv1, su0, sv1);
                    writer.writeFluidQuad(tempPositions, tempUvs, 0, 1, 0, color,
                            stillId, su0, sv0, su1, sv1, layer, texManager);
                    setPos4(tempPositions, lx, ly + h01, lz + 1, lx + 1, ly + h11, lz + 1,
                            lx + 1, ly + h10, lz, lx, ly + h00, lz);
                    writer.writeFluidQuad(tempPositions, tempUvs, 0, -1, 0, color,
                            stillId, su0, sv0, su1, sv1, layer, texManager);
                } else {
                    float angle = (float) MathHelper.atan2(flow.z, flow.x) - (float) (Math.PI / 2.0);
                    float sin = MathHelper.sin(angle) * 0.25f;
                    float cos = MathHelper.cos(angle) * 0.25f;
                    // Vanilla's corners in its order (0,0) (0,1) (1,1) (1,0), reordered to
                    // this emitter's (0,0) (1,0) (1,1) (0,1).
                    float c00u = flowSprite.getFrameU(0.5f - cos - sin), c00v = flowSprite.getFrameV(0.5f - cos + sin);
                    float c01u = flowSprite.getFrameU(0.5f - cos + sin), c01v = flowSprite.getFrameV(0.5f + cos + sin);
                    float c11u = flowSprite.getFrameU(0.5f + cos + sin), c11v = flowSprite.getFrameV(0.5f + cos - sin);
                    float c10u = flowSprite.getFrameU(0.5f + cos - sin), c10v = flowSprite.getFrameV(0.5f - cos - sin);
                    setUv4(tempUvs, c00u, c00v, c10u, c10v, c11u, c11v, c01u, c01v);
                    writer.writeFluidQuad(tempPositions, tempUvs, 0, 1, 0, color,
                            flowId, fu0, fv0, fu1, fv1, layer, texManager);
                    setPos4(tempPositions, lx, ly + h01, lz + 1, lx + 1, ly + h11, lz + 1,
                            lx + 1, ly + h10, lz, lx, ly + h00, lz);
                    setUv4(tempUvs, c01u, c01v, c11u, c11v, c10u, c10v, c00u, c00v);
                    writer.writeFluidQuad(tempPositions, tempUvs, 0, -1, 0, color,
                            flowId, fu0, fv0, fu1, fv1, layer, texManager);
                }
            }
        }

        // Bottom face
        fluidCheckPos.set(fluidPos.getX(), fluidPos.getY() - 1, fluidPos.getZ());
        FluidState belowFluid = getCachedFluidState(world,
                fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());
        BlockState belowState = getCachedBlockState(world,
                fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());
        if (!belowFluid.getFluid().matchesType(fluidState.getFluid()) && !belowState.isOpaqueFullCube(world, fluidCheckPos)) {
            setPos4(tempPositions, lx, ly, lz + 1, lx + 1, ly, lz + 1,
                    lx + 1, ly, lz, lx, ly, lz);
            if (isWater) {
                float u0 = TiledTextureCoordinates.tile(baseX, lx, tileSize), v0 = TiledTextureCoordinates.tile(baseZ, lz, tileSize);
                float u1 = TiledTextureCoordinates.tile(baseX, lx + 1, tileSize), v1 = TiledTextureCoordinates.tile(baseZ, lz + 1, tileSize);
                setUv4(tempUvs, u0, v1, u1, v1, u1, v0, u0, v0);
                writer.writeFluidQuad(tempPositions, tempUvs, 0, -1, 0, color,
                        stillId, 0, 0, 1, 1, layer, texManager);
            } else {
                setUv4(tempUvs, su0, sv0, su1, sv0, su1, sv1, su0, sv1);
                writer.writeFluidQuad(tempPositions, tempUvs, 0, -1, 0, color,
                        stillId, su0, sv0, su1, sv1, layer, texManager);
            }
        }

        // Side faces
        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            fluidCheckPos.set(
                fluidPos.getX() + dir.getOffsetX(),
                fluidPos.getY(),
                fluidPos.getZ() + dir.getOffsetZ());
            FluidState sideFluid = getCachedFluidState(world,
                    fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());
            BlockState sideState = getCachedBlockState(world,
                    fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());

            if (sideFluid.getFluid().matchesType(fluidState.getFluid())) continue;
            if (sideState.isOpaqueFullCube(world, fluidCheckPos)) continue;

            float sideH0, sideH1;
            switch (dir) {
                case NORTH -> {
                    sideH0 = h10; sideH1 = h00;
                    setPos4(tempPositions, lx + 1, ly, lz, lx, ly, lz,
                            lx, ly + sideH1, lz, lx + 1, ly + sideH0, lz);
                }
                case SOUTH -> {
                    sideH0 = h01; sideH1 = h11;
                    setPos4(tempPositions, lx, ly, lz + 1, lx + 1, ly, lz + 1,
                            lx + 1, ly + sideH1, lz + 1, lx, ly + sideH0, lz + 1);
                }
                case EAST -> {
                    sideH0 = h11; sideH1 = h10;
                    setPos4(tempPositions, lx + 1, ly, lz + 1, lx + 1, ly, lz,
                            lx + 1, ly + sideH1, lz, lx + 1, ly + sideH0, lz + 1);
                }
                case WEST -> {
                    sideH0 = h00; sideH1 = h01;
                    setPos4(tempPositions, lx, ly, lz, lx, ly, lz + 1,
                            lx, ly + sideH1, lz + 1, lx, ly + sideH0, lz);
                }
                default -> { continue; }
            }

            float fnx = dir.getOffsetX(), fny = 0, fnz = dir.getOffsetZ();
            if (isWater) {
                boolean xAxis = (dir == Direction.EAST || dir == Direction.WEST);
                float sU0, sU1, sV0, sV1;
                if (xAxis) {
                    sU0 = TiledTextureCoordinates.tile(baseZ, lz, tileSize); sU1 = TiledTextureCoordinates.tile(baseZ, lz + 1, tileSize);
                } else {
                    sU0 = TiledTextureCoordinates.tile(baseX, lx, tileSize); sU1 = TiledTextureCoordinates.tile(baseX, lx + 1, tileSize);
                }
                sV0 = TiledTextureCoordinates.tile(baseY, ly, tileSize); sV1 = TiledTextureCoordinates.tile(baseY, ly + 1, tileSize);
                setUv4(tempUvs, sU0, sV1, sU1, sV1, sU1, sV0, sU0, sV0);
                writer.writeFluidQuad(tempPositions, tempUvs, fnx, fny, fnz, color,
                        stillId, 0, 0, 1, 1, layer, texManager);
            } else {
                // Vanilla FluidRenderer side mapping: one tile is half the flowing
                // sprite, so U spans its left half and V runs from the fluid surface
                // ((1 - height) / 2) down to the half-way line. The full sprite over a
                // one-block face showed the texture at twice the density.
                float sideU0 = flowSprite.getFrameU(0.0f);
                float sideU1 = flowSprite.getFrameU(0.5f);
                float sideVBottom = flowSprite.getFrameV(0.5f);
                float sideVTop0 = flowSprite.getFrameV((1.0f - sideH0) * 0.5f);
                float sideVTop1 = flowSprite.getFrameV((1.0f - sideH1) * 0.5f);
                setUv4(tempUvs, sideU0, sideVBottom, sideU1, sideVBottom, sideU1, sideVTop1, sideU0, sideVTop0);
                writer.writeFluidQuad(tempPositions, tempUvs, fnx, fny, fnz, color,
                        flowId, fu0, fv0, fu1, fv1, layer, texManager);
            }
        }
    }

    static boolean isFluidTopCoveredByCeiling(BlockView world, float fluidHeight,
                                              BlockPos neighborPos, BlockState neighborState) {
        if (fluidHeight + WATER_HEIGHT_EPSILON < WATER_CEILING_CULL_MIN_HEIGHT
                || !neighborState.isOpaque()) {
            return false;
        }
        if (neighborState.isOpaqueFullCube(world, neighborPos)) {
            return true;
        }

        VoxelShape neighborShape = neighborState.getCullingShape(world, neighborPos);
        return isFluidSideCovered(Direction.UP, 1.0f, true, neighborShape);
    }

    static boolean isFluidTopCoveredByCeiling(float fluidHeight,
                                              boolean neighborOpaque, VoxelShape neighborShape) {
        if (fluidHeight + WATER_HEIGHT_EPSILON < WATER_CEILING_CULL_MIN_HEIGHT) return false;

        return isFluidSideCovered(Direction.UP, 1.0f, neighborOpaque, neighborShape);
    }

    static boolean isFluidSideCovered(Direction direction, float fluidHeight,
                                      boolean neighborOpaque, VoxelShape neighborShape) {
        if (!neighborOpaque) return false;

        VoxelShape fluidShape = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, fluidHeight, 1.0);
        return VoxelShapes.isSideCovered(fluidShape, neighborShape, direction);
    }

    private FluidTopOcclusion.Exposure classifyWaterTopExposure(
            BlockRenderView world, FluidState referenceFluid, int worldX, int worldY, int worldZ) {
        if (getCachedBlockState(world, worldX, worldY, worldZ).isOpaque()) {
            return FluidTopOcclusion.Exposure.EXPOSED;
        }

        if (waterTopCellCacheFluid == null
                || !waterTopCellCacheFluid.getFluid().matchesType(referenceFluid.getFluid())) {
            Arrays.fill(waterTopCellCache, FluidTopOcclusion.UNKNOWN);
            waterTopCellCacheFluid = referenceFluid;
        }

        int originCacheIndex = waterTopCellCacheIndex(worldX, worldY, worldZ);
        if (originCacheIndex >= 0) {
            // The caller already established that this still-fluid origin has
            // a covering ceiling, so do not repeat its shape comparison.
            waterTopCellCache[originCacheIndex] = FluidTopOcclusion.CONNECTED;
        }

        waterTopCullWorld = world;
        waterTopCullReferenceFluid = referenceFluid;
        waterTopCullOriginX = worldX;
        waterTopCullOriginY = worldY;
        waterTopCullOriginZ = worldZ;
        try {
            return FluidTopOcclusion.classifyConnectedOrigin(
                    WATER_TOP_CULL_RADIUS, waterTopCullStack, waterTopCellClassifier);
        } finally {
            waterTopCullWorld = null;
            waterTopCullReferenceFluid = null;
        }
    }

    private byte classifyWaterTopCellAtOffset(int xOffset, int zOffset) {
        int worldX = waterTopCullOriginX + xOffset;
        int worldY = waterTopCullOriginY;
        int worldZ = waterTopCullOriginZ + zOffset;
        int cacheIndex = waterTopCellCacheIndex(worldX, worldY, worldZ);
        if (cacheIndex < 0) {
            return classifyWaterTopCell(
                    waterTopCullWorld, waterTopCullReferenceFluid, worldX, worldY, worldZ);
        }

        byte classification = waterTopCellCache[cacheIndex];
        if (classification == FluidTopOcclusion.UNKNOWN) {
            classification = classifyWaterTopCell(
                    waterTopCullWorld, waterTopCullReferenceFluid, worldX, worldY, worldZ);
            waterTopCellCache[cacheIndex] = classification;
        }
        return classification;
    }

    private int waterTopCellCacheIndex(int worldX, int worldY, int worldZ) {
        int x = worldX - baseX + WATER_TOP_CULL_RADIUS;
        int y = worldY - baseY;
        int z = worldZ - baseZ + WATER_TOP_CULL_RADIUS;
        if ((x | y | z) < 0
                || x >= WATER_TOP_CACHE_WIDTH
                || y >= SECTION_SIZE
                || z >= WATER_TOP_CACHE_WIDTH) {
            return -1;
        }
        return (y * WATER_TOP_CACHE_WIDTH + z) * WATER_TOP_CACHE_WIDTH + x;
    }

    private byte classifyWaterTopCell(BlockRenderView world, FluidState referenceFluid,
                                      int worldX, int worldY, int worldZ) {
        BlockState state = getCachedBlockState(world, worldX, worldY, worldZ);
        if (state.isOpaque()) {
            return FluidTopOcclusion.BLOCKED;
        }

        FluidState fluid = getCachedFluidState(world, worldX, worldY, worldZ);
        if (!fluid.getFluid().matchesType(referenceFluid.getFluid()) || !fluid.isStill()) {
            return FluidTopOcclusion.EXPOSED;
        }

        FluidState aboveFluid = getCachedFluidState(world, worldX, worldY + 1, worldZ);
        if (aboveFluid.getFluid().matchesType(referenceFluid.getFluid())) {
            return FluidTopOcclusion.BLOCKED;
        }

        fluidCheckPos.set(worldX, worldY + 1, worldZ);
        BlockState aboveState = getCachedBlockState(world, worldX, worldY + 1, worldZ);
        return isFluidTopCoveredByCeiling(world, fluid.getHeight(), fluidCheckPos, aboveState)
                ? FluidTopOcclusion.CONNECTED
                : FluidTopOcclusion.EXPOSED;
    }

    private float getFluidHeight(BlockRenderView world, FluidState referenceFluid,
                                  int worldX, int worldY, int worldZ) {
        float totalHeight = 0;
        int count = 0;

        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                fluidCheckPos.set(worldX + dx, worldY + 1, worldZ + dz);
                FluidState aboveFluid = getCachedFluidState(world,
                        fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());
                if (aboveFluid.getFluid().matchesType(referenceFluid.getFluid())) {
                    return 1.0f;
                }

                fluidCheckPos.set(worldX + dx, worldY, worldZ + dz);
                FluidState fs = getCachedFluidState(world,
                        fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ());
                if (fs.getFluid().matchesType(referenceFluid.getFluid())) {
                    float h = fs.getHeight();
                    totalHeight += h;
                    count++;
                } else if (!getCachedBlockState(world,
                        fluidCheckPos.getX(), fluidCheckPos.getY(), fluidCheckPos.getZ()).isSolid()) {
                    count++;
                }
            }
        }

        return count > 0 ? totalHeight / count : 0.5f;
    }

    private int getCachedWaterColor(IClientFluidTypeExtensions handler, BlockRenderView world,
                                    FluidState fluidState, int lx, int ly, int lz) {
        int cell = ((ly >> 3) << 4) | ((lz >> 2) << 2) | (lx >> 2);
        int bit = 1 << cell;
        if ((waterColorKnownMask & bit) == 0) {
            waterColorCache[cell] = handler.getTintColor(fluidState, world, fluidPos);
            waterColorKnownMask |= bit;
        }
        return waterColorCache[cell];
    }

    // --- Helpers ---

    private boolean shouldDrawBlockSide(BlockRenderView world, BlockState state, Direction side) {
        int neighborX = mutablePos.getX() + side.getOffsetX();
        int neighborY = mutablePos.getY() + side.getOffsetY();
        int neighborZ = mutablePos.getZ() + side.getOffsetZ();
        BlockState neighbor = getCachedBlockState(world, neighborX, neighborY, neighborZ);
        long key = ((long) Block.getRawIdFromState(state) << 35)
                | ((long) Block.getRawIdFromState(neighbor) << 3)
                | side.ordinal();
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - FACE_CULL_CACHE_BITS));
        if (faceCullKeys[slot] == key && faceCullResults[slot] != FACE_CULL_UNKNOWN) {
            return faceCullResults[slot] == FACE_CULL_DRAWN;
        }

        neighborPos.set(neighborX, neighborY, neighborZ);
        boolean drawn = Block.shouldDrawSide(state, world, mutablePos, side, neighborPos);
        faceCullKeys[slot] = key;
        faceCullResults[slot] = drawn ? FACE_CULL_DRAWN : FACE_CULL_HIDDEN;
        return drawn;
    }

    private BlockState getCachedBlockState(BlockView world, int worldX, int worldY, int worldZ) {
        int lx = worldX - baseX;
        int ly = worldY - baseY;
        int lz = worldZ - baseZ;
        if ((lx | ly | lz) < 0 || lx >= SECTION_SIZE || ly >= SECTION_SIZE || lz >= SECTION_SIZE) {
            neighborPos.set(worldX, worldY, worldZ);
            return world.getBlockState(neighborPos);
        }

        int idx = localIndex(lx, ly, lz);
        int word = idx >> 6;
        long bit = 1L << (idx & 63);
        if ((blockStateKnown[word] & bit) != 0) {
            return blockStateCache[idx];
        }

        neighborPos.set(worldX, worldY, worldZ);
        BlockState state = world.getBlockState(neighborPos);
        cacheLocalBlockState(idx, state);
        return state;
    }

    private FluidState getCachedFluidState(BlockView world, int worldX, int worldY, int worldZ) {
        int lx = worldX - baseX;
        int ly = worldY - baseY;
        int lz = worldZ - baseZ;
        if ((lx | ly | lz) < 0 || lx >= SECTION_SIZE || ly >= SECTION_SIZE || lz >= SECTION_SIZE) {
            neighborPos.set(worldX, worldY, worldZ);
            return world.getFluidState(neighborPos);
        }

        int idx = localIndex(lx, ly, lz);
        int word = idx >> 6;
        long bit = 1L << (idx & 63);
        if ((fluidStateKnown[word] & bit) != 0) {
            return fluidStateCache[idx];
        }

        FluidState fluidState = getCachedBlockState(world, worldX, worldY, worldZ).getFluidState();
        cacheLocalFluidState(idx, fluidState);
        return fluidState;
    }

    private void cacheLocalBlockState(int idx, BlockState state) {
        blockStateCache[idx] = state;
        blockStateKnown[idx >> 6] |= 1L << (idx & 63);
    }

    private void cacheLocalFluidState(int idx, FluidState fluidState) {
        fluidStateCache[idx] = fluidState;
        fluidStateKnown[idx >> 6] |= 1L << (idx & 63);
    }

    private static int localIndex(int lx, int ly, int lz) {
        return (ly << 8) | (lz << 4) | lx;
    }

    private int getBlockTint(BlockState state, BlockRenderView world, int colorIndex) {
        if (colorIndex < 0 || colorIndex >= TINT_CACHE_SIZE) {
            return blockColors.getColor(state, world, mutablePos, colorIndex);
        }

        int bit = 1 << colorIndex;
        if ((tintValidMask & bit) == 0) {
            tintCache[colorIndex] = blockColors.getColor(state, world, mutablePos, colorIndex);
            tintValidMask |= bit;
        }
        return tintCache[colorIndex];
    }

    /**
     * The terrain layer a block's quads are bucketed under, by
     * {@link BlockTextureManager#layerType} name. A held block item asks the same
     * question, so it shades as its placed counterpart does.
     */
    public static String classifyBlockLayer(BlockState state) {
        return classifyBlockLayer(state, RenderLayers.getBlockLayer(state));
    }

    private static String classifyBlockLayer(BlockState state, RenderLayer layer) {
        // Vanilla draws clear glass as a cutout: the texture is alpha 0 between
        // the frame pixels and the GL path simply discards those. Remix can
        // refract through it instead, so clear glass takes its own layer and a
        // translucent material with the frame as a diffuse layer.
        // Stained glass is already translucent and keeps vanilla's layer.
        if (com.nvidia.rtxremix.minecraft.texture.BlockMaterialCatalog.glass(state)) return "glass";
        if (layer == RenderLayer.getSolid()) return "solid";
        if (layer == RenderLayer.getCutoutMipped()) return "cutout_mipped";
        if (layer == RenderLayer.getCutout()) return "cutout";
        if (layer == RenderLayer.getTranslucent()) return "translucent";
        if (layer == RenderLayer.getTripwire()) return "cutout";
        return "solid";
    }

    private static void setPos4(float[] out,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3) {
        out[0] = x0; out[1] = y0; out[2] = z0;
        out[3] = x1; out[4] = y1; out[5] = z1;
        out[6] = x2; out[7] = y2; out[8] = z2;
        out[9] = x3; out[10] = y3; out[11] = z3;
    }

    private static void setUv4(float[] out,
                                float u0, float v0, float u1, float v1,
                                float u2, float v2, float u3, float v3) {
        out[0] = u0; out[1] = v0; out[2] = u1; out[3] = v1;
        out[4] = u2; out[5] = v2; out[6] = u3; out[7] = v3;
    }
}
