package io.github.qazcto7.velonet.mod.shared.network.util;

import io.netty.handler.codec.DecoderException;

public class QuietDecoderException extends DecoderException {
    public QuietDecoderException(String message) {
        super(message);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
