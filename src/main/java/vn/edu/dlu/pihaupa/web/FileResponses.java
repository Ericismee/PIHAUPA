package vn.edu.dlu.pihaupa.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.file.*;

/** File adapter streams downloads with bounded buffering. */
public final class FileResponses {

  private FileResponses() {}

  public static void send(HttpExchange x, String type, Path file) throws IOException {
    x.getResponseHeaders().set("Content-Type", type);
    x.getResponseHeaders().set("Cache-Control", "no-store");
    x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    x.sendResponseHeaders(200, Files.size(file));
    try (var out = x.getResponseBody()) {
      Files.copy(file, out);
    }
  }
}
