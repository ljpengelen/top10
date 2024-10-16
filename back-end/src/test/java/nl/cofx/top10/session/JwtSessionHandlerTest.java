package nl.cofx.top10.session;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import nl.cofx.top10.jwt.Jwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtSessionHandlerTest {

    private static final String ACCOUNT_ID = "6c1f2bd1-bc6f-4ce0-ae4b-ff5a17eaf470";
    private static final String NAME = "Jeff Doe";
    private static final String EMAIL_ADDRESS = "jeff.doe@example.com";

    private static final String ENCODED_SECRET_KEY = "FsJtRGG84NM7BNewGo5AXvg6GJ1DKedDJjkirpDEAOtVgdi6j3f+THdeEika6v3dB8N4DO0fywkd+JK2A5eKLQ==";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ENCODED_SECRET_KEY));

    private final RoutingContext routingContext = mock(RoutingContext.class);
    private final HttpServerRequest request = mock(HttpServerRequest.class);
    private final HttpServerResponse response = mock(HttpServerResponse.class);

    private final Jwt jwt = new Jwt(SECRET_KEY);
    private final JwtSessionHandler jwtSessionHandler = new JwtSessionHandler(jwt);

    @BeforeEach
    public void setUp() {
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
    }

    @Test
    public void doesNotSetUserGivenNoAuthorizationHeader() {
        jwtSessionHandler.handle(routingContext);

        verify(routingContext).next();
        verify(routingContext, never()).setUser(any());
    }

    @Test
    public void doesNotSetUserGivenMalformedAuthorizationHeader() {
        when(request.getHeader("Authorization")).thenReturn("Malformed value");
        jwtSessionHandler.handle(routingContext);

        verify(routingContext).next();
        verify(routingContext, never()).setUser(any());
    }

    @Test
    public void doesNotSetUserGivenInvalidToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer abcd1234");
        jwtSessionHandler.handle(routingContext);

        verify(routingContext).next();
        verify(routingContext, never()).setUser(any());
    }

    @Test
    public void setsUserGivenValidToken() {
        var token = Jwts.builder()
                .subject(ACCOUNT_ID)
                .claim("name", NAME)
                .claim("emailAddress", EMAIL_ADDRESS)
                .signWith(SECRET_KEY, Jwts.SIG.HS512)
                .compact();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        jwtSessionHandler.handle(routingContext);

        verify(routingContext).next();
        verify(routingContext).setUser(User.create(new JsonObject()
                .put("accountId", ACCOUNT_ID)
                .put("name", NAME)
                .put("emailAddress", EMAIL_ADDRESS)));
    }
}
