package dev.veryue.vuequests.editor;

import dev.veryue.vuequests.models.quest.QuestDisplay;
import dev.veryue.vuequests.platform.PlatformHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

public final class QuestEditorAssets {
    private static final String GUI_SPRITE_DIRECTORY = "textures/gui/sprites";
    public static final String QUEST_BACKGROUND_TEXTURE_DIRECTORY = GUI_SPRITE_DIRECTORY + "/" + QuestDisplay.BACKGROUND_SPRITE_FOLDER;
    public static final FileToIdConverter GUI_SPRITE_FILES = new FileToIdConverter(GUI_SPRITE_DIRECTORY, ".png");
    private static final int MAX_RESOURCE_BYTES = 8 * 1024 * 1024;
    private static List<Path> roots = List.of();
    private static List<ResourceLocation> questBackgrounds;
    private static String revision = UUID.randomUUID().toString();
    private static final Cache<Optional<byte[]>> files = new Cache<>(MAX_RESOURCE_BYTES, 1024);
    private static final Cache<List<String>> directories = new Cache<>(2 * 1024 * 1024, 256);

    public static void discoverRoots() {
        var sources = new ArrayList<>(PlatformHelper.INSTANCE.assetRoots());
        // NeoForge keeps vanilla assets in a separate client-extra JAR, outside ModList.
        if (VanillaPackResources.class.getResource("/assets/.mcassetsroot") != null) {
            var vanilla = new VanillaPackResourcesBuilder().pushJarResources().build(
                    new PackLocationInfo("vanilla", Component.literal("Minecraft"), PackSource.BUILT_IN, Optional.empty()));
            vanilla.listRawPaths(PackType.CLIENT_RESOURCES, ResourceLocation.withDefaultNamespace("models"),
                    path -> sources.add(path.getParent().getParent().getParent()));
        }
        setRoots(sources);
    }

    public static synchronized void setRoots(List<Path> sources) {
        roots = sources.stream().distinct().toList();
        invalidate();
    }

    public static synchronized String revision() { return revision; }

    public static synchronized void invalidate() {
        files.clear();
        directories.clear();
        questBackgrounds = null;
        revision = UUID.randomUUID().toString();
    }

    public static synchronized List<ResourceLocation> questBackgrounds() throws IOException {
        if (questBackgrounds != null) return questBackgrounds;
        TreeSet<ResourceLocation> backgrounds = new TreeSet<>();
        for (String namespace : list("assets")) {
            String directoryPath = "assets/" + namespace + "/" + QUEST_BACKGROUND_TEXTURE_DIRECTORY;
            for (Path root : roots) {
                Path directory = root.resolve(directoryPath);
                if (!Files.isDirectory(directory)) continue;
                try (var textures = Files.find(directory, Integer.MAX_VALUE,
                        (file, attributes) -> attributes.isRegularFile() && file.getFileName().toString().endsWith(".png"))) {
                    textures.forEach(file -> {
                        String relative = directory.relativize(file).toString().replace('\\', '/');
                        ResourceLocation texture = ResourceLocation.tryBuild(namespace, QUEST_BACKGROUND_TEXTURE_DIRECTORY + "/" + relative);
                        if (texture != null) backgrounds.add(GUI_SPRITE_FILES.fileToId(texture));
                    });
                }
            }
        }
        questBackgrounds = List.copyOf(backgrounds);
        return questBackgrounds;
    }

    public static synchronized byte[] read(String path) throws IOException {
        validate(path);
        if (!path.startsWith("assets/") || path.split("/").length < 3) return null;
        Optional<byte[]> cached = files.get(path);
        if (cached != null) return cached.orElse(null);
        byte[] data = null;
        for (Path root : roots) {
            Path file = root.resolve(path);
            if (Files.isRegularFile(file)) {
                try (var stream = Files.newInputStream(file)) {
                    data = stream.readNBytes(MAX_RESOURCE_BYTES + 1);
                    if (data.length > MAX_RESOURCE_BYTES) throw new IOException("The resource exceeds the editor's 8 MiB limit.");
                }
                break;
            }
        }
        files.put(path, Optional.ofNullable(data), 128L + path.length() * 2L + (data == null ? 0 : data.length));
        return data;
    }

    public static synchronized List<String> list(String path) throws IOException {
        validate(path);
        if (path.isEmpty()) return List.of("assets");
        List<String> cached = directories.get(path);
        if (cached != null) return cached;
        TreeSet<String> names = new TreeSet<>();
        for (Path root : roots) {
            Path directory = root.resolve(path);
            if (Files.isDirectory(directory)) {
                try (var entries = Files.list(directory)) {
                    entries.forEach(entry -> names.add(entry.getFileName().toString()));
                }
            }
        }
        List<String> result = List.copyOf(names);
        directories.put(path, result, 128L + path.length() * 2L + result.stream().mapToLong(name -> 40L + name.length() * 2L).sum());
        return result;
    }

    public static void validate(String path) {
        if (path.isEmpty()) return;
        if ((!path.equals("assets") && !path.startsWith("assets/")) || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid resource path");
        }
        for (String piece : path.split("/", -1)) {
            if (piece.isEmpty() || piece.equals(".") || piece.equals("..") || piece.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Invalid resource path");
            }
        }
    }

    private static final class Cache<V> {
        private record Entry<V>(V value, long bytes) { }
        private final LinkedHashMap<String, Entry<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
        private final long maximumBytes;
        private final int maximumEntries;
        private long bytes;

        private Cache(long maximumBytes, int maximumEntries) {
            this.maximumBytes = maximumBytes;
            this.maximumEntries = maximumEntries;
        }

        private V get(String key) {
            Entry<V> entry = entries.get(key);
            return entry == null ? null : entry.value();
        }

        private void put(String key, V value, long weight) {
            Entry<V> previous = entries.remove(key);
            if (previous != null) bytes -= previous.bytes();
            if (weight > maximumBytes) return;
            entries.put(key, new Entry<>(value, weight));
            bytes += weight;
            while (bytes > maximumBytes || entries.size() > maximumEntries) bytes -= entries.pollFirstEntry().getValue().bytes();
        }

        private void clear() { entries.clear(); bytes = 0; }
    }
}
