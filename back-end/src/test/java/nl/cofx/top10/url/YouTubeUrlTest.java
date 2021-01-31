package nl.cofx.top10.url;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YouTubeUrlTest {

    @Test
    public void returnsEmbeddableUrlsForLongUrls() {
        assertThat(YouTubeUrl.toEmbeddableUrl("https://www.youtube.com/watch?v=6RjJFoGt0y4")).isEqualTo("https://www.youtube-nocookie.com/embed/6RjJFoGt0y4");
        assertThat(YouTubeUrl.toEmbeddableUrl("https://www.youtube.com/watch?v=RBgcN9lrZ3g&list=PLsn6N7S-aJO3KeJnHmiT3rUcmZqesaj_b&index=9"))
                .isEqualTo("https://www.youtube-nocookie.com/embed/RBgcN9lrZ3g");
    }

    @Test
    public void returnsEmbeddableUrlsForShortUrls() {
        assertThat(YouTubeUrl.toEmbeddableUrl("https://youtu.be/9_vjHV-nVYY")).isEqualTo("https://www.youtube-nocookie.com/embed/9_vjHV-nVYY");
        assertThat(YouTubeUrl.toEmbeddableUrl("https://youtu.be/vw378gNT-Kg")).isEqualTo("https://www.youtube-nocookie.com/embed/vw378gNT-Kg");
        assertThat(YouTubeUrl.toEmbeddableUrl("https://youtu.be/9_vjHV-nVYY?t=295")).isEqualTo("https://www.youtube-nocookie.com/embed/9_vjHV-nVYY");
    }
}
