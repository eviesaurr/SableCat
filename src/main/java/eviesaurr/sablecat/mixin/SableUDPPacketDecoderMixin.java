package eviesaurr.sablecat.mixin;

import eviesaurr.sablecat.SableCat;
import eviesaurr.sablecat.fix.FixRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "dev.ryanhcode.sable.network.udp.SableUDPPacketDecoder", remap = false)
public class SableUDPPacketDecoderMixin {

    private static volatile int sablecat$maxValidPacketId = -1;

    @Inject(
        method = "decode",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void sablecat$skipInvalidPacket(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("udp-invalid-packet-guard")) {
            return;
        }

        try {
            ByteBuf buf = msg.content();
            if (buf.readableBytes() < 1) {
                return;
            }

            int packetId = buf.getUnsignedByte(buf.readerIndex());
            int maxValid = sablecat$getMaxValidPacketId();
            if (packetId > maxValid) {
                ci.cancel();
            }
        } catch (Throwable t) {
            SableCat.LOGGER.debug("UDP invalid packet guard: failed to inspect packet, falling through to vanilla decode", t);
        }
    }

    private static int sablecat$getMaxValidPacketId() {
        int cached = sablecat$maxValidPacketId;
        if (cached >= 0) {
            return cached;
        }
        synchronized (SableUDPPacketDecoderMixin.class) {
            cached = sablecat$maxValidPacketId;
            if (cached >= 0) {
                return cached;
            }
            try {
                Class<?> enumClass = Class.forName("dev.ryanhcode.sable.network.udp.SableUDPPacketType");
                java.lang.reflect.Field valuesField = enumClass.getDeclaredField("VALUES");
                valuesField.setAccessible(true);
                Object[] values = (Object[]) valuesField.get(null);
                cached = values.length - 1;
            } catch (Throwable t) {
                SableCat.LOGGER.warn("UDP invalid packet guard: failed to read SableUDPPacketType.VALUES, falling back to hardcoded limit 5", t);
                cached = 5;
            }
            sablecat$maxValidPacketId = cached;
            return cached;
        }
    }
}
