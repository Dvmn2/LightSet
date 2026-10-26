package net.dvmn2.lightsetplugin.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic file replacement helper. Thread safe (stateless).
 */
final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Writes bytes to a temporary file next to the target and moves it over the target.
     *
     * @param target destination file
     * @param bytes  content
     * @throws IOException when the file cannot be written or moved
     */
    static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
