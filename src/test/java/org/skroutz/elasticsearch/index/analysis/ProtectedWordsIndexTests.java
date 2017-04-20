package org.skroutz.elasticsearch.index.analysis;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import java.io.StringReader;
import org.elasticsearch.action.support.WordDelimiterActionListener;
import org.elasticsearch.plugin.WordDelimiterPlugin;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;

import static org.elasticsearch.common.settings.Settings.builder;

import static org.elasticsearch.test.ESTokenStreamTestCase.assertTokenStreamContents;
import static org.skroutz.elasticsearch.index.analysis.AnalysisTestsHelper.filterFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

@ThreadLeakScope(Scope.NONE)
public class ProtectedWordsIndexTests extends ESIntegTestCase {
  private final WordDelimiterActionListener wordsListener = WordDelimiterActionListener.getInstance();
  private final static String INDEX_NAME = "protected_words";
  private final static String TYPE_NAME = "word";
  private final static String FILTER_NAME = "my_word_delimiter";

  @Override
  protected Collection<Class<? extends Plugin>> nodePlugins() {
      return Collections.singleton(WordDelimiterPlugin.class);
  }

  @Override
  protected Settings nodeSettings(int nodeOrdinal) {
    return builder()
        .put(super.nodeSettings(nodeOrdinal))
        .put("plugin.dynamic_word_delimiter.refresh_interval", "500ms")
        .build();
  }

  public void testAddWordToIndex() throws Exception {
    Settings indexSettings = builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    createIndex(INDEX_NAME);
    ensureGreen();
    client().prepareIndex(INDEX_NAME, TYPE_NAME, "1")
            .setSource("word", "1tb")
            .execute();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertTrue(protectedWords.size() == 1);

    String source = "skliros 1tb";
    String[] expected = new String[]{"skliros", "1tb"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }
  
  public void testRemoveWordFromIndex() throws Exception {
    Settings indexSettings = builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    createIndex(INDEX_NAME);
    ensureGreen();
    client().prepareIndex(INDEX_NAME, TYPE_NAME, "1").setSource("word", "1tb").execute();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertTrue(protectedWords.size() == 1);

    String source = "skliros 1tb";
    String[] expected = new String[]{"skliros", "1tb"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);

    client().prepareDelete(INDEX_NAME, TYPE_NAME, "1")
            .execute()
            .actionGet();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    protectedWords = wordsListener.getProtectedWords();
    assertTrue(protectedWords.isEmpty());

    expected = new String[]{"skliros", "1", "tb"};
    tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));

    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }
}
