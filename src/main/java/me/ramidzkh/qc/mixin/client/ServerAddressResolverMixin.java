package me.ramidzkh.qc.mixin.client;

import me.ramidzkh.qc.client.QuicSocketAddress;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ServerAddressResolver.class)
public interface ServerAddressResolverMixin {

    @Inject(method = "method_36903", at = @At("RETURN"), cancellable = true)
    private static void capture(ServerAddress serverAddress,
            CallbackInfoReturnable<Optional<ResolvedServerAddress>> callbackInfoReturnable) {
        callbackInfoReturnable.setReturnValue(callbackInfoReturnable.getReturnValue()
                .map(x -> ResolvedServerAddress.from(
                        new QuicSocketAddress(x.asInetSocketAddress().getAddress(), x.getPort(), serverAddress))));
    }
}
