package net.dvmn2.lightsetplugin.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes snapshots of one file so that an older snapshot never overwrites a newer one.
 *
 * <p>Thread safe: tickets are taken on the main thread in snapshot order, writes may run on any thread.
 */
final class LatestWriter {

    private final Path target;
    private final Logger log;
    private final AtomicLong tickets = new AtomicLong();
    private final Object lock = new Object();
    private long lastWritten;

    LatestWriter(Path target, Logger log) {
        this.target = target;
        this.log = log;
    }

    /** Takes the ticket for a snapshot that is about to be written. */
    long nextTicket() {
        return tickets.incrementAndGet();
    }

    /** Writes bytes unless a newer snapshot has already been written. */
    void write(long ticket, byte[] bytes) {
        synchronized (lock) {
            if (ticket < lastWritten) {
                return;
            }
            lastWritten = ticket;
            try {
                AtomicFiles.writeAtomically(target, bytes);
            } catch (IOException ex) {
                log.log(Level.SEVERE, "Failed to save " + target.getFileName(), ex);
            }
        }
    }
}
