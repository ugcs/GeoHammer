package com.ugcs.geohammer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceTest {

    @Test
    void deliversFilesToRunningInstance(@TempDir Path dir) throws IOException {
        Path file = Files.createFile(dir.resolve("data.geohammer"));
        List<File> opened = Collections.synchronizedList(new ArrayList<>());
        int port = SingleInstance.listenOnLoopback(opened::addAll);

        assertTrue(SingleInstance.sendFilesTo(port, new String[] {file.toString()}));
        assertEquals(List.of(file.toFile()), opened);
    }

    @Test
    void reportsFailureWhenRequestIsNotConfirmed() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            startSilentServer(server);

            assertFalse(SingleInstance.sendFilesTo(server.getLocalPort(), new String[] {"data.geohammer"}));
        }
    }

    @Test
    void reportsFailureWhenNobodyListens() throws IOException {
        int port;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }

        assertFalse(SingleInstance.sendFilesTo(port, new String[] {"data.geohammer"}));
    }

    private static void startSilentServer(ServerSocket server) {
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                socket.getInputStream().read();
            } catch (IOException e) {
                // the server is closed by the test
            }
        }, "silent-server");
        thread.setDaemon(true);
        thread.start();
    }
}
