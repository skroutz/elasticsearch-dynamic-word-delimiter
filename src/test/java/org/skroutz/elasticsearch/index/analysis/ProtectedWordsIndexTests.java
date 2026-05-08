package org.skroutz.elasticsearch.index.analysis;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.index.IndexVersion;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WordDelimiterActionListener;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import static org.elasticsearch.common.settings.Settings.builder;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.plugin.WordDelimiterPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.junit.Test;
import static org.skroutz.elasticsearch.index.analysis.AnalysisTestsHelper.filterFactory;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;

@ThreadLeakScope(Scope.NONE)
public class ProtectedWordsIndexTests extends ESSingleNodeTestCase {
  private final WordDelimiterActionListener wordsListener = WordDelimiterActionListener.getInstance();
  private final static String INDEX_NAME = "protected_words";
  private final static String FILTER_NAME = "my_word_delimiter";

  @Override
  protected Collection<Class<? extends Plugin>> getPlugins() {
    return Arrays.asList(WordDelimiterPlugin.class);
  }

  @Override
  protected Settings nodeSettings() {
    return builder()
      .put("plugin.dynamic_word_delimiter.refresh_interval", "500ms")
      .build();
  }

  @Test
  public void testAddWordToIndex() throws Exception {
    Settings indexSettings = builder()
        .put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current())
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    createIndex(INDEX_NAME);
    ensureGreen();
    client().index(new IndexRequest().
            setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).
            index(INDEX_NAME).
            id("1").
            source("word", "1tb")).get();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertEquals(1, protectedWords.size());

    String source = "skliros 1tb";
    Tokenizer tokenizer = new WhitespaceTokenizer();
    TokenStream tokenStream = filterFactory.create(tokenizer);

    tokenizer.setReader(new StringReader(source));
    tokenStream.reset();
    assertTrue(tokenStream.incrementToken());
    assertEquals(tokenStream.getAttribute(CharTermAttribute.class).toString(), "skliros");
    assertTrue(tokenStream.incrementToken());
    assertEquals(tokenStream.getAttribute(CharTermAttribute.class).toString(), "1tb");
    tokenStream.close();
  }

  public void testRemoveWordFromIndex() throws Exception {
    Settings indexSettings = builder()
        .put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current())
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    createIndex(INDEX_NAME);
    ensureGreen();
    IndexResponse indexed = (IndexResponse) client().index(new IndexRequest().
            setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).
            index(INDEX_NAME).
            source("word", "1tb")).get();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertEquals(1, protectedWords.size());

    String source = "skliros 1tb";
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    TokenStream tokenStream = filterFactory.create(tokenizer);
    tokenStream.reset();
    assertTrue(tokenStream.incrementToken());
    assertEquals(tokenStream.getAttribute(CharTermAttribute.class).toString(), "skliros");
    assertTrue(tokenStream.incrementToken());
    assertEquals(tokenStream.getAttribute(CharTermAttribute.class).toString(), "1tb");
    tokenStream.close();

    client().delete(new DeleteRequest().
            setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).
            index(INDEX_NAME).
            id(indexed.getId())).get();

    Thread.sleep(TimeValue.timeValueSeconds(2).getMillis());

    protectedWords = wordsListener.getProtectedWords();
    assertTrue(protectedWords.isEmpty());

    source = "skliros 1tb";
    tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    tokenStream = filterFactory.create(tokenizer);
    tokenStream.reset();
    assertTrue(tokenStream.incrementToken());
    assertEquals(tokenStream.getAttribute(CharTermAttribute.class).toString(), "skliros");
    assertTrue(tokenStream.incrementToken());
    assertEquals(tokenStream.getAttribute(CharTermAttribute.class).toString(), "1");
    assertTrue(tokenStream.incrementToken());
    assertEquals(tokenStream.getAttribute(CharTermAttribute.class).toString(), "tb");
    tokenStream.close();
  }

  /**
   * Regression test: ensure all protected words are loaded when the index has more
   * than the default search limit of 10 documents.
   */
  @Test
  public void testRegressionMoreThan10ProtectedWords() throws Exception {
    Settings indexSettings = builder()
        .put(IndexMetadata.SETTING_VERSION_CREATED, IndexVersion.current())
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    createIndex(INDEX_NAME);
    ensureGreen();

    String[] protectedWordsList = {
        "1tb", "2gb", "3mb", "4kb", "5pb", "6eb", "7zb", "8yb", "9bb", "10ab",
        "11cd", "12ef", "13gh", "14ij", "15kl"
    };

    for (int i = 0; i < protectedWordsList.length; i++) {
      client().index(new IndexRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).index(INDEX_NAME)
          .id(String.valueOf(i + 1)).source("word", protectedWordsList[i])).get();
    }

    Thread.sleep(TimeValue.timeValueSeconds(3).getMillis());

    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertEquals("All 15 protected words should be loaded, not just the first 10",
        15, protectedWords.size());

    String sourceWith15thWord = "test 15kl storage";
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(sourceWith15thWord));
    TokenStream tokenStream = filterFactory.create(tokenizer);
    tokenStream.reset();

    assertTrue(tokenStream.incrementToken());
    assertEquals("test", tokenStream.getAttribute(CharTermAttribute.class).toString());

    assertTrue(tokenStream.incrementToken());
    assertEquals("REGRESSION: 15th protected word should remain intact",
        "15kl", tokenStream.getAttribute(CharTermAttribute.class).toString());

    assertTrue(tokenStream.incrementToken());
    assertEquals("storage", tokenStream.getAttribute(CharTermAttribute.class).toString());

    tokenStream.close();

    String sourceWith11thWord = "data 11cd backup";
    tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(sourceWith11thWord));
    tokenStream = filterFactory.create(tokenizer);
    tokenStream.reset();

    assertTrue(tokenStream.incrementToken());
    assertEquals("data", tokenStream.getAttribute(CharTermAttribute.class).toString());

    assertTrue(tokenStream.incrementToken());
    assertEquals("11th protected word should also remain intact",
        "11cd", tokenStream.getAttribute(CharTermAttribute.class).toString());

    assertTrue(tokenStream.incrementToken());
    assertEquals("backup", tokenStream.getAttribute(CharTermAttribute.class).toString());

    tokenStream.close();
  }
}
