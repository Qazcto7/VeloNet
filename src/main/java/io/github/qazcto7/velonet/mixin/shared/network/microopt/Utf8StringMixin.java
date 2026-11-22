package io.github.qazcto7.velonet.mixin.shared.network.microopt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Utf8String.class)
public class Utf8StringMixin {
    @Unique
    private static final int MAX_SAFE_UTF8_LENGTH = Integer.MAX_VALUE / 3;

    @Overwrite
    public static void write(ByteBuf output, CharSequence value, int maxLength) {
        if (value.length() > maxLength) {
            throw new EncoderException("String too big (was " + value.length() + " characters, max " + maxLength + ")");
        }
        int utf8Bytes = ByteBufUtil.utf8Bytes(value);
        if (maxLength <= MAX_SAFE_UTF8_LENGTH) {
            int maxAllowed = ByteBufUtil.utf8MaxBytes(maxLength);
            if (utf8Bytes > maxAllowed) {
                throw new EncoderException("String too big (was " + utf8Bytes + " bytes encoded, max " + maxAllowed + ")");
            }
        }
        VarInt.write(output, utf8Bytes);
        ByteBufUtil.writeUtf8(output, value);
    }
}
