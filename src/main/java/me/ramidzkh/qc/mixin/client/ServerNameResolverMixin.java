package me.ramidzkh.qc.mixin.client;

import me.ramidzkh.qc.client.DNSLookup;
import me.ramidzkh.qc.client.ServerAddressProperties;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerNameResolver.class)
public class ServerNameResolverMixin {

    @Shadow
    @Final
    private ServerRedirectHandler redirectHandler;

    @Redirect(method = "resolveAddress", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/resolver/ServerNameResolver;redirectHandler:Lnet/minecraft/client/multiplayer/resolver/ServerRedirectHandler;"))
    private ServerRedirectHandler getRedirectHandler(ServerNameResolver self, ServerAddress serverAddress) {
        return switch (((ServerAddressProperties) (Object) serverAddress).getQuicTier()) {
            case QUIC_ONLY -> DNSLookup.INSTANCE;
            case QUIC_PREFERRED -> a -> DNSLookup.INSTANCE.lookupRedirect(a)
                    .or(() -> redirectHandler.lookupRedirect(a));
            case QUIC_AFTER -> a -> redirectHandler.lookupRedirect(a)
                    .or(() -> DNSLookup.INSTANCE.lookupRedirect(a));
            case VANILLA -> redirectHandler;
        };
    }
}
