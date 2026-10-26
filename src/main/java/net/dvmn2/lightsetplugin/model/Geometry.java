package net.dvmn2.lightsetplugin.model;

/**
 * Result of one geometry calculation of a source: where light blocks go and how strong each is.
 *
 * <p>The arrays are owned by the record and MUST NOT be modified after construction.
 *
 * @param positions  packed positions of air blocks that receive light
 * @param factors    brightness factor {@code 0..1} for each position (same length as {@code positions})
 * @param beamLength length of the visible particle beam, blocks
 * @param truncated  whether the calculation hit an unloaded chunk and is incomplete
 */
public record Geometry(long[] positions, float[] factors, double beamLength, boolean truncated) {

    /** Geometry with no light blocks. */
    public static final Geometry EMPTY = new Geometry(new long[0], new float[0], 0.0, false);
}
