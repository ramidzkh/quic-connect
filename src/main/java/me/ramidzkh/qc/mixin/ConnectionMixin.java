package me.ramidzkh.qc.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.ramidzkh.qc.client.QuicConnection;
import me.ramidzkh.qc.client.QuicSocketAddress;
import me.ramidzkh.qc.client.QuicTier;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Shadow
    private Channel channel;

    @Shadow
    private boolean encrypted;

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

    @Redirect(method = "disconnect", at = @At(value = "FIELD", target = "Lnet/minecraft/network/Connection;channel:Lio/netty/channel/Channel;"))
    private Channel getChannelForDisconnect(Connection self) {
        if (channel instanceof QuicStreamChannel quic) {
            return quic.parent();
        } else {
            return channel;
        }
    }

    @Inject(method = "setEncryptionKey", at = @At("HEAD"), cancellable = true)
    private void onSetEncryptionKey(CallbackInfo callbackInfo) {
        if (encrypted) {
            callbackInfo.cancel();
        }
    }
}
