/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtxRemixModCommandTest {

    @Test
    void enablingRtxDoesNotClaimToRenderWhenInitializationFailed() {
        assertEquals("RTX Rendering: unavailable (renderer not initialized)",
                RtxRemixMod.rtxRenderingStatus(true, false));
        assertEquals("RTX Rendering: ON", RtxRemixMod.rtxRenderingStatus(true, true));
        assertEquals("RTX Rendering: OFF", RtxRemixMod.rtxRenderingStatus(false, false));
        assertEquals("RTX Rendering: OFF", RtxRemixMod.rtxRenderingStatus(false, true));
    }

    @Test
    void cameraPlaySupportsDefaultTimeAndExplicitClockModes() {
        CommandDispatcher<ServerCommandSource> dispatcher = cameraDispatcher();

        assertParses(dispatcher, "camera play demo");
        assertParses(dispatcher, "camera play demo time");
        assertParses(dispatcher, "camera play demo fixed-60");

        ParseResults<ServerCommandSource> invalid =
                dispatcher.parse("camera play demo bogus", null);
        assertTrue(invalid.getReader().canRead() || !invalid.getExceptions().isEmpty());
    }

    @Test
    void cameraPlaySuggestsOnlySupportedClockModes() {
        CommandDispatcher<ServerCommandSource> dispatcher = cameraDispatcher();
        ParseResults<ServerCommandSource> parsed =
                dispatcher.parse("camera play demo ", null);

        Set<String> suggestions = dispatcher.getCompletionSuggestions(parsed).join()
                .getList()
                .stream()
                .map(suggestion -> suggestion.getText())
                .collect(Collectors.toSet());

        assertEquals(Set.of("time", "fixed-60"), suggestions);
    }

    @Test
    void omittedModeRemainsExecutableAtCameraNameNode() {
        CommandDispatcher<ServerCommandSource> dispatcher = cameraDispatcher();
        var nameNode = dispatcher.getRoot()
                .getChild("camera")
                .getChild("play")
                .getChild("name");

        assertNotNull(nameNode);
        assertNotNull(nameNode.getCommand());
        assertNotNull(nameNode.getChild("time").getCommand());
        assertNotNull(nameNode.getChild("fixed-60").getCommand());
    }

    private static CommandDispatcher<ServerCommandSource> cameraDispatcher() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(RtxRemixMod.cameraCommand());
        return dispatcher;
    }

    private static void assertParses(
            CommandDispatcher<ServerCommandSource> dispatcher,
            String command
    ) {
        ParseResults<ServerCommandSource> parsed = dispatcher.parse(command, null);
        assertFalse(parsed.getReader().canRead(), command);
        assertTrue(parsed.getExceptions().isEmpty(), command);
    }
}
