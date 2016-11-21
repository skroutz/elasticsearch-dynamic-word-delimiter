package org.skroutz.elasticsearch.index.analysis;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import java.io.StringReader;
import org.elasticsearch.action.support.WordDelimiterActionListener;
import org.elasticsearch.plugin.WordDelimiterPlugin;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.test.ElasticsearchTokenStreamTestCase.assertTokenStreamContents;
import static org.skroutz.elasticsearch.index.analysis.AnalysisTestsHelper.filterFactory;

import org.junit.Test;
import java.util.Set;

@ThreadLeakScope(Scope.NONE)
public class ProtectedWordsIndexTests extends ElasticsearchIntegrationTest {
  private final WordDelimiterActionListener wordsListener = WordDelimiterActionListener.getInstance();

  @Override
  protected Settings nodeSettings(int nodeOrdinal) {
    return settingsBuilder()
        .put("plugin.types", WordDelimiterPlugin.class.getName())
        .put("plugin.dynamic_word_delimiter.refresh_interval", "500ms")
        .put(super.nodeSettings(nodeOrdinal))
        .build();
  }

  @Test
  public void testAddWordToIndex() throws Exception {
    Settings indexSettings = settingsBuilder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, "my_word_delimiter");

    createIndex("protected_words");
    ensureGreen();
    client().prepareIndex("protected_words", "word", "1").setSource("word", "1tb").execute();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertTrue(protectedWords.size() == 1);

    String source = "skliros 1tb";
    String[] expected = new String[]{"skliros", "1tb"};
    Tokenizer tokenizer = new WhitespaceTokenizer(new StringReader(source));

    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  @Test
  public void testRemoveWordFromIndex() throws Exception {
    Settings indexSettings = settingsBuilder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, "my_word_delimiter");

    createIndex("protected_words");
    ensureGreen();
    client().prepareIndex("protected_words", "word", "1").setSource("word", "1tb").execute();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertTrue(protectedWords.size() == 1);

    String source = "skliros 1tb";
    String[] expected = new String[]{"skliros", "1tb"};
    Tokenizer tokenizer = new WhitespaceTokenizer(new StringReader(source));

    assertTokenStreamContents(filterFactory.create(tokenizer), expected);

    client().prepareDelete("protected_words", "word", "1").execute().actionGet();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    protectedWords = wordsListener.getProtectedWords();
    assertTrue(protectedWords.isEmpty());

    expected = new String[]{"skliros", "1", "tb"};
    tokenizer = new WhitespaceTokenizer(new StringReader(source));

    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }
}
