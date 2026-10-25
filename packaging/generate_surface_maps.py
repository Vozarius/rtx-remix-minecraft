# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
"""Batch normal/height generator and water/lava recolouring for the reference material catalog.

Requires Python, Pillow and numpy. Runtime generation uses the equivalent Java Sobel kernel.
Authored source PNGs and user emission masks are never changed.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image


def surface_maps(pixels: np.ndarray, strength: float, depth: float) -> tuple[np.ndarray, np.ndarray]:
    height = pixels[:, :, :3].astype(np.float32) @ np.array([.2126, .7152, .0722], dtype=np.float32) / 255
    dx = np.zeros_like(height)
    dy = np.zeros_like(height)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            value = np.roll(height, (-oy, -ox), axis=(0, 1))
            alpha = np.roll(pixels[:, :, 3], (-oy, -ox), axis=(0, 1))
            value = np.where(alpha == 0, height, value)
            dx += value * ox * (2 if oy == 0 else 1)
            dy += value * oy * (2 if ox == 0 else 1)
    dx *= -strength / 4
    dy *= -strength / 4
    dx = np.where(pixels[:, :, 3] == 0, 0, dx)
    dy = np.where(pixels[:, :, 3] == 0, 0, dy)
    norm = np.sqrt(dx*dx + dy*dy + 1)
    normal = np.stack([dx/norm*.5+.5, dy/norm*.5+.5, 1/norm], axis=2)
    return np.rint(normal*255).astype(np.uint8), np.rint((1-depth+height*depth)*255).astype(np.uint8)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("reference/materials"))
    parser.add_argument("--strength", type=float, default=1)
    parser.add_argument("--depth", type=float, default=.12)
    args = parser.parse_args()
    if not 0 <= args.strength <= 4 or not 0 <= args.depth <= 1:
        parser.error("strength must be in 0..4, depth in 0..1")
    root = args.root
    rows = json.loads((root / "catalog-static.json").read_text(encoding="utf-8"))
    fluid_rows = json.loads((root / "fluids-static.json").read_text(encoding="utf-8"))
    output = root / "generated-surface-maps"
    count = 0
    for row in rows:
        ns, resource = row["sprite"].split(":", 1)
        source = root / row["source"]
        pixels = np.array(Image.open(source).convert("RGBA"))
        frame_width = pixels.shape[1]
        frame_height = pixels.shape[0]
        metadata = source.with_suffix(".png.mcmeta")
        if metadata.exists():
            animation = json.loads(metadata.read_text(encoding="utf-8")).get("animation", {})
            frame_width = animation.get("width", min(pixels.shape[0], pixels.shape[1]))
            frame_height = animation.get("height", frame_width)
        normal = np.zeros((*pixels.shape[:2], 3), dtype=np.uint8)
        height = np.zeros(pixels.shape[:2], dtype=np.uint8)
        for y in range(0, pixels.shape[0], frame_height):
            for x in range(0, pixels.shape[1], frame_width):
                tile = pixels[y:y+frame_height, x:x+frame_width]
                normal[y:y+frame_height, x:x+frame_width], height[y:y+frame_height, x:x+frame_width] = surface_maps(tile, args.strength, args.depth)
        lab = np.dstack([normal[:, :, :2], np.full(height.shape, 255, dtype=np.uint8), height])
        dest = output / "assets" / ns / "textures" / resource
        dest.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray(lab).save(str(dest)+"_n.png")
        Image.fromarray(normal).save(str(dest)+"_normal.png")
        Image.fromarray(height).save(str(dest)+"_height.png")
        for suffix in ("_n", "_normal", "_height"):
            if metadata.exists():
                Path(str(dest)+suffix+".png.mcmeta").write_bytes(metadata.read_bytes())
        count += 1
    water = np.array(Image.open(root / "sources/assets/minecraft/textures/block/water_still.png").convert("RGBA"))
    lava = np.array(Image.open(root / "sources/assets/minecraft/textures/block/lava_still.png").convert("RGBA"))
    fluid_report = []
    for row in fluid_rows:
        ns, resource = row["sprite"].split(":", 1)
        lava_style = ns in {"aeronautics", "create_aeronautics", "create_enchantment_industry", "enchantment_industry"} or ns == "minecraft" and "lava_" in resource
        base = lava if lava_style else water
        light = base[:, :, :3].astype(np.float32) @ np.array([.2126, .7152, .0722], dtype=np.float32)
        variation = light / max(float(light.mean()), .0001)
        rgba = row["average_rgba"]
        target = np.array(rgba[:3])
        peak_variation = float(variation.max())
        contrast = target if peak_variation <= 1 else np.minimum(target, (1-target)/(peak_variation-1))
        colour = np.clip(np.rint((target+(variation[:, :, None]-1)*contrast)*255), 0, 255).astype(np.uint8)
        result = np.dstack([colour, np.full(light.shape, round(rgba[3]*255), dtype=np.uint8)])
        dest = root / "fluid-textures/assets" / ns / "textures" / (resource+".png")
        dest.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray(result).save(dest)
        metadata = root / ("sources/assets/minecraft/textures/block/"+("lava_still" if lava_style else "water_still")+".png.mcmeta")
        if metadata.exists():
            dest.with_suffix(".png.mcmeta").write_bytes(metadata.read_bytes())
        fluid_report.append({"sprite": row["sprite"], "base": "lava" if lava_style else "water", "source_average_rgba": rgba, "texture": dest.relative_to(root).as_posix()})
    (root / "fluid-textures/catalog.json").write_text(json.dumps(fluid_report, indent=2), encoding="utf-8")
    (output / "generator.json").write_text(json.dumps({"kernel":"Sobel", "normal_strength":args.strength,"height_depth":args.depth,"textures":count}), encoding="utf-8")
    print(f"Generated {count} normal/height sets and {len(fluid_report)} fluid animations")


if __name__ == "__main__":
    main()
