package nl.cofx.top10.random;

import org.apache.commons.math3.random.RandomDataGenerator;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TokenGenerator {

    private final RandomDataGenerator randomDataGenerator = new RandomDataGenerator();

    public String generateToken() {
        return randomDataGenerator.nextSecureHexString(32);
    }
}
