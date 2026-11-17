package piHAUPA;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Open transaction files without extracting bundled gzip data to disk. */
public final class TransactionFiles {
    private TransactionFiles() {}

    public static boolean isCompressed(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("transactions.txt.gz");
    }

    public static InputStream open(Path file) throws IOException {
        var stream = new BufferedInputStream(Files.newInputStream(file), 65536);
        if (!isCompressed(file)) return stream;
        try {
            return new GZIPInputStream(stream, 65536);
        } catch (IOException e) {
            stream.close();
            throw e;
        }
    }

    public static BufferedReader reader(Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(open(file), StandardCharsets.UTF_8.newDecoder()), 65536);
    }

    /** Bundled gzip files have one member and fewer than 4 GiB of decoded data. */
    public static long decodedSize(Path file) throws IOException {
        if (!isCompressed(file)) return Files.size(file);
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < 18) return 0;
            channel.position(size - 4);
            var trailer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            while (trailer.hasRemaining()) {
                if (channel.read(trailer) == -1) return 0;
            }
            return Integer.toUnsignedLong(trailer.getInt(0));
        }
    }
}
