# NeoForge mod compatibility

This checkout targets Minecraft 1.21.1 and NeoForge 21.1.242. The local
`reference/` directory supplies the exact optional renderer API versions used
for compilation and binary transformation tests. These dependencies are
compile-only; they are not embedded in the RTX Remix JAR.

## Capture paths

- Sodium 0.8.13: capture the final block, fluid and mesh-appender quads before
  vertex compression and translucent sorting. ModelData, connected models,
  multipart geometry, tint and per-vertex colours come from the installed
  model renderer. Captures publish only after Sodium accepts a build result;
  cancelled and superseded results release their buffers. Empty sections
  clear previous captures even when Sodium skips its meshing task. Full terrain
  recaptures exclude built air sections and prioritise nearby sections. Additional
  off-camera builds yield to foreground work and stop at capture-queue high-water
  marks (64 sections or 16 MiB), with at most eight extra submissions per update.
- Distant Horizons 3.3.3: copy final 16-byte LOD vertices before DH releases its
  pooled buffers, then publish retained colour/alpha meshes into Remix. The
  active quadtree supplies all directions for traced shadows and reflections.
  Near-terrain clipping cuts crossing quads, not just their centres. Uploads
  prioritise nearby sections and default to two pages of 1024 quads per frame;
  `rtxremix.dh.pagesPerFrame` changes that budget. DH owns LOD generation, database,
  radius and detail selection. The adapter uses vertex colours, not DH 3.3.3's
  optional detail texture tiles or its GLSL fog/SSAO/shader-pack effects.
- Slopium 0.1.0-alpha.18: camera/wall culling cannot suppress Create models during
  a Remix world render; distance helpers do not truncate an accepted Remix model
  capture. Client tick budgets and explicit rendering-disable options retain their
  own behaviour. No Slopium source or saved config is modified.
- Bits 'n' Bobs headlight: register client headlamps and decode all nine placements,
  dye colours, four ComputerCraft addressing bits and facing from the actual block
  entity. Directional disk lights follow Sable frames and Create contraptions.
  Switched-off lamps stop drawing without native resource churn. On-state and
  sixteen dyed sprites receive UV-remapped copies of the supplied emission mask;
  off-state sprites remain unmasked. The ordinary six-face fallback is excluded
  for this block so it does not light through its housing.
- Create 6.0.11 and Flywheel 1.0.6: while RTX is enabled, Flywheel reports its
  GPU backend inactive. Create and its addons use their ordinary block-entity
  and contraption renderers, which feed the existing Remix entity capture.
  This changes the rendering backend, not contraption assembly or simulation.
  Vertex and part buffers grow to fit the model. Long quad runs are split
  into 16,384-vertex mesh parts, without dropping geometry. Catnip's shade-separated
  buffers retain template vertices and carry animation on the instance transform;
  rotations reuse native meshes instead of baking a new mesh every frame. Capture
  skips raster diffuse shading and lightmap reads, preserving texture, tint, alpha
  and sprite shifts for the path tracer.
- Copycats 3.0.9: model data owns material-specific face occlusion. The vanilla
  capture path does not repeat whole-block neighbour hiding for Copycat blocks,
  which otherwise reads a multipart material without its part context. Other
  block-entity and dynamic face checks are evaluated per position, not cached
  solely by the two block states.
- Sable 2.0.5: retain the sublevel object as the capture's coordinate-system
  identity. Mesh roots and analytic lights follow its interpolated render
  pose, including scale and rotation point. Large storage coordinates are
  cancelled in double precision before conversion to native floats.
  Removed sublevels and emptied sections retire their geometry. Single-block
  sublevels use the same section capture path, including after an RTX toggle.
- Sodium Dynamic Lights 1.0.10: read its registered external emitters under
  the provider's read lock. Create Dynamic Lights' contraption and Sable
  sources become Remix lights; emitters already represented by captured Sable
  terrain rules are excluded to avoid lighting them twice. The existing Remix light manager keeps
  ownership of entity/held-item and placed block-entity lights.
- Entity Culling 1.10.5: raster visibility flags do not suppress captured
  entities and block entities while RTX is enabled. Off-camera geometry can
  contribute to traced shadows and reflections.
- FrameGen 1.4: defer history capture until the traced image is composited;
  seed matching traced depth into the main framebuffer first. A captured
  first-person model receives the provider's near-camera lock treatment.
- ImmediatelyFast 1.6.14: preserve its buffer-upload redirects and use the
  actual glyph atlas dimensions. These hooks were checked during the port.

Model-dependent multiblock connections are evaluated by their own mods. There
is no list of substitute block models: a model's neighbour-dependent output
is captured when Minecraft/Sodium rebuilds it. Opaque, cutout, translucent,
fluid and decal categories retain their separate material paths.

## Verification boundary

`gradlew.bat test` checks large captures, independently coloured vertices,
frame transforms, scaled/directional lights, packaged Minecraft access and
production mixin application to the actual optional renderer binaries. The
mixin verifier runs in separate JVMs with reference mods present and absent;
it does not launch Minecraft or initialise the native renderer.

These checks do **not** prove every registered block state, addon animation,
multiblock arrangement or interaction in this modpack. The reference inventory
below records the integration route, not an in-game certification. In
particular, arbitrary custom OpenGL/shader passes need dedicated Remix adapters.
Distant Horizons uses a dedicated colour-geometry adapter with the limits described above. Custom particle renderers and
shader-specific effects also need separate visual validation. A universal
compatibility claim would therefore be inaccurate.

For a visual check, use the official launcher and the packaged JAR in a test
installation. Exercise connected copycats/Struts, active Create machines,
assembled and disassembled contraptions, moving/rotating Sable sublevels,
light sources, block removal, resource reload and RTX off/on transitions.
Prism is not required for installing or playing this packaged mod.

## Reference inventory

`reference/` contains 75 JAR files. It includes two versions of the same
`createdynlight` mod: `create-dyn-light-2.3.1-sodium-sable.jar` and
`create-dyn-light-forge-1.21.1-2.0.1.jar`. Install **one** version at a time;
NeoForge cannot load both copies of one mod ID. Both expose their Forge-side
sources through Sodium Dynamic Lights' `DynamicLightSource` API.

| Reference JAR | Integration route |
| --- | --- |
| `AI-Improvements-1.21-0.5.3.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `CreateDragonsPlus-1.11.9.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar` | Dedicated colour/alpha LOD adapter for 3.3.3; game verification pending |
| `FarmersDelight-1.21.1-1.3.4.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `ImmediatelyFast-NeoForge-1.6.14+1.21.1.jar` | Compatible upload hooks and dynamic font atlas |
| `Measurements-neoforge-1.21.1-3.0.3.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `aerocopycats-1.1.1.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `appleskin-neoforge-mc1.21-3.0.9.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `azimuth-1.4.9.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `better-snowy-biome-v2.5.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `betterchunkloading-1.21-5.4.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `bigcontraptions-neoforge-1.0.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `bits_n_bobs-2.3.5.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `climbable_ropes-2.1.3.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `cmparallelpipes-neoforge-2.0.2.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `copycat_roller-2.0.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `copycats-3.0.9+mc.1.21.1-neoforge.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `crawl-0.14.9.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `create-1.21.1-6.0.11.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `create-aeronautics-bundled-1.21.1-1.3.2.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `create-dyn-light-2.3.1-sodium-sable.jar` | Registered external dynamic light sources; select one version |
| `create-dyn-light-forge-1.21.1-2.0.1.jar` | Registered external dynamic light sources; select one version |
| `create-enchantment-industry-2.4.2.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `create_bb-1.0.8-1.21.1.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `create_power_loader-2.0.5-mc1.21.1.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `createaddition-1.6.0.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `createbigcannons-5.11.7+mc.1.21.1.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `createdeco-2.1.3.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `createpropulsion-1.1.5.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `createrailwaysnavigator-neoforge-1.21.1-beta-0.9.1-C6.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `createrandomizefilters-1.0.7.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `cupboard-1.21.1-4.2.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `dragonlib-neoforge-1.21.1-beta-3.0.28.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `dungeons-and-taverns-desert-temple-replacement-v1.2.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `dungeons-and-taverns-jungle-temple-replacement-v1.2.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `dungeons-and-taverns-ocean-monument-replacement-v1.2.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `dungeons-and-taverns-stronghold-overhaul-v2.1.f.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `entityculling-neoforge-1.10.5-mc1.21.1.jar` | Dedicated traced-visibility adapter |
| `ferritecore-7.0.3-neoforge.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `framegen-1.4-neoforge-1.21.1.jar` | Dedicated final-colour/depth history adapter |
| `jei-1.21.1-neoforge-19.51.0.418.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `konkrete_neoforge_1.9.9_MC_1.21.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `kotlinforforge-5.12.0-all.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `lithosphere-1.7.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `lithostitched-1.7.2-neoforge-21.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `mapsyncer-1.0.3-neoforge-1.21.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `modernfix-neoforge-5.27.24+mc1.21.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `moonlight-1.21.1-3.6.5-neoforge.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `panoramica-neoforge-1.3.0+mc1.21.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `polymorph-neoforge-1.1.0+1.21.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `puffish_biome_dither-1.0.0-1.21-neoforge.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `railways-0.3.0-beta.2+neoforge-mc1.21.1.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `reamer-1.0.0.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `reeses-sodium-options-neoforge-2.2.4+mc1.21.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `ritchiesprojectilelib-2.1.2+mc.1.21.1-neoforge.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `rolling-tones-0.2.2.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `sable-neoforge-1.21.1-2.0.5.jar` | Dedicated moving coordinate-system adapter |
| `sable_xaero_bridge-0.1.11.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `skinrestorer-2.11.0+1.21-neoforge.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `sliceanddice-4.3.3-neoforge.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `slopium-neoforge-1.21.1-0.1.0-alpha.18.jar` | Scoped camera/distance compatibility; tick budgets retained; game check pending |
| `snassets-1.0.2.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `sodium-extra-neoforge-0.9.4+mc1.21.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `sodium-neoforge-0.8.13+mc1.21.1.jar` | Dedicated final-quad terrain capture |
| `sodiumdynamiclights-neoforge-1.0.10-1.21.1.jar` | Dedicated external-emitter adapter |
| `spark-1.10.124-neoforge.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `still-life-0.1.1.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `struts-1.3.1.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `tfmg-1.21.1-1.3.1-community.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `tinyredstone-1.21.1-6.1.6.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `tracks-neoforge-1.21.1-1.0.1.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `trading_floor-3.0.16.jar` | Shared model/ModelData and block-entity/contraption capture; visual check pending |
| `vista-1.21.1-5.5.6-neoforge.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `xaerominimap-neoforge-1.21.1-26.5.0.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |
| `xaeroworldmap-neoforge-1.21.1-1.46.0.jar` | Shared Minecraft capture or normal gameplay/UI path; visual check pending |


## Materials and client-only installation

The packaged renderer registers no synchronized blocks, items, menus, payloads, or particle
identifiers. Its only mod entrypoint is restricted to `Dist.CLIENT`; its mixin plugin also
skips dedicated servers. Local foam particles use vanilla atlas sprites without registry entries.
The optional `rtx-remix-content-neoforge` artifact owns Copper Beacon registration, persisted
state, menu payloads and physical wall connections. Install that small artifact on both ends
when using Copper Beacon; it has no dependency on renderer classes or the native runtime.
Existing block identifiers and NBT remain unchanged. Keep the content artifact when opening
existing saves containing that block.

One material catalog scans registered block states and their actual baked model sprites at
texture reload. The catalog classifies glass and light-emitting models together and indexes
registered FluidType sprite pairs. Unauthored luminous states receive six outward-facing area
lights, while explicit light rules retain priority. Surface emission is authored separately:
resource packs can supply `<sprite>_e.png`, with RGB brightness times alpha as an albedo emission
mask. Automatic fallback lights also scale by mean authored mask coverage; explicit light rules
remain authoritative. Seven supplied mask files are bound to 27 sprites (including 17 UV-remapped headlight variants) (see
`reference/materials/emission-mask-mapping.json`). Existing LabPBR emission remains the default
when no `_e` sidecar is provided. Animated
masks follow sprite animation uploads.

Non-glass translucent atlas surfaces use alpha-blended opaque materials, preserving their
paint and alpha without glass refraction. Glass models use a transmitting material with their
original sprite as a diffuse layer. Streamed Create quads are split when the material changes,
so a glass window and its opaque housing can share a contraption render buffer.

Fluid variants use recoloured vanilla water animation and the water normal flipbook. Aeronautics
and Enchantment Industry types use vanilla lava animation and matching generated lava normals;
vanilla lava remains lava. Each variant retains the alpha-weighted mean source RGB and mean
coverage across its source frames, reducing contrast near saturation instead of clipping the
mean. Original renderer vertex tint/alpha remains multiplicative. Default fluid tint also colours
transmission. Materials are indexed by registered fluid identity, so types sharing a water sprite
can select different base styles. Scoped vanilla, Sodium and Catnip FluidRenderHelper hooks
propagate that identity into terrain and streamed tank/contraption quads, with sprite fallback
for other renderers. Texture averages cannot reconstruct viscosity, foam, or measured optical
properties. Empty placeholder resources cannot supply a measurable colour.

Missing normal/height sidecars are generated from tileable Sobel luminance gradients in shared
atlases. Transparent neighbours do not raise silhouette ridges. Authored `_n` wins, and glass
keeps a flat automatic dielectric while retaining authored normals. The Materials settings expose
normal strength (0..4) and height depth (0..1); zero disables each generated effect. Settings commit
once when leaving the screen and recapture terrain once. The standalone
`packaging/generate_surface_maps.py` exports RGB normals, grey heights and LabPBR `_n` for the
5932 source textures plus 340 static fluid candidates. These files are an authoring export;
runtime generation follows the active pack. Glass retains its original diffuse sprite and has a
separate colour-selective transmission texture to strengthen coloured-light absorption.

Create contraptions have native world-space light owners independent of Sodium Dynamic Lights.
They cache luminous block positions and transform rule/automatic emitters with the entity's
interpolated local transform and any parent Sable frame. Copper Beacon disk axes, position,
shaping and radiance use its articulated pose. These lights and terrain, including sublevels,
share the Remix scene, so light and shadows cross owner boundaries. Provider-generated Create
point lights are excluded to avoid duplicate sources.

The active registry/pack catalog and original PNGs are exported to
`<game>/rtx-remix/reference/materials`. `rtxremix.materialReferenceDirectory` overrides the path.
`packaging/export_reference_materials.py --minecraft <1.21.1-client.jar>` creates the offline
reference authoring superset, fluid colour previews and JSON manifests under `reference/materials`;
its name-based candidates do not certify runtime block emission. See that folder's README for
mask authoring. Packaging and headless tests do not constitute an in-game pack compatibility test.


Sodium world-change rebuilds also enter the offscreen capture queue: an invisible surface can
still cast a shadow or appear in a reflection. A complete column snapshot may publish while a
newer dirty successor is queued, avoiding starvation in continuously edited machinery; world
and column-owner replacements remain invalid. Synchronous captures keep the queue bounded,
return evicted sections for recapture, and never wait for the render thread to consume itself.
Contraption lights reuse local/world definitions, skip unchanged poses, and budget creation and
retirement to 64 native operations per frame. Existing moving lights update without that cap so
large mechanisms do not acquire stale light transforms. Reference PNG/JSON file I/O runs on a
single background worker. No full-pack FPS or frame-time claim is made without a live game run.


Initial Sodium sections enter the capture retry ring even when its visibility walk has never
visited them. The ring visits at most 128 sections per normal frame and preserves Sodium's
initial/rebuild queue distinction, queue-size limits and running-job exclusion. It does not
schedule offscreen sorting or add work to synchronous `updateChunks(true)` waits. This prevents
an edit arriving during an existing job from starting overlapping rebuilds of that section.
The supplied sibling Sodium/Slopium source trees were used as references only; production
transformation tests target the exact JAR versions listed above.

Server datapack recipes, loot tables and mining tags for Copper Beacon now travel only with
the optional content artifact. A renderer-only single-player installation therefore does not
try to load recipes/tags referring to an unregistered content block.

Vanilla-only chunk-load reflag filtering no longer intercepts Sodium invalidations. Light-only
client notifications are preserved for external worker renderers, alongside the immutable light
snapshots already required by Sodium/Sable. In-place chunk packet replacements therefore reach
the renderer that owns their section cache. The DH near cut uses the server-clamped client view
distance, avoiding an uncovered band when a multiplayer server sends fewer chunks than the
client's configured distance.
