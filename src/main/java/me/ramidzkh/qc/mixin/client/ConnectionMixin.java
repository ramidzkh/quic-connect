package me.ramidzkh.qc.mixin.client;

import io.netty.channel.ChannelFuture;
import me.ramidzkh.qc.client.QuicConnection;
import me.ramidzkh.qc.client.QuicSocketAddress;
import me.ramidzkh.qc.client.QuicTier;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private static void onConnect(InetSocketAddress address, boolean useNativeTransport, Connection connection,
            CallbackInfoReturnable<ChannelFuture> callbackInfoReturnable)
            throws ExecutionException, InterruptedException {
        if (address instanceof QuicSocketAddress quicAddress) {
            // TODO: QUIC_PREFERRED, QUIC_AFTER failover behaviour
            if (quicAddress.getQuicTier() == QuicTier.QUIC_ONLY) {
                callbackInfoReturnable.setReturnValue(QuicConnection.connect(address, useNativeTransport, connection));
            }
        }
    }
}
