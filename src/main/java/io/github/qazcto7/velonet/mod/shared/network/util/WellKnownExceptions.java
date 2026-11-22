package io.github.qazcto7.velonet.mod.shared.network.util;

public final class WellKnownExceptions {
    public static final QuietDecoderException BAD_LENGTH_CACHED = new QuietDecoderException("Bad packet length");
    public static final QuietDecoderException FRAME_TOO_LARGE_CACHED = new QuietDecoderException("Frame length exceeds allowed maximum");
    public static final QuietDecoderException VARINT_BIG_CACHED = new QuietDecoderException("VarInt too big");
    public static final QuietDecoderException ZERO_LENGTH_CACHED = new QuietDecoderException("Frame length cannot be zero");

    private WellKnownExceptions() {
    }
}
