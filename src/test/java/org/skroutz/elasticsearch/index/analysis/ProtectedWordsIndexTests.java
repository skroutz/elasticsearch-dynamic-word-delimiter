package org.skroutz.elasticsearch.index.analysis;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.apache.http.HttpHost;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.Version;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WordDelimiterActionListener;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import static org.elasticsearch.common.settings.Settings.builder;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.plugin.WordDelimiterPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.transport.netty4.Netty4Plugin;
import org.junit.Test;
import static org.skroutz.elasticsearch.index.analysis.AnalysisTestsHelper.filterFactory;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@ThreadLeakScope(Scope.NONE)
public class ProtectedWordsIndexTests extends ESSingleNodeTestCase {
  private final WordDelimiterActionListener wordsListener = WordDelimiterActionListener.getInstance();
  private final static String INDEX_NAME = "protected_words";
  private final static String FILTER_NAME = "my_word_delimiter";

  private ElasticsearchTransport transport;

  @Override
  protected Collection<Class<? extends Plugin>> getPlugins() {
    return Arrays.asList(Netty4Plugin.class, WordDelimiterPlugin.class);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // create an rest client provided from elasticsearch to connect to the node
    RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost("localhost", 9200));
    RestClient restClient = restClientBuilder.build();
    this.transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    if (this.transport != null) {
      try {
        this.transport.close();
      } catch (IOException e) {
        logger.warn("Failed to close Elasticsearch transport", e);
      }
    }
  }

  @Override
  protected Settings nodeSettings() {
    return builder()
      .put("plugin.dynamic_word_delimiter.refresh_interval", "500ms")
      .put("network.host", "localhost")
      .put("http.port", 9200)
      .put("http.type", "netty4")
      .put("cluster.name", "test-1node-cluster")
      .build();
  }

  @Override
  protected boolean addMockHttpTransport() {
    return false;
  }

  @Test
  public void testAddWordToIndex() throws Exception {
    Settings indexSettings = builder()
        .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
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
        .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
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
   * Regression test to ensure that when more than 10 protected words are stored
   * in the index,
   * all of them are correctly retrieved and applied. Without SEARCH_SIZE=10000 in
   * the
   * CustomElasticsearchClient, only the first 10 protected words would be loaded
   * due to
   * Elasticsearch's default search size limit.
   */
  @Test
  public void testRegressionMoreThan10ProtectedWords() throws Exception {
    Settings indexSettings = builder()
        .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    createIndex(INDEX_NAME);
    ensureGreen();

    // Add 15 protected words to test beyond the default Elasticsearch search limit
    // of 10
    String[] protectedWordsList = {
        "1tb", "2gb", "3mb", "4kb", "5pb", "6eb", "7zb", "8yb", "9bb", "10ab",
        "11cd", "12ef", "13gh", "14ij", "15kl"
    };

    // Index all 15 protected words
    for (int i = 0; i < protectedWordsList.length; i++) {
      client().index(new IndexRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).index(INDEX_NAME)
          .id(String.valueOf(i + 1)).source("word", protectedWordsList[i])).get();
    }

    // Wait for the system to process all the new words
    Thread.sleep(TimeValue.timeValueSeconds(3).getMillis());

    // Verify all 15 words are in the protected words set
    Set<String> protectedWords = wordsListener.getProtectedWords();
    assertEquals("All 15 protected words should be loaded, not just the first 10",
        15, protectedWords.size());

    // Test that the 15th word (which would be missed without SEARCH_SIZE=10000) is
    // protected
    String sourceWith15thWord = "test 15kl storage";
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(sourceWith15thWord));
    TokenStream tokenStream = filterFactory.create(tokenizer);
    tokenStream.reset();

    // Verify "test" is tokenized normally
    assertTrue(tokenStream.incrementToken());
    assertEquals("test", tokenStream.getAttribute(CharTermAttribute.class).toString());

    // CRITICAL REGRESSION TEST: "15kl" should remain intact (not split to "15" and
    // "kl")
    // This would FAIL if SEARCH_SIZE was not set, because "15kl" wouldn't be in the
    // protected words
    assertTrue(tokenStream.incrementToken());
    assertEquals("REGRESSION: 15th protected word should remain intact, proving SEARCH_SIZE=10000 works",
        "15kl", tokenStream.getAttribute(CharTermAttribute.class).toString());

    // Verify "storage" is tokenized normally
    assertTrue(tokenStream.incrementToken());
    assertEquals("storage", tokenStream.getAttribute(CharTermAttribute.class).toString());

    tokenStream.close();

    // Test another word from the middle of the list (11th word)
    String sourceWith11thWord = "data 11cd backup";
    tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(sourceWith11thWord));
    tokenStream = filterFactory.create(tokenizer);
    tokenStream.reset();

    assertTrue(tokenStream.incrementToken());
    assertEquals("data", tokenStream.getAttribute(CharTermAttribute.class).toString());

    // This tests that the 11th word is also protected (would fail without
    // SEARCH_SIZE=10000)
    assertTrue(tokenStream.incrementToken());
    assertEquals("11th protected word should also remain intact",
        "11cd", tokenStream.getAttribute(CharTermAttribute.class).toString());

    assertTrue(tokenStream.incrementToken());
    assertEquals("backup", tokenStream.getAttribute(CharTermAttribute.class).toString());

    tokenStream.close();

    // Test the last word (15th) to ensure it's definitely working
    String sourceWithLastWord = "final 15kl test";
    tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(sourceWithLastWord));
    tokenStream = filterFactory.create(tokenizer);
    tokenStream.reset();

    assertTrue(tokenStream.incrementToken());
    assertEquals("final", tokenStream.getAttribute(CharTermAttribute.class).toString());

    // MOST CRITICAL TEST: The 15th word should be protected
    // Without SEARCH_SIZE=10000, this would be split into "15" and "kl"
    assertTrue(tokenStream.incrementToken());
    assertEquals("CRITICAL: Last protected word proves all words beyond 10 are loaded",
        "15kl", tokenStream.getAttribute(CharTermAttribute.class).toString());

    assertTrue(tokenStream.incrementToken());
    assertEquals("test", tokenStream.getAttribute(CharTermAttribute.class).toString());

    tokenStream.close();
  }
}
