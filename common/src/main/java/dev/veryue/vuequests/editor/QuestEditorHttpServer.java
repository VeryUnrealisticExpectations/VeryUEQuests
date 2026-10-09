package dev.veryue.vuequests.editor;

import com.sun.net.httpserver.HttpServer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuestEditorHttpServer implements AutoCloseable {

    private HttpServer server;
    private ExecutorService executor;

    public synchronized int start(String host, int port) throws IOException {
        if (server != null) {
            return server.getAddress().getPort();
        }
        if (QuestEditorHttpHandler.class.getResource(QuestEditorHttpHandler.RESOURCE_ROOT + "index.html") == null) {
            throw new FileNotFoundException("The bundled quest editor index.html is missing.");
        }

        HttpServer newServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        ExecutorService newExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            newServer.createContext("/", new QuestEditorHttpHandler());
            newServer.setExecutor(newExecutor);
            newServer.start();
        } catch (RuntimeException exception) {
            newServer.stop(0);
            newExecutor.shutdownNow();
            throw exception;
        }
        server = newServer;
        executor = newExecutor;
        return server.getAddress().getPort();
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
            server = null;
            executor = null;
        }
    }
}
