package dev.veryue.vuequests.editor;

import net.minecraft.server.MinecraftServer;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestEditorServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static HttpServer server;
    private static ExecutorService executor;

    public static void start(MinecraftServer minecraftServer) {
        String host = System.getProperty(MOD_ID + ".editor.host", "127.0.0.1");
        try {
            QuestEditorAssets.discoverRoots();
            int port = Integer.parseInt(System.getProperty(MOD_ID + ".editor.port", "6700"));
            int boundPort = start(host, port, new QuestEditorService(minecraftServer));
            LOGGER.info("Quest editor available at http://{}:{}/", host, boundPort);
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not start the quest editor HTTP server", exception);
        }
    }

    static synchronized int start(String host, int port, QuestEditorService service) throws IOException {
        if (server != null) return server.getAddress().getPort();
        if (QuestEditorHttpHandler.class.getResource(QuestEditorHttpHandler.RESOURCE_ROOT + "index.html") == null) {
            throw new FileNotFoundException("The bundled quest editor index.html is missing.");
        }
        HttpServer listener = HttpServer.create(new InetSocketAddress(host, port), 0);
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            listener.createContext("/", new QuestEditorHttpHandler(service));
            listener.setExecutor(workers);
            listener.start();
        } catch (RuntimeException exception) {
            listener.stop(0);
            workers.shutdownNow();
            throw exception;
        }
        server = listener;
        executor = workers;
        return server.getAddress().getPort();
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
            server = null;
            executor = null;
        }
        QuestEditorAssets.setRoots(List.of());
    }
}
