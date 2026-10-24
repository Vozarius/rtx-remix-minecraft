/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.particle.rapids;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** Local visual particles share vanilla sprites and never enter a synchronized registry. */
public enum RapidFoamParticles {
    RAPID_FOAM,
    RAPID_FOAM_DOT,
    RAPID_MIST;
    private static final SpriteProvider SPRITES = new SpriteProvider() {
        @Override
        public Sprite getSprite(int age, int maxAge) {
            return sprite(maxAge == 0 ? 0 : Math.min(7, age * 7 / maxAge));
        }
        @Override
        public Sprite getSprite(Random random) {
            return sprite(random.nextInt(8));
        }
        private Sprite sprite(int frame) {
            return ((SpriteAtlasTexture) MinecraftClient.getInstance().getTextureManager().getTexture(
                            Identifier.ofVanilla("textures/atlas/particles.png")))
                    .getSprite(Identifier.ofVanilla("generic_" + frame));
        }
    };
    private static final SpriteProvider DOT = new SpriteProvider() {
        @Override
        public Sprite getSprite(int age, int maxAge) {
            return SPRITES.getSprite(0, 1);
        }
        @Override
        public Sprite getSprite(Random random) {
            return SPRITES.getSprite(0, 1);
        }
    };
    public Particle create(
            MinecraftClient client, double x, double y, double z, double vx, double vy, double vz) {
        return switch (this) {
            case RAPID_FOAM -> new RapidFoamParticle.Factory(SPRITES).createParticle(ParticleTypes.CLOUD, client.world, x,y,z,vx,vy,vz);
            case RAPID_FOAM_DOT -> new RapidFoamDotParticle.Factory(DOT).createParticle(ParticleTypes.CLOUD, client.world, x,y,z,vx,vy,vz);
            case RAPID_MIST -> new RapidMistParticle.Factory(SPRITES).createParticle(ParticleTypes.CLOUD, client.world, x,y,z,vx,vy,vz);
        };
    }
}
