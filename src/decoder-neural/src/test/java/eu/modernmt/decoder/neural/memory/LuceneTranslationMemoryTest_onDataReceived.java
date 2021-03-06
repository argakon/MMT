package eu.modernmt.decoder.neural.memory;

import eu.modernmt.data.TranslationUnit;
import eu.modernmt.lang.LanguagePair;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static eu.modernmt.decoder.neural.memory.TestData.*;
import static org.junit.Assert.assertEquals;

/**
 * Created by davide on 03/08/17.
 */
public class LuceneTranslationMemoryTest_onDataReceived {

    private TLuceneTranslationMemory memory;

    public void setup(LanguagePair... languages) throws Throwable {
        this.memory = new TLuceneTranslationMemory(languages);
    }

    @After
    public void teardown() throws Throwable {
        this.memory.close();
        this.memory = null;
    }

    private void test(List<TranslationUnit> units) throws IOException {
        int size = units.size();
        Map<Short, Long> expectedChannels = TestData.channels(0, size - 1);
        Set<TLuceneTranslationMemory.Entry> expectedEntries =
                TLuceneTranslationMemory.Entry.asEntrySet(memory.getLanguageIndex(), units);

        memory.onDataReceived(units);

        assertEquals(size + 1, memory.size());
        assertEquals(expectedEntries, memory.entrySet());
        assertEquals(expectedChannels, memory.getLatestChannelPositions());
    }

    @Test
    public void monoDirectionalMemoryAndDirectContributions() throws Throwable {
        setup(EN__IT);
        test(TestData.tuList(EN__IT, 4));
    }

    @Test
    public void monoDirectionalMemoryAndReversedContributions() throws Throwable {
        setup(EN__IT);
        test(TestData.tuList(IT__EN, 4));
    }

    @Test
    public void biDirectionalMemory() throws Throwable {
        setup(EN__IT, IT__EN);
        test(TestData.tuList(EN__IT, 4));
    }

    @Test
    public void dialectMonoDirectionalMemoryAndDirectContributions() throws Throwable {
        setup(EN_US__IT);
        test(TestData.tuList(EN_US__IT, 4));
    }

    @Test
    public void dialectMonoDirectionalMemoryAndReversedContributions() throws Throwable {
        setup(EN_US__IT);
        test(TestData.tuList(IT__EN_US, 4));
    }

    @Test
    public void dialectBiDirectionalMemory() throws Throwable {
        setup(EN_US__IT, IT__EN_US);
        test(TestData.tuList(EN_US__IT, 4));
    }

    @Test
    public void multilingualMemoryWithOneDirectionContributions() throws Throwable {
        setup(EN__IT, FR__ES);
        test(TestData.tuList(EN__IT, 4));
    }

    @Test
    public void multilingualMemoryWithAllDirectionContributions() throws Throwable {
        setup(EN__IT, FR__ES);

        List<TranslationUnit> units = Arrays.asList(
                TestData.tu(0, 0L, 1L, EN__IT, null),
                TestData.tu(0, 1L, 2L, EN__IT, null),
                TestData.tu(0, 2L, 1L, FR__ES, null),
                TestData.tu(0, 3L, 1L, FR__ES, null)
        );

        test(units);
    }

    @Test
    public void duplicateContribution() throws Throwable {
        setup(EN__IT, FR__ES);

        List<TranslationUnit> units = Arrays.asList(
                TestData.tu(1, 0L, 1L, EN__IT, null),
                TestData.tu(1, 1L, 1L, EN__IT, null)
        );

        List<TranslationUnit> cloneUnits = Arrays.asList(
                TestData.tu(1, 0L, 2L, FR__ES, null),
                TestData.tu(1, 1L, 2L, FR__ES, null)
        );

        Map<Short, Long> expectedChannels = TestData.channels(1, 1);
        memory.onDataReceived(units);

        assertEquals(2 + 1, memory.size());
        assertEquals(expectedChannels, memory.getLatestChannelPositions());

        memory.onDataReceived(cloneUnits);

        assertEquals(2 + 1, memory.size());
        assertEquals(expectedChannels, memory.getLatestChannelPositions());
    }

}
