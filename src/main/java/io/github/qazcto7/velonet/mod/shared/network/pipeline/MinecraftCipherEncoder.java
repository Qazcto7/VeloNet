package io.github.qazcto7.velonet.mod.shared.network.pipeline;

import com.google.common.base.Preconditions;
import com.velocitypowered.natives.encryption.VelocityCipher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import io.github.qazcto7.velonet.mod.shared.network.util.PipelineUtil;

public class MinecraftCipherEncoder extends MessageToMessageEncoder<ByteBuf> {
    private final VelocityCipher cipher;

    public MinecraftCipherEncoder(VelocityCipher cipher) {
        this.cipher = Preconditions.checkNotNull(cipher, "cipher");
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (PipelineUtil.dropWriteIfInactive(ctx, msg, promise)) {
            return;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        ByteBuf compatible = PipelineUtil.ensureMutableCompatibleBuffer(ctx.alloc(), this.cipher, msg);
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
}
