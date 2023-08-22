package me.ramidzkh.qc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.ramidzkh.qc.server.GameProfileCodec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GameProfileCodecTests {

    // produce extremely long random GameProfiles and test them
    @RepeatedTest(10)
    public void test() {
        var random = ThreadLocalRandom.current();
        var profile = new GameProfile(UUID.randomUUID(), Long.toHexString(random.nextLong()));

        while (random.nextInt(3) != 0) {
            var name = String.join("", Collections.nCopies(random.nextInt(Short.MAX_VALUE), "a"));
            var value = String.join("", Collections.nCopies(random.nextInt(Short.MAX_VALUE), "b"));
            var signature = random.nextBoolean()
                    ? String.join("", Collections.nCopies(random.nextInt(Short.MAX_VALUE), "c"))
                    : null;
            profile.getProperties().put(name, new Property(name, value, signature));
        }

        var write = GameProfileCodec.write(profile);
        Assertions.assertEquals(profile, GameProfileCodec.read(ByteBuffer.wrap(write)));
    }
}
