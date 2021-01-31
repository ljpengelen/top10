package nl.cofx.top10.session;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import nl.cofx.top10.jwt.Jwt;

class JwtSessionHandlerTest {

    private static final int ACCOUNT_ID = 1234;

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
    public void rejectsRequestWithoutAuthorizationHeader() {
        jwtSessionHandler.handle(routingContext);

        verify(response).setStatusCode(400);
        verify(response).end(new JsonObject()
                .put("error", "Missing authorization header")
                .toBuffer());
    }

    @Test
    public void rejectsRequestWithMalformedAuthorizationHeader() {
        when(request.getHeader("Authorization")).thenReturn("Malformed value");
        jwtSessionHandler.handle(routingContext);

        verify(response).setStatusCode(400);
        verify(response).end(new JsonObject()
                .put("error", "Malformed authorization header")
                .toBuffer());
    }

    @Test
    public void rejectsRequestWithInvalidToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer abcd1234");
        jwtSessionHandler.handle(routingContext);

        verify(response).setStatusCode(401);
        verify(response).end(new JsonObject()
                .put("error", "No session")
                .toBuffer());
    }

    @Test
    public void acceptsRequestWithToken() {
        var token = Jwts.builder()
                .setSubject(String.valueOf(ACCOUNT_ID))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS512)
                .compact();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        jwtSessionHandler.handle(routingContext);

        verifyNoInteractions(response);

        verify(routingContext).setUser(User.create(new JsonObject().put("accountId", ACCOUNT_ID)));
    }
}
