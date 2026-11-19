package com.byteup.jei.pack;

import com.byteup.jei.ByteUpJEI;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Executors;

/** Tiny embedded HTTP server so the one-plugin resource pack can be downloaded by vanilla clients. */
public final class PackHttpServer {
    private final ByteUpJEI plugin;
    private HttpServer server;

    public PackHttpServer(ByteUpJEI plugin) { this.plugin = plugin; }

    public void start() throws IOException {
        if (server != null) return;
        int port = plugin.getConfig().getInt("resource-pack.port", 8767);
        String bind = plugin.getConfig().getString("resource-pack.bind", "0.0.0.0");
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/byteup-jei.zip", this::handle);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "ByteUP-JEI-PackHTTP");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        plugin.getLogger().info("Servidor interno do resource pack ativo em " + bind + ":" + port);
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET") && !exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        if (plugin.pack() == null || !Files.isRegularFile(plugin.pack().file())) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        byte[] data = Files.readAllBytes(plugin.pack().file());
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=byteup-jei.zip");
        exchange.getResponseHeaders().set("ETag", '"' + plugin.pack().sha1Hex() + '"');
        if (exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(data.length));
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        }
        exchange.close();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
