package dev.veryue.vuequests.editor;

import net.minecraft.server.MinecraftServer;
import dev.veryue.vuequests.editor.api.QuestApiHttpHandler;
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
import static dev.veryue.vuequests.editor.QuestEditorConstants.API_CONTEXTS;

public class QuestEditorServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static HttpServer server;
    private static ExecutorService executor;

    public static synchronized boolean start(MinecraftServer minecraftServer) {
        if (minecraftServer.isDedicatedServer()) return false;
        if (server != null) return true;
        String host = System.getProperty(MOD_ID + ".editor.host", "127.0.0.1");
        try {
            QuestEditorAssets.discoverRoots();
            QuestEditorResources.start(minecraftServer);
            int port = Integer.parseInt(System.getProperty(MOD_ID + ".editor.port", "6700"));
            start(host, port, new QuestEditorService(minecraftServer));
            LOGGER.info("Quest editor available at {}", getUrl());
            return true;
        } catch (IOException | RuntimeException exception) {
            stop();
            LOGGER.error("Could not start the quest editor HTTP server", exception);
            return false;
        }
    }

    public static synchronized boolean isRunning() {
        return server != null;
    }

    public static synchronized String getUrl() {
        if (server == null) throw new IllegalStateException("The quest editor is not running.");
        InetSocketAddress address = server.getAddress();
        String host = address.getAddress().isAnyLocalAddress() ? "localhost" : address.getHostString();
        if (host.contains(":")) host = "[" + host + "]";
        return "http://" + host + ":" + address.getPort() + "/";
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
            QuestApiHttpHandler api = new QuestApiHttpHandler(service.api());
            for (String route : API_CONTEXTS) {
                listener.createContext(route, api);
            }
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
        QuestEditorResources.stop();
    }
}
