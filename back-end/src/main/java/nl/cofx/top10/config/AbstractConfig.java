package nl.cofx.top10.config;

import javax.crypto.SecretKey;

import org.apache.commons.lang3.StringUtils;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

abstract public class AbstractConfig {

    protected SecretKey fetchJwtSecretKey(String name) {
        var encodedSecretKey = fetchMandatoryString(name);
        var decodedSecretKey = Decoders.BASE64.decode(encodedSecretKey);

        return Keys.hmacShaKeyFor(decodedSecretKey);
    }

    protected String fetchOptionalString(String name) {
        return System.getenv(name);
    }

    protected String fetchMandatoryString(String name) {
        var value = System.getenv(name);

        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException(String.format("Tried to load environment variable \"%s\", which is not set.", name));
        }

        return value;
    }

    protected int fetchMandatoryInt(String name) {
        return Integer.parseInt(fetchMandatoryString(name));
    }
}
