package nl.cofx.top10.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTest {

    private static final String ENCODED_SECRET_KEY = "FsJtRGG84NM7BNewGo5AXvg6GJ1DKedDJjkirpDEAOtVgdi6j3f+THdeEika6v3dB8N4DO0fywkd+JK2A5eKLQ==";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ENCODED_SECRET_KEY));

    private final Jwt jwt = new Jwt(SECRET_KEY);

    @Test
    public void convertsValidJwt() {
        var subject = "test";
        var validJwt = Jwts.builder()
                .subject(subject)
                .signWith(SECRET_KEY, Jwts.SIG.HS512)
                .compact();

        var jws = jwt.getJws(validJwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getPayload().getSubject()).isEqualTo(subject);
    }

    @Test
    public void returnsNullGivenInvalidJwt() {
        assertThat(jwt.getJws("")).isNull();
    }

    @Test
    public void returnsNullGivenNullJwt() {
        assertThat(jwt.getJws(null)).isNull();
    }

    @Test
    public void returnsNullGivenExpiredJwt() {
        var validJwt = Jwts.builder()
                .expiration(new Date())
                .subject("test")
                .signWith(SECRET_KEY, Jwts.SIG.HS512)
                .compact();

        assertThat(jwt.getJws(validJwt)).isNull();
    }
}
