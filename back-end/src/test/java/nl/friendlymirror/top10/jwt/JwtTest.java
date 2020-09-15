package nl.friendlymirror.top10.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

class JwtTest {

    private static final String ENCODED_SECRET_KEY = "FsJtRGG84NM7BNewGo5AXvg6GJ1DKedDJjkirpDEAOtVgdi6j3f+THdeEika6v3dB8N4DO0fywkd+JK2A5eKLQ==";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ENCODED_SECRET_KEY));

    private final Jwt jwt = new Jwt(SECRET_KEY);

    @Test
    public void convertsValidJwt() {
        var subject = "test";
        var validJwt = Jwts.builder()
                .setSubject(subject)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        var jws = jwt.getJws(validJwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getBody().getSubject()).isEqualTo(subject);
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
                .setExpiration(new Date())
                .setSubject("test")
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();

        assertThat(jwt.getJws(validJwt)).isNull();
    }
}
