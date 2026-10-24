# NeoForge 1.21.1

This checkout builds a native NeoForge mod for Minecraft Java Edition 1.21.1.
Use Java 21 and NeoForge 21.1.242 or newer in the 21.1 series.

## Official Minecraft Launcher

1. Install Minecraft Java Edition 1.21.1 and run it once.
2. Download the [NeoForge installer](https://neoforged.net/) for Minecraft 1.21.1,
   run it with Java 21, and select **Install client**.
3. In the official launcher, select the **NeoForge** installation. Open that
   installation's game directory and place the packaged mod JAR in `mods/`.
4. Start the NeoForge installation. The mod extracts its bundled Remix runtime
   into that game directory on first launch.

Use the JAR from `build/distributions/`. It contains the Java mod, JNI bridge,
Remix SDK, runtime textures, baseline PBR assets, and gameplay configuration.
The lightweight JAR from `build/libs/` is for the development staging tools.

Prism is the repository's development launcher. Players can use the official
launcher with NeoForge.

## Renderer compatibility

Terrain capture currently requires Minecraft's vanilla chunk builder. Sodium
replaces that builder and world traversal, so its terrain does not reach Remix.
Test this port without Sodium and its dependent addons, including Sodium Extra,
Reese's Sodium Options, and Sodium Dynamic Lights. Other mods that replace
terrain rendering need a capture integration before they can be supported.

The RTX toggle reports the renderer as unavailable when initialization failed;
the master setting alone does not mean that Remix started. Check `logs/latest.log`
for the first RTX Remix initialization error.

## Build

Install a 64-bit JDK 21 and Visual Studio C++ tools, then run:

```powershell
.\gradlew.bat build test -Parch=win64
.\gradlew.bat package -Parch=win64
```

For Windows ARM64, select `-Parch=arm64`. Omitting the architecture includes both
runtime payloads. The resulting filename includes `neoforge`.

The sources retain Yarn names through Architectury Loom. Distribution remaps
Minecraft references to Mojang names and converts the access widener into a
NeoForge access transformer. NeoForge metadata and native event registration
load the mod. Resource and saved-block identifiers retain the
`rtx-remix-renderer` namespace; the loader mod ID is `rtx_remix_renderer`.

## Verification

Build and JVM tests cover the CPU-side contracts. A successful build does not
validate native loading, Mixin application, or rendered output. This port still
needs a signed-in in-game run covering terrain, entities, particles, dimensions,
overlay input, and Copper Beacon controls before release.
