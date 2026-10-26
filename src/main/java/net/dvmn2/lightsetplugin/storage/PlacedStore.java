package net.dvmn2.lightsetplugin.storage;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes {@code placed.bin}, the registry of light blocks the plugin has placed.
 *
 * <p>The registry lets the plugin find and remove light blocks left behind after a crash.
 * Format (gzip-compressed, big-endian, {@link java.io.DataOutputStream} encoding):
 * <pre>
 * int     magic          0x4C535043
 * int     version        1
 * int     worldCount     0..1024
 * repeat worldCount:
 *   UTF   world          world name
 *   int   count          0..10000000
 *   long  position[count] packed block positions (see Positions)
 * </pre>
 * {@link #load()} runs on the main thread in {@code onEnable}; {@link #write(long, Map)} is thread safe.
 */
public final class PlacedStore {

    /** Version of the {@code placed.bin} format written by this class. */
    public static final int FORMAT_VERSION = 1;

    private static final String FILE_NAME = "placed.bin";
    private static final int MAGIC = 0x4C535043;
    private static final int MAX_WORLDS = 1024;
    private static final int MAX_POSITIONS_PER_WORLD = 10_000_000;

    private final Path file;
    private final Logger log;
    private final LatestWriter writer;

    /**
     * Creates a store.
     *
     * @param dataFolder plugin data folder
     * @param log        plugin logger
     */
    public PlacedStore(Path dataFolder, Logger log) {
        this.file = dataFolder.resolve(FILE_NAME);
        this.log = log;
        this.writer = new LatestWriter(file, log);
    }

    /**
     * Loads the registry. An unreadable file is renamed to {@code placed.bin.corrupt}.
     *
     * @return world name to packed positions, never {@code null}
     */
    public Map<String, long[]> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            return read(in);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to read " + FILE_NAME + "; it will be kept as " + FILE_NAME + ".corrupt", ex);
            quarantine();
            return Map.of();
        }
    }

    /** Takes the ticket for the next snapshot. Call on the main thread before {@link #write(long, Map)}. */
    public long nextTicket() {
        return writer.nextTicket();
    }

    /**
     * Writes a snapshot atomically. Safe to call from any thread.
     *
     * @param ticket   ticket from {@link #nextTicket()}; an older snapshot never replaces a newer one
     * @param snapshot world name to packed positions; the arrays MUST NOT change while this runs
     */
    public void write(long ticket, Map<String, long[]> snapshot) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(snapshot.size());
            for (Map.Entry<String, long[]> entry : snapshot.entrySet()) {
                out.writeUTF(entry.getKey());
                long[] positions = entry.getValue();
                out.writeInt(positions.length);
                for (long position : positions) {
                    out.writeLong(position);
                }
            }
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to encode " + FILE_NAME, ex);
            return;
        }
        writer.write(ticket, bytes.toByteArray());
    }

    private Map<String, long[]> read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a " + FILE_NAME + " file (bad magic number)");
        }
        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported " + FILE_NAME + " version " + version);
        }
        int worldCount = in.readInt();
        if (worldCount < 0 || worldCount > MAX_WORLDS) {
            throw new IOException("Invalid world count " + worldCount);
        }
        Map<String, long[]> result = new HashMap<>();
        for (int i = 0; i < worldCount; i++) {
            String world = in.readUTF();
            int count = in.readInt();
            if (count < 0 || count > MAX_POSITIONS_PER_WORLD) {
                throw new IOException("Invalid position count " + count + " for world " + world);
            }
            long[] positions = new long[count];
            for (int j = 0; j < count; j++) {
                positions[j] = in.readLong();
            }
            result.put(world, positions);
        }
        return result;
    }

    private void quarantine() {
        try {
            Files.move(file, file.resolveSibling(FILE_NAME + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to move the unreadable " + FILE_NAME + " aside", ex);
        }
    }
}
