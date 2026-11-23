package dev.veryue.vuequests.client.editor;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

public class NativePreviewRenderer implements Font.GlyphVisitor {

    private final List<GlyphRenderState> glyphs = new ArrayList<>();

    @Override
    public void acceptGlyph(TextRenderable.Styled glyph) {
        acceptEffect(glyph);
    }

    @Override
    public void acceptEffect(TextRenderable renderable) {
        glyphs.add(new GlyphRenderState(new Matrix3x2f(), renderable, null));
    }

    public CompletableFuture<NativeImage> draw(Font font, List<FormattedCharSequence> lines, int width, int height,
                                              int top, int scale, int leftGutter, int topGutter) {
        RenderSystem.assertOnRenderThread();
        for (int line = 0; line < lines.size(); line++) {
            int y = line * font.lineHeight + topGutter - top;
            if (y + font.lineHeight + 512 >= 0 && y - 512 < height) {
                font.prepareText(lines.get(line), leftGutter, y, 0xFFFFFFFF, false, false, 0).visit(this);
            }
        }

        TextureTarget target = new TextureTarget("Quest component preview", width * scale, height * scale, true);
        CompletableFuture<NativeImage> result = new CompletableFuture<>();
        GpuBuffer readback = null;
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 0);
            drawGlyphs(encoder, target, width, height);

            readback = RenderSystem.getDevice().createBuffer(() -> "Quest component preview readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) target.width * target.height * 4);
            GpuBuffer pixels = readback;
            encoder.copyTextureToBuffer(target.getColorTexture(), pixels, 0,
                    () -> readImage(encoder, target, pixels, result), 0);
        } catch (Throwable failure) {
            if (readback != null) {
                readback.close();
            }
            target.destroyBuffers();
            result.completeExceptionally(failure);
        }
        return result;
    }

    private void drawGlyphs(CommandEncoder encoder, TextureTarget target, int width, int height) {
        RenderSystem.backupProjectionMatrix();
        try (ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Quest component preview");
             ByteBufferBuilder vertices = new ByteBufferBuilder(256 * 1024)) {
            Projection projection = new Projection();
            projection.setupOrtho(1000, 11000, width, height, true);
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                    new Matrix4f().setTranslation(0, 0, -11000), new Vector4f(1), new Vector3f(), new Matrix4f());
            int index = 0;
            while (index < glyphs.size()) {
                GlyphRenderState glyph = glyphs.get(index);
                RenderPipeline pipeline = glyph.pipeline();
                TextureSetup texture = glyph.textureSetup();
                BufferBuilder batch = new BufferBuilder(vertices, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
                do {
                    glyph.buildVertices(batch);
                    index++;
                    if (index == glyphs.size()) {
                        break;
                    }
                    glyph = glyphs.get(index);
                } while (glyph.pipeline() == pipeline && glyph.textureSetup().equals(texture));

                try (MeshData mesh = batch.buildOrThrow();
                     GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Quest component preview vertices",
                             GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer())) {
                    int indexCount = mesh.drawState().indexCount();
                    RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
                    GpuBuffer indexBuffer = indices.getBuffer(indexCount);
                    try (RenderPass pass = encoder.createRenderPass(() -> "Quest component preview", target.getColorTextureView(),
                            OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                        pass.setPipeline(pipeline);
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("DynamicTransforms", transforms);
                        if (texture.texure0() != null) {
                            pass.bindTexture("Sampler0", texture.texure0(), texture.sampler0());
                        }
                        if (texture.texure1() != null) {
                            pass.bindTexture("Sampler1", texture.texure1(), texture.sampler1());
                        }
                        if (texture.texure2() != null) {
                            pass.bindTexture("Sampler2", texture.texure2(), texture.sampler2());
                        }
                        pass.setVertexBuffer(0, vertexBuffer);
                        pass.setIndexBuffer(indexBuffer, indices.type());
                        pass.drawIndexed(0, 0, indexCount, 1);
                    }
                }
            }
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void readImage(CommandEncoder encoder, TextureTarget target, GpuBuffer pixels, CompletableFuture<NativeImage> result) {
        NativeImage image = null;
        try (pixels) {
            try (GpuBuffer.MappedView mapped = encoder.mapBuffer(pixels, true, false)) {
                image = new NativeImage(target.width, target.height, false);
                for (int y = 0; y < target.height; y++) {
                    for (int x = 0; x < target.width; x++) {
                        image.setPixelABGR(x, target.height - y - 1, mapped.data().getInt((x + y * target.width) * 4));
                    }
                }
            }
            if (result.complete(image)) {
                image = null;
            }
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        } finally {
            if (image != null) {
                image.close();
            }
            target.destroyBuffers();
        }
    }
}
