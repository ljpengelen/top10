package nl.cofx.top10;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;

@Log4j2
public class SecretKeyTest {

    @Test
    public void generateEncodedSecretKey() {
        var key = Jwts.SIG.HS512.key().build();
        var encodedKey = Encoders.BASE64.encode(key.getEncoded());
        log.info("Encoded secret key: {}", encodedKey);
    }
}
