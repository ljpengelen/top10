package nl.cofx.top10.url;

import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import nl.cofx.top10.ValidationException;

@UtilityClass
public class YouTubeUrl {

    private final String LONG_URL_PREFIX = "https://www.youtube.com/watch";
    private final Pattern LONG_URL_PATTERN = Pattern.compile("v=([^&]+)");
    private final String SHORT_URL_PREFIX = "https://youtu.be/";
    private final Pattern SHORT_URL_PATTERN = Pattern.compile("^https://youtu.be/([^\\\\?]+)");
    private final String EMBEDDABLE_PREFIX = "https://www.youtube-nocookie.com/embed/";

    public String toEmbeddableUrl(String url) {
        return EMBEDDABLE_PREFIX + extractVideoId(url);
    }

    public String extractVideoId(String url) {
        if (url.startsWith(LONG_URL_PREFIX)) {
            var matcher = LONG_URL_PATTERN.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        if (url.startsWith(SHORT_URL_PREFIX)) {
            var matcher = SHORT_URL_PATTERN.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        throw new ValidationException(String.format("Unrecognized URl format: %s", url));
    }
}
