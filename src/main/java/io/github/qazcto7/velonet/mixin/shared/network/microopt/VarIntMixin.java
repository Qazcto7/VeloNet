package io.github.qazcto7.velonet.mixin.shared.network.microopt;

import io.netty.buffer.ByteBuf;
import io.github.qazcto7.velonet.mod.shared.network.util.VarIntUtil;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VarInt.class)
public class VarIntMixin {
    @Overwrite
    public static int getByteSize(int value) {
        return VarIntUtil.getVarIntLength(value);
    }

    @Overwrite
    public static ByteBuf write(ByteBuf output, int value) {
        if ((value & 0xFFFFFF80) == 0) {
            output.writeByte(value);
        } else if ((value & 0xFFFFC000) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            output.writeShort(w);
        } else {
            writeVarIntFull(output, value);
        }
        return output;
    }

    @Unique
    private static void writeVarIntFull(ByteBuf output, int value) {
        if ((value & 0xFFE00000) == 0) {
            int w = (value & 0x7F | 0x80) << 16 | (value >>> 7 & 0x7F | 0x80) << 8 | (value >>> 14);
            output.writeMedium(w);
        } else if ((value & 0xF0000000) == 0) {
            int w = (value & 0x7F | 0x80) << 24 | (value >>> 7 & 0x7F | 0x80) << 16 | (value >>> 14 & 0x7F | 0x80) << 8 | (value >>> 21);
            output.writeInt(w);
        } else {
            int w = (value & 0x7F | 0x80) << 24 | (value >>> 7 & 0x7F | 0x80) << 16 | (value >>> 14 & 0x7F | 0x80) << 8 | (value >>> 21 & 0x7F | 0x80);
            output.writeInt(w);
            output.writeByte(value >>> 28);
        }
    }
}
