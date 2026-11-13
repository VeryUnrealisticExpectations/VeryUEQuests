package dev.veryue.vuequests.client.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.JsonOps;
import dev.veryue.vuequests.exceptions.EditorResourcesUnavailableException;
import dev.veryue.vuequests.editor.QuestEditorAssets;
import dev.veryue.vuequests.editor.QuestEditorResources;
import dev.veryue.vuequests.editor.QuestEditorService.ConflictException;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class NativeComponentPreview {
    private static final String SOURCE_TAG = "vuequests-preview:";
    private final LinkedHashMap<String, NativePreviewWork> pending = new LinkedHashMap<>();
    private final LinkedHashMap<String, JsonObject> cache = new LinkedHashMap<>(16, 0.75F, true);
    private boolean scheduled;
    private int inFlight;
    private long cacheBytes;

    public synchronized void invalidate() {
        cache.clear();
        cacheBytes = 0;
    }

    public synchronized CompletableFuture<JsonObject> request(JsonObject request) {
        String id = string(request, "previewId", "preview");
        if (id.length() > 128) throw new IllegalArgumentException("Invalid preview ID.");
        if (pending.size() >= 16 && !pending.containsKey(id)) return CompletableFuture.failedFuture(new EditorResourcesUnavailableException("The native preview queue is busy."));
        CompletableFuture<JsonObject> response = new CompletableFuture<JsonObject>().orTimeout(2, TimeUnit.SECONDS);
        NativePreviewWork previous = pending.put(id, new NativePreviewWork(request.deepCopy(), response));
        if (previous != null) previous.response().completeExceptionally(new ConflictException("A newer preview replaced this request."));
        scheduleDrain();
        return response;
    }

    private void scheduleDrain() {
        if (!scheduled && !pending.isEmpty() && inFlight < 2) {
            scheduled = true;
            Minecraft.getInstance().execute(this::drain);
        }
    }

    private void drain() {
        NativePreviewWork entry;
        synchronized (this) {
            scheduled = false;
            if (pending.isEmpty() || inFlight >= 2) return;
            entry = pending.pollFirstEntry().getValue();
            inFlight++;
            scheduleDrain();
        }
        if (entry.response().isDone()) {
            finished();
            return;
        }
        try {
            if (!QuestEditorResources.hasLocalClient() || Minecraft.getInstance().getSingleplayerServer() == null) throw new EditorResourcesUnavailableException("The local client is unavailable.");
            String revision = QuestEditorAssets.revision();
            if (!revision.equals(string(entry.request(), "assetRevision", ""))) throw new ConflictException("Minecraft resources changed; refresh the font catalog.");
            JsonObject cacheRequest = entry.request().deepCopy();
            cacheRequest.remove("requestId");
            cacheRequest.remove("previewId");
            String key = cacheRequest.toString();
            boolean animated = hasObfuscated(cacheRequest.get("fragments"));
            JsonObject cached;
            synchronized (this) { cached = animated ? null : cache.get(key); }
            if (cached != null) {
                JsonObject response = cached.deepCopy();
                copyRequestId(entry.request(), response);
                entry.response().complete(response);
                finished();
                return;
            }
            render(entry.request()).whenComplete((frame, failure) -> {
                if (failure != null) {
                    entry.response().completeExceptionally(failure);
                    finished();
                    return;
                }
                frame.response().addProperty("animated", animated);
                CompletableFuture.runAsync(() -> {
                    try (frame) {
                        JsonObject response = frame.response();
                        unpremultiply(frame.image());
                        response.addProperty("png", Base64.getEncoder().encodeToString(png(frame.image())));
                        if (!revision.equals(QuestEditorAssets.revision())) throw new ConflictException("Minecraft resources changed during rendering.");
                        synchronized (this) {
                            long bytes = key.length() * 2L + response.toString().length() * 2L;
                            if (!animated && bytes <= 12 * 1024 * 1024) {
                                JsonObject replaced = cache.put(key, response.deepCopy());
                                if (replaced != null) cacheBytes -= key.length() * 2L + replaced.toString().length() * 2L;
                                cacheBytes += bytes;
                                while (cacheBytes > 12 * 1024 * 1024 || cache.size() > 32) {
                                    var oldest = cache.pollFirstEntry();
                                    cacheBytes -= oldest.getKey().length() * 2L + oldest.getValue().toString().length() * 2L;
                                }
                            }
                        }
                        entry.response().complete(response);
                    } catch (Exception exception) { entry.response().completeExceptionally(exception); }
                    finally { finished(); }
                }, Util.ioPool());
            });
        } catch (Exception exception) {
            entry.response().completeExceptionally(exception);
            finished();
        }
    }

    private synchronized void finished() {
        inFlight--;
        scheduleDrain();
    }

    private CompletableFuture<NativePreviewFrame> render(JsonObject request) throws IOException {
        RenderSystem.assertOnRenderThread();
        int wrapWidth = integer(request, "width", 320, 32, 1024);
        int scale = integer(request, "scale", 2, 1, 4);
        int viewportTop = integer(request, "viewportTop", 0, 0, 1_000_000);
        int viewportHeight = integer(request, "viewportHeight", 512, 1, 1024);
        if ((long) wrapWidth * viewportHeight * scale * scale > 4_194_304) throw new IllegalArgumentException("The preview dimensions exceed four million pixels.");
        JsonArray fragments = request.getAsJsonArray("fragments");
        if (fragments == null || fragments.size() > 512) throw new IllegalArgumentException("Supply at most 512 component fragments.");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) throw new EditorResourcesUnavailableException("The hosting client has no active world.");
        Font font = minecraft.font;
        List<FormattedText> parts = new ArrayList<>();
        List<PreviewSourcePoint> sources = new ArrayList<>();
        Map<String, JsonObject> outputFragments = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        Set<String> editableIds = new HashSet<>();
        float[] bearings = {0, 0, 0, 9};
        int characters = 0;
        for (JsonElement element : fragments) {
            JsonObject fragment = element.getAsJsonObject();
            String id = string(fragment, "id", "");
            if (id.isEmpty() || id.length() > 128 || !ids.add(id)) throw new IllegalArgumentException("Component fragment IDs must be unique.");
            if (!fragment.has("component")) throw new IllegalArgumentException("Supply each fragment's component.");
            checkDepth(fragment.get("component"), 0);
            Component component = ComponentSerialization.CODEC.parse(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), fragment.get("component"))
                    .getOrThrow(IllegalArgumentException::new);
            boolean editable = fragment.has("literal") && !fragment.get("literal").isJsonNull();
            String plain = component.getString();
            if (editable) editableIds.add(id);
            if (editable && !plain.equals(fragment.get("literal").getAsString())) throw new IllegalArgumentException("Editable fragment text must match its native component.");
            characters += plain.length();
            if (characters > 8192) throw new IllegalArgumentException("Native previews support at most 8192 text characters; the saved component can be larger.");
            JsonObject output = new JsonObject();
            output.addProperty("id", id);
            output.addProperty("plainText", plain);
            output.add("ranges", new JsonArray());
            output.add("carets", new JsonArray());
            outputFragments.put(id, output);
            int[] offset = {0};
            component.visit((style, text) -> {
                int base = offset[0];
                StringDecomposer.iterateFormatted(text, style, (index, resolvedStyle, codePoint) -> {
                    var sequence = FormattedCharSequence.codepoint(codePoint, resolvedStyle);
                    var bounds = font.prepareText(sequence, 0, 0, 0xFFFFFFFF, false, false, 0).bounds();
                    if (bounds != null) {
                        float advance = font.getSplitter().stringWidth(sequence);
                        bearings[0] = Math.min(bearings[0], bounds.left());
                        bearings[1] = Math.max(bearings[1], bounds.right() - advance);
                        bearings[2] = Math.min(bearings[2], bounds.top());
                        bearings[3] = Math.max(bearings[3], bounds.bottom());
                    }
                    int sourceIndex = sources.size();
                    sources.add(new PreviewSourcePoint(id, base + index, base + index + Character.charCount(codePoint), !editable));
                    parts.add(FormattedText.of(new String(Character.toChars(codePoint)), resolvedStyle.withInsertion(SOURCE_TAG + sourceIndex)));
                    return true;
                });
                offset[0] += text.length();
                return Optional.empty();
            }, Style.EMPTY);
        }
        int leftGutter = Math.min(wrapWidth / 3, Math.max(2, (int) Math.ceil(-bearings[0]) + 1));
        int rightGutter = Math.min(wrapWidth / 3, Math.max(2, (int) Math.ceil(bearings[1]) + 1));
        int topGutter = Math.min(512, Math.max(1, (int) Math.ceil(-bearings[2]) + 1));
        int bottomGutter = Math.min(512, Math.max(2, (int) Math.ceil(bearings[3] - font.lineHeight) + 1));
        int textWidth = Math.max(1, wrapWidth - leftGutter - rightGutter);
        List<FormattedText> logicalLines = new ArrayList<>(font.getSplitter().splitLines(FormattedText.composite(parts), textWidth, Style.EMPTY));
        if (logicalLines.isEmpty()) logicalLines = List.of(FormattedText.EMPTY);
        List<FormattedCharSequence> lines = Language.getInstance().getVisualOrder(logicalLines);
        float[] widest = {0};
        Map<String, Set<String>> seenCarets = new HashMap<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            int line = lineIndex;
            List<NativePreviewGlyph> glyphs = new ArrayList<>();
            lines.get(line).accept((index, style, codePoint) -> {
                float advance = font.getSplitter().stringWidth(FormattedCharSequence.codepoint(codePoint, style));
                String marker = style.getInsertion();
                int sourceIndex = marker != null && marker.startsWith(SOURCE_TAG) ? Integer.parseInt(marker.substring(SOURCE_TAG.length())) : -1;
                glyphs.add(new NativePreviewGlyph(codePoint, style, advance, sourceIndex));
                return true;
            });
            float x = leftGutter;
            for (int glyphIndex = 0; glyphIndex < glyphs.size(); glyphIndex++) {
                NativePreviewGlyph glyph = glyphs.get(glyphIndex);
                if (glyph.sourceIndex() >= 0) {
                    PreviewSourcePoint source = sources.get(glyph.sourceIndex());
                    JsonObject output = outputFragments.get(source.id());
                    JsonObject range = new JsonObject();
                    range.addProperty("start", source.start());
                    range.addProperty("end", source.end());
                    range.addProperty("x", x);
                    range.addProperty("y", line * font.lineHeight + topGutter);
                    range.addProperty("width", glyph.advance());
                    range.addProperty("height", font.lineHeight);
                    range.addProperty("line", line);
                    output.getAsJsonArray("ranges").add(range);
                    int direction = Character.getDirectionality(glyph.codePoint());
                    boolean reverse = direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
                    if (glyphIndex + 1 < glyphs.size() && Math.abs(glyphs.get(glyphIndex + 1).sourceIndex() - glyph.sourceIndex()) == 1) reverse = glyphs.get(glyphIndex + 1).sourceIndex() < glyph.sourceIndex();
                    else if (glyphIndex > 0 && Math.abs(glyphs.get(glyphIndex - 1).sourceIndex() - glyph.sourceIndex()) == 1) reverse = glyphs.get(glyphIndex - 1).sourceIndex() > glyph.sourceIndex();
                    int leading = reverse ? source.end() : source.start();
                    int trailing = reverse ? source.start() : source.end();
                    int textLength = output.get("plainText").getAsString().length();
                    if (!source.atomic() || leading == 0 || leading == textLength) addCaret(output, seenCarets, source.id(), leading, x, line, font.lineHeight, "leading");
                    if (!source.atomic() || trailing == 0 || trailing == textLength) addCaret(output, seenCarets, source.id(), trailing, x + glyph.advance(), line, font.lineHeight, "trailing");
                }
                x += glyph.advance();
            }
            widest[0] = Math.max(widest[0], x);
        }
        fillCarets(outputFragments, editableIds, seenCarets, font.lineHeight);
        for (JsonObject fragment : outputFragments.values()) {
            for (JsonElement element : fragment.getAsJsonArray("carets")) {
                JsonObject caret = element.getAsJsonObject();
                caret.addProperty("y", caret.get("y").getAsInt() + topGutter);
                if (caret.get("x").getAsFloat() == 0) caret.addProperty("x", leftGutter);
            }
        }
        int width = Math.min(wrapWidth, Math.max(leftGutter + rightGutter, (int) Math.ceil(widest[0]) + rightGutter));
        int totalHeight = lines.size() * font.lineHeight + topGutter + bottomGutter;
        viewportTop = Math.min(viewportTop, Math.max(0, totalHeight - 1));
        int height = Math.max(1, Math.min(viewportHeight, totalHeight - viewportTop));
        JsonObject response = new JsonObject();
        response.addProperty("assetRevision", QuestEditorAssets.revision());
        copyRequestId(request, response);
        response.addProperty("width", width);
        response.addProperty("height", height);
        response.addProperty("totalHeight", totalHeight);
        response.addProperty("viewportTop", viewportTop);
        response.addProperty("lineHeight", font.lineHeight);
        JsonArray output = new JsonArray();
        outputFragments.values().forEach(output::add);
        response.add("fragments", output);
        JsonArray warnings = new JsonArray();
        if (-bearings[0] >= leftGutter || bearings[1] >= rightGutter || -bearings[2] >= topGutter || bearings[3] - font.lineHeight >= bottomGutter) warnings.add("Some glyphs extend beyond the available preview viewport.");
        response.add("warnings", warnings);
        return new NativePreviewRenderer().draw(font, lines, width, height, viewportTop, scale, leftGutter, topGutter)
                .thenApply(image -> new NativePreviewFrame(response, image));
    }

    private static byte[] png(NativeImage image) throws IOException {
        BufferedImage bitmap = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0, 0, image.getWidth(), image.getHeight(), image.getPixels(), 0, image.getWidth());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(bitmap, "png", output);
            return output.toByteArray();
        }
    }

    private static void addCaret(JsonObject fragment, Map<String, Set<String>> seen, String id, int offset, float x, int line, int lineHeight, String affinity) {
        if (!seen.computeIfAbsent(id, ignored -> new HashSet<>()).add(offset + ":" + line + ":" + x + ":" + affinity)) return;
        JsonObject caret = new JsonObject();
        caret.addProperty("offset", offset);
        caret.addProperty("x", x);
        caret.addProperty("y", line * lineHeight);
        caret.addProperty("line", line);
        caret.addProperty("affinity", affinity);
        fragment.getAsJsonArray("carets").add(caret);
    }

    private static void fillCarets(Map<String, JsonObject> fragments, Set<String> editable, Map<String, Set<String>> seen, int lineHeight) {
        JsonObject previousCaret = null;
        for (JsonObject fragment : fragments.values()) {
            String id = fragment.get("id").getAsString();
            JsonArray carets = fragment.getAsJsonArray("carets");
            String text = fragment.get("plainText").getAsString();
            if (carets.isEmpty()) {
                addCaret(fragment, seen, id, 0, previousCaret == null ? 0 : previousCaret.get("x").getAsFloat(), previousCaret == null ? 0 : previousCaret.get("line").getAsInt(), lineHeight, "leading");
            }
            if (editable.contains(id)) {
                Map<Integer, JsonObject> positions = new HashMap<>();
                for (JsonElement element : carets) positions.put(element.getAsJsonObject().get("offset").getAsInt(), element.getAsJsonObject());
                for (int offset = 0; offset <= text.length();) {
                    JsonObject existing = positions.get(offset);
                    if (existing == null) {
                        JsonObject nearest = null;
                        for (int before = offset - 1; before >= 0 && nearest == null; before--) nearest = positions.get(before);
                        if (nearest == null) {
                            if (previousCaret != null) nearest = previousCaret;
                            else {
                                int firstOffset = positions.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
                                JsonObject first = positions.get(firstOffset);
                                if (first == null) first = carets.get(0).getAsJsonObject();
                                int leadingLines = (int) text.substring(0, Math.min(firstOffset, text.length())).chars().filter(character -> character == '\n').count();
                                nearest = first.deepCopy();
                                nearest.addProperty("line", Math.max(0, first.get("line").getAsInt() - leadingLines));
                                if (leadingLines > 0) nearest.addProperty("x", 0);
                            }
                        }
                        boolean newline = offset > 0 && text.charAt(offset - 1) == '\n';
                        addCaret(fragment, seen, id, offset, newline ? 0 : nearest.get("x").getAsFloat(), nearest.get("line").getAsInt() + (newline ? 1 : 0), lineHeight, "leading");
                        positions.put(offset, carets.get(carets.size() - 1).getAsJsonObject());
                    }
                    if (offset == text.length()) break;
                    offset += Character.charCount(text.codePointAt(offset));
                }
            }
            for (JsonElement element : carets) {
                JsonObject caret = element.getAsJsonObject();
                if (previousCaret == null || caret.get("offset").getAsInt() == text.length()) previousCaret = caret;
            }
        }
    }

    private static void copyRequestId(JsonObject request, JsonObject response) {
        response.add("requestId", request.has("requestId") ? request.get("requestId").deepCopy() : new JsonPrimitive(0));
    }

    private static void unpremultiply(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                int alpha = pixel >>> 24;
                if (alpha == 0 || alpha == 255) continue;
                int red = Math.min(255, (pixel & 255) * 255 / alpha);
                int green = Math.min(255, (pixel >> 8 & 255) * 255 / alpha);
                int blue = Math.min(255, (pixel >> 16 & 255) * 255 / alpha);
                image.setPixel(x, y, alpha << 24 | blue << 16 | green << 8 | red);
            }
        }
    }

    private static boolean hasObfuscated(JsonElement element) {
        if (element == null) return false;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("obfuscated") && object.get("obfuscated").isJsonPrimitive() && object.get("obfuscated").getAsBoolean()) return true;
            return object.entrySet().stream().anyMatch(entry -> hasObfuscated(entry.getValue()));
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) if (hasObfuscated(child)) return true;
        }
        return false;
    }

    private static int integer(JsonObject object, String name, int fallback, int minimum, int maximum) {
        if (!object.has(name)) return fallback;
        double value = object.get(name).getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum) throw new IllegalArgumentException("Invalid " + name + " dimension.");
        return (int) value;
    }

    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback;
    }

    private static void checkDepth(JsonElement element, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Component nesting exceeds 64 levels.");
        if (element.isJsonObject()) element.getAsJsonObject().entrySet().forEach(entry -> checkDepth(entry.getValue(), depth + 1));
        else if (element.isJsonArray()) element.getAsJsonArray().forEach(child -> checkDepth(child, depth + 1));
    }
}
