package me.ramidzkh.qc.mixin.client;

import me.ramidzkh.qc.client.QuicSocketAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(ServerAddressResolver.class)
public interface ServerAddressResolverMixin {

    @Redirect(method = "method_36903", at = @At(value = "NEW", target = "(Ljava/net/InetAddress;I)Ljava/net/InetSocketAddress;"))
    private static InetSocketAddress capture(InetAddress addr, int port, ServerAddress serverAddress) {
        return new QuicSocketAddress(addr, port, serverAddress);
    }
}
