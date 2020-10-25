package nl.friendlymirror.top10.random;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

    @Test
    public void generatesRandomToken() {
        var token = TokenGenerator.generateToken();
        assertThat(token).isNotBlank();
        assertThat(token).hasSize(32);
    }
}
