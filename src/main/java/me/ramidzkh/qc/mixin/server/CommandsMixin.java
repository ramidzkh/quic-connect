package me.ramidzkh.qc.mixin.server;

import com.mojang.brigadier.CommandDispatcher;
import me.ramidzkh.qc.server.DumpProfileCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Shadow
    public abstract CommandDispatcher<CommandSourceStack> getDispatcher();

    @Inject(at = @At("RETURN"), method = "<init>")
    private void addCommand(CallbackInfo callbackInfo) {
        DumpProfileCommand.register(getDispatcher());
    }
}
