package nl.cofx.top10.session;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

class PrivateRouteHandlerTest {

    private static final User USER = User.create(new JsonObject());

    private final RoutingContext routingContext = mock(RoutingContext.class);
    private final HttpServerResponse response = mock(HttpServerResponse.class);

    private final PrivateRouteHandler privateRouteHandler = new PrivateRouteHandler();

    @BeforeEach
    public void setUp() {
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        when(routingContext.response()).thenReturn(response);
    }

    @Test
    public void rejectsRequestsWithoutAuthenticatedUser() {
        privateRouteHandler.handle(routingContext);

        verify(response).setStatusCode(401);
        verify(response).end(new JsonObject()
                .put("error", "No authenticated user found")
                .toBuffer());
        verify(routingContext, never()).next();
    }

    @Test
    public void acceptsRequestsWithAuthenticatedUser() {
        when(routingContext.user()).thenReturn(USER);
        privateRouteHandler.handle(routingContext);

        verifyNoInteractions(response);
        verify(routingContext).next();
    }
}
