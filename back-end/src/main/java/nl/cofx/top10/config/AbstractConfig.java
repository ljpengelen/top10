package nl.cofx.top10.config;

import org.apache.commons.lang3.StringUtils;

public class AbstractConfig {

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
