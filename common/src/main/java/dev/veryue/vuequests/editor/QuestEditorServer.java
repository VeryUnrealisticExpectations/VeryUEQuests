package dev.veryue.vuequests.editor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static dev.veryue.vuequests.VeryUEConstants.MOD_ID;

public class QuestEditorServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final QuestEditorHttpServer SERVER = new QuestEditorHttpServer();

    public static void start() {
        String host = System.getProperty(MOD_ID + ".editor.host", "127.0.0.1");
        try {
            int port = Integer.parseInt(System.getProperty(MOD_ID + ".editor.port", "6700"));
            int boundPort = SERVER.start(host, port);
            LOGGER.info("Quest editor available at http://{}:{}/", host, boundPort);
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not start the quest editor HTTP server", exception);
        }
    }

    public static void stop() {
        SERVER.close();
    }
}
