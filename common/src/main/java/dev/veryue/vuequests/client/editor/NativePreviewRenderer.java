package dev.veryue.vuequests.client.editor;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

public class NativePreviewRenderer implements Font.GlyphVisitor {

    private final List<GlyphRenderState> glyphs = new ArrayList<>();

    @Override
    public void acceptRenderable(TextRenderable renderable) {
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

        TextureTarget target = new TextureTarget("Quest component preview", width * scale, height * scale,
                GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        StagedVertexBuffer vertices = new StagedVertexBuffer(() -> "Quest component preview vertices", 256 * 1024);
        ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Quest component preview");
        CompletableFuture<NativeImage> result = new CompletableFuture<>();
        GpuBuffer readback = null;
        RenderSystem.backupProjectionMatrix();
        try {
            List<StagedVertexBuffer.Draw> draws = new ArrayList<>();
            List<GlyphRenderState> batches = new ArrayList<>();
            GlyphRenderState previous = null;
            StagedVertexBuffer.Draw draw = null;
            for (GlyphRenderState glyph : glyphs) {
                var pipeline = glyph.pipeline();
                if (previous == null || previous.pipeline() != pipeline || !previous.textureSetup().equals(glyph.textureSetup())) {
                    draw = vertices.appendDraw(pipeline.getVertexFormatBinding(0), pipeline.getPrimitiveTopology());
                    draws.add(draw);
                    batches.add(glyph);
                    previous = glyph;
                }
                glyph.buildVertices(vertices.getVertexBuilder(draw));
            }
            vertices.upload();
            RenderSystem.resizeAllAutoStorageIndexBuffers();
            Projection projection = new Projection();
            projection.setupOrtho(1000, 11000, width, height, true);
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
            var transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f().setTranslation(0, 0, -11000));
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Quest component preview", target.getColorTextureView(), Optional.of(new Vector4f()),
                    target.getDepthTextureView(), OptionalDouble.of(0))) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                for (int index = 0; index < draws.size(); index++) {
                    var execute = vertices.getExecuteInfo(draws.get(index));
                    if (execute == null) continue;
                    GlyphRenderState glyph = batches.get(index);
                    TextureSetup texture = glyph.textureSetup();
                    pass.setPipeline(RenderSystem.getCompiledPipeline(glyph.pipeline()));
                    pass.setVertexBuffer(0, execute.vertexBuffer().slice());
                    if (texture.texure0() != null) pass.setUniform("Sampler0", texture.texure0(), texture.sampler0());
                    if (texture.texure1() != null) pass.setUniform("Sampler1", texture.texure1(), texture.sampler1());
                    if (texture.texure2() != null) pass.setUniform("Sampler2", texture.texure2(), texture.sampler2());
                    pass.setIndexBuffer(execute.indexBuffer(), execute.indexType());
                    pass.drawIndexed(execute.indexCount(), 1, execute.firstIndex(), execute.baseVertex(), 0);
                }
            }

            readback = RenderSystem.getDevice().createBuffer(() -> "Quest component preview readback", 9,
                    (long) target.width * target.height * 4);
            GpuBuffer pixels = readback;
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(target.getColorTexture(), pixels, 0, () -> {
                NativeImage image = null;
                try (GpuBufferSlice.MappedView mapped = pixels.map(true, false)) {
                    image = new NativeImage(target.width, target.height, false);
                    for (int y = 0; y < target.height; y++) {
                        for (int x = 0; x < target.width; x++) {
                            image.setPixelABGR(x, target.height - y - 1, mapped.data().getInt((x + y * target.width) * 4));
                        }
                    }
                    if (!result.complete(image)) image.close();
                    image = null;
                } catch (Throwable failure) {
                    if (image != null) image.close();
                    result.completeExceptionally(failure);
                } finally {
                    pixels.close();
                    vertices.close();
                    projectionBuffer.close();
                    target.destroyBuffers();
                }
            }, 0);
        } catch (Throwable failure) {
            if (readback != null) readback.close();
            vertices.close();
            projectionBuffer.close();
            target.destroyBuffers();
            result.completeExceptionally(failure);
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
        return result;
    }
}
