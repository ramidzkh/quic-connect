package me.ramidzkh.qc.mixin.client;

import me.ramidzkh.qc.client.QuicTier;
import me.ramidzkh.qc.client.ServerAddressProperties;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerAddress.class)
public class ServerAddressMixin implements ServerAddressProperties {

    @Shadow
    @Final
    private static ServerAddress INVALID;

    @Unique
    private QuicTier quicTier = QuicTier.QUIC_PREFERRED;

    @Inject(method = "parseString", at = @At("HEAD"), cancellable = true)
    private static void parse(String address, CallbackInfoReturnable<ServerAddress> callbackInfoReturnable) {
        var index = address.indexOf("://");

        if (index == -1) {
            return;
        }

        var scheme = address.substring(0, index);
        var newAddress = ServerAddress.parseString(address.substring(index + 3));

        if (newAddress != INVALID) {
            switch (scheme) {
                case "minecraft":
                    ((ServerAddressProperties) (Object) newAddress).setQuicTier(QuicTier.VANILLA);
                    break;
                case "quic":
                    ((ServerAddressProperties) (Object) newAddress).setQuicTier(QuicTier.QUIC_ONLY);
                default:
                    // TODO: API?
                    break;
            }
        }

        callbackInfoReturnable.setReturnValue(newAddress);
    }

    @ModifyVariable(method = "isValidAddress", at = @At("HEAD"), argsOnly = true, index = 0)
    private static String modify(String address) {
        var index = address.indexOf("://");

        if (index == -1) {
            return address;
        } else {
            return address.substring(index + 3);
        }
    }

    @Override
    public QuicTier getQuicTier() {
        return quicTier;
    }

    @Override
    public void setQuicTier(QuicTier quicTier) {
        this.quicTier = quicTier;
    }
}
