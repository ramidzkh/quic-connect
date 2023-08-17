package me.ramidzkh.qc.client;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Optional;

// Copied from ServerRedirectHandler - class_6371
@Environment(value = EnvType.CLIENT)
public class DNSLookup implements ServerRedirectHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final DirContext context;

    public static final ServerRedirectHandler INSTANCE = createDnsHandler();

    private DNSLookup(DirContext context) {
        this.context = context;
    }

    @Override
    public @NotNull Optional<ServerAddress> lookupRedirect(ServerAddress address) {
        if (address.getPort() == 25565) {
            try {
                var attributes = context.getAttributes("_quic_connect._udp." + address.getHost(),
                        new String[] { "SRV" });
                var attribute = attributes.get("srv");

                if (attribute != null) {
                    var strings = attribute.get().toString().split(" ", 4);
                    var newAddress = new ServerAddress(strings[3], Integer.parseInt(strings[2]));
                    ((ServerAddressProperties) (Object) newAddress)
                            .setQuicTier(((ServerAddressProperties) (Object) address).getQuicTier());
                    return Optional.of(newAddress);
                }
            } catch (Throwable ignored) {
            }
        }

        return Optional.empty();
    }

    private static ServerRedirectHandler createDnsHandler() {
        try {
            var factory = "com.sun.jndi.dns.DnsContextFactory";
            Class.forName(factory);
            var hashtable = new Hashtable<String, String>();
            hashtable.put("java.naming.factory.initial", factory);
            hashtable.put("java.naming.provider.url", "dns:");
            hashtable.put("com.sun.jndi.dns.timeout.retries", "1");
            return new DNSLookup(new InitialDirContext(hashtable));
        } catch (Throwable throwable) {
            LOGGER.error("Failed to initialize Quic SRV redirect resolver, some servers might not work", throwable);
            return ServerRedirectHandler.EMPTY;
        }
    }
}
