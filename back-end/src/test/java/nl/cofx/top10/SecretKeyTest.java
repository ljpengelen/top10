package nl.cofx.top10;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SecretKeyTest {

    @Test
    public void generateEncodedSecretKey() {
        var key = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        var encodedKey = Encoders.BASE64.encode(key.getEncoded());
        log.info("Encoded secret key: {}", encodedKey);
    }
}
