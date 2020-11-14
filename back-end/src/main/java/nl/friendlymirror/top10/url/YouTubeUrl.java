package nl.friendlymirror.top10.url;

import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import nl.friendlymirror.top10.ValidationException;

@UtilityClass
public class YouTubeUrl {

    private final Pattern SHORT_URL_PATTERN = Pattern.compile("^https://youtu.be/([^\\\\?]+)");
    private final Pattern LONG_URL_PATTERN = Pattern.compile("v=([^&]+)");

    public String toEmbeddableUrl(String url) {
        return "https://www.youtube-nocookie.com/embed/" + extractVideoId(url);
    }

    private String extractVideoId(String url) {
        if (url.startsWith("https://www.youtube.com/watch")) {
            var matcher = LONG_URL_PATTERN.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        if (url.startsWith("https://youtu.be/")) {
            var matcher = SHORT_URL_PATTERN.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        throw new ValidationException(String.format("Unrecognized URl format: %s", url));
    }
}
