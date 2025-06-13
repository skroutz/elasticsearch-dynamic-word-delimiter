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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

@ThreadLeakScope(Scope.NONE)
public class ProtectedWordsIndexTests extends ESSingleNodeTestCase {
  private final WordDelimiterActionListener wordsListener = WordDelimiterActionListener.getInstance();
  private final static String INDEX_NAME = "protected_words";
  private final static String FILTER_NAME = "my_word_delimiter";

  private ElasticsearchClient client;
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
    this.client = new ElasticsearchClient(this.transport);
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
}
