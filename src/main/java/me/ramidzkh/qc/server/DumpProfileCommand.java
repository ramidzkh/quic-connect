package me.ramidzkh.qc.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HexFormat;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DumpProfileCommand {

    private static final HexFormat FORMAT = HexFormat.of().withDelimiter(":").withUpperCase();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("quic-connect")
                .requires(source -> source.hasPermission(1))
                .then(literal("dump-profile")
                        .then(argument("players", EntityArgument.players())
                                .executes(context -> {
                                    for (var player : EntityArgument.getPlayers(context, "players")) {
                                        context.getSource().sendSystemMessage(build(player));
                                    }

                                    return 0;
                                }))));
    }

    private static Component build(ServerPlayer player) {
        var config = GameProfileCodec.OID + " = DER:"
                + FORMAT.formatHex(GameProfileCodec.write(player.getGameProfile()));
        return Component.literal(player.getStringUUID() + ": ").append(player.getDisplayName())
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, config)));
    }
}
