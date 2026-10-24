# SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
"""Extract reference-pack textures and water-coloured previews without launching Minecraft.

Static model references are an authoring superset, not a registry/light-emission dump.
The mod exports its exact active registry/pack catalog on resource reload.
Requires Pillow; run from the repository root.
"""
from __future__ import annotations

import argparse
import io
import json
import re
import zipfile
from pathlib import Path

from PIL import Image, UnidentifiedImageError

LIGHT_NAME = re.compile(r"glow|light|lamp|lantern|torch|lava|molten|neon|signal|burner|furnace|fire|lit_|_lit|bulb|luminous", re.I)
FLUID_NAME = re.compile(r"(?:^|/)(?:fluid|fluids|liquid|liquids)(?:/|_)|_(?:still|flow|flowing)$|(?:^|/)(?:lava|water)_", re.I)


def identifier(value: str, namespace: str = "minecraft") -> str:
    return value if ":" in value else namespace + ":" + value


def average(image: Image.Image) -> tuple[float, float, float, float]:
    pixels = list(image.convert("RGBA").get_flattened_data())
    weight = sum(p[3] for p in pixels)
    rgb = tuple(sum(p[c] * p[3] for p in pixels) / weight / 255 if weight else 0 for c in range(3))
    return (*rgb, weight / len(pixels) / 255)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, default=Path("reference"))
    parser.add_argument("--minecraft", type=Path, required=True)
    args = parser.parse_args()
    resources: dict[str, tuple[bytes, str]] = {}
    def archive(data: bytes, owner: str) -> None:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            for name in jar.namelist():
                if name.startswith("assets/") and name.endswith((".png", ".json", ".mcmeta")):
                    resources[name] = (jar.read(name), owner)
                elif name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                    archive(jar.read(name), owner + "!" + name)
    archive(args.minecraft.read_bytes(), args.minecraft.name)
    for jar in sorted(args.reference.glob("*.jar")):
        archive(jar.read_bytes(), jar.name)
    models: dict[str, dict] = {}
    blocks: dict[str, set[str]] = {}
    textures: dict[str, tuple[bytes, str]] = {}
    for name, resource in resources.items():
        parts = name.split("/", 3)
        if len(parts) != 4:
            continue
        _, ns, kind, path = parts
        if kind == "models" and path.endswith(".json"):
            models[ns + ":" + path[:-5]] = json.loads(resource[0])
        elif kind == "textures" and path.endswith(".png"):
            textures[ns + ":" + path[:-4]] = resource
    def model_textures(model_id: str, trail: frozenset[str] = frozenset()) -> dict[str, str]:
        if model_id in trail or model_id not in models:
            return {}
        model = models[model_id]
        result = model_textures(identifier(model["parent"]), trail | {model_id}) if "parent" in model else {}
        result.update(model.get("textures", {}))
        return result
    def resolve(value: str, slots: dict[str, str]) -> str | None:
        seen: set[str] = set()
        while value.startswith("#"):
            if value in seen or value[1:] not in slots:
                return None
            seen.add(value)
            value = slots[value[1:]]
        return identifier(value)
    def variants(node: object) -> set[str]:
        if isinstance(node, dict):
            result = {identifier(node["model"])} if isinstance(node.get("model"), str) else set()
            for child in node.values():
                result.update(variants(child))
            return result
        if isinstance(node, list):
            return set().union(*(variants(child) for child in node))
        return set()
    for name, resource in resources.items():
        match = re.fullmatch(r"assets/([^/]+)/blockstates/(.+)\.json", name)
        if not match:
            continue
        block = match[1] + ":" + match[2]
        for model_id in variants(json.loads(resource[0])):
            slots = model_textures(model_id)
            for value in slots.values():
                texture = resolve(value, slots)
                if texture in textures:
                    blocks.setdefault(texture, set()).add(block)
    out = args.reference / "materials"
    rows = []
    fluids = []
    water = Image.open(io.BytesIO(textures["minecraft:block/water_still"][0])).convert("RGBA")
    water_grey = [sum(p[:3]) / 3 for p in water.get_flattened_data()]
    water_mean = sum(water_grey) / len(water_grey)
    for texture in sorted(set(blocks) | {key for key in textures if FLUID_NAME.search(key.split(":", 1)[1])}):
        ns, path = texture.split(":", 1)
        data, owner = textures[texture]
        try:
            image = Image.open(io.BytesIO(data)).convert("RGBA")
        except UnidentifiedImageError:
            print(f"Skipped unreadable texture {texture} in {owner}")
            continue
        rgba = average(image)
        relative = Path("sources/assets") / ns / "textures" / (path + ".png")
        dest = out / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        if not dest.exists():
            dest.write_bytes(data)
        animation = resources.get("assets/" + ns + "/textures/" + path + ".png.mcmeta")
        if animation and not dest.with_suffix(".png.mcmeta").exists():
            dest.with_suffix(".png.mcmeta").write_bytes(animation[0])
        fluid = bool(FLUID_NAME.search(path))
        candidate = bool(LIGHT_NAME.search(texture) or any(LIGHT_NAME.search(b) for b in blocks.get(texture, set())))
        row = {"sprite": texture, "source": relative.as_posix(), "blocks": sorted(blocks.get(texture, set())),
               "archive": owner, "average_rgba": rgba, "has_transparency": image.getextrema()[3][0] < 255,
               "emission_candidate_by_name_only": candidate,
               "mask_path": "assets/" + ns + "/textures/" + path + "_e.png", "fluid_texture_candidate": fluid}
        if candidate:
            link = out / "emission-candidates/assets" / ns / "textures" / (path + ".png")
            link.parent.mkdir(parents=True, exist_ok=True)
            if not link.exists():
                link.write_bytes(data)
        if fluid:
            preview = Image.new("RGBA", water.size)
            preview.putdata([tuple(min(255, round(rgba[c] * 255 * light / water_mean)) for c in range(3))
                             + (round(rgba[3] * 255),) for light in water_grey])
            relative_preview = Path("fluid-previews/assets") / ns / "textures" / (path + ".png")
            preview_path = out / relative_preview
            preview_path.parent.mkdir(parents=True, exist_ok=True)
            if not preview_path.exists():
                preview.save(preview_path)
                metadata = resources.get("assets/minecraft/textures/block/water_still.png.mcmeta")
                if metadata:
                    preview_path.with_suffix(".png.mcmeta").write_bytes(metadata[0])
            row["water_preview"] = relative_preview.as_posix()
            fluids.append(row)
        rows.append(row)
    out.mkdir(exist_ok=True)
    (out / "catalog-static.json").write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding="utf-8")
    (out / "fluids-static.json").write_text(json.dumps(fluids, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Extracted {len(rows)} model/fluid textures; {sum(r['emission_candidate_by_name_only'] for r in rows)} emission candidates; {len(fluids)} fluid texture candidates -> {out}")


if __name__ == "__main__":
    main()
