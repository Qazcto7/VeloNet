package io.github.qazcto7.velonet.mod.shared.network.pipeline;

import com.google.common.base.Preconditions;
import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

public class MinecraftCipherDecoder extends MessageToMessageDecoder<ByteBuf> {
    private final VelocityCipher cipher;

    public MinecraftCipherDecoder(VelocityCipher cipher) {
        this.cipher = Preconditions.checkNotNull(cipher, "cipher");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!ctx.channel().isActive()) {
            return;
        }
        ByteBuf compatible = MoreByteBufUtils.ensureCompatible(ctx.alloc(), this.cipher, in).slice();
        boolean added = false;
        try {
            this.cipher.process(compatible);
            out.add(compatible);
            added = true;
        } finally {
            if (!added) {
                compatible.release();
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        this.cipher.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        this.cipher.close();
    }
}
