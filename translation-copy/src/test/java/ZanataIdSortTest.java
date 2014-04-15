import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.pressgang.ccms.zanata.ZanataIdSort;
import org.junit.Test;

public class ZanataIdSortTest {
    @Test
    public void shouldSortZanataIdsCorrectly() {
        // Given a list of zanata ids
        final List<String> zanataIds = Arrays.asList("33-56091", "33-10234", "33-90384", "33-75612-74628");

        // When
        Collections.sort(zanataIds, new ZanataIdSort());

        // Then
        assertThat(zanataIds.get(0), is("33-10234"));
        assertThat(zanataIds.get(1), is("33-56091"));
        assertThat(zanataIds.get(2), is("33-75612-74628"));
        assertThat(zanataIds.get(3), is("33-90384"));
    }
}
