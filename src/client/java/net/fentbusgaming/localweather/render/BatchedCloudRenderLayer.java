package net.fentbusgaming.localweather.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

public class BatchedCloudRenderLayer extends RenderLayer {

    public BatchedCloudRenderLayer(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    // This creates a dedicated, fully batched pipeline target
    public static RenderLayer getBatchedClouds() {
        return RenderLayer.of(
                "local_weather_batched_clouds",
                VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, // Matches your stripped format
                VertexFormat.DrawMode.QUADS,
                2097152, // 2MB huge buffer size to batch thousands of cloud quads together
                false,   // No crumbling animations
                true,    // Sort transparency
                MultiPhaseParameters.builder()
                        // The Magic Trick: An unlit translucent shader that ignores world lightmap context!
                        .program(RenderPhase.BEACON_BEAM_PROGRAM)
                        .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                        .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                        .cull(RenderPhase.DISABLE_CULLING) // Let players see faces from underneath
                        .build(false)
        );
    }
}