package org.elasticsearch.module;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WordDelimiterActionListener;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.gateway.GatewayService;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public class WordDelimiterRunnable extends AbstractRunnable {
  public static final TimeValue REFRESH_INTERVAL = TimeValue.timeValueMinutes(5);
  public static final TimeValue BACKOFF_TIME = TimeValue.timeValueSeconds(2);
  public static final long NOT_READY_POLL_MS = 200L;
  public static final String INDEX_NAME = "protected_words";
  public static final int RESULTS_SIZE = 10000;

  private volatile boolean running;
  private final String index;
  private final long interval;
  private final long backoffTime;
  private static final Logger logger = Loggers.getLogger(WordDelimiterRunnable.class, "WordDelimiter", "Runnable");

  private final Client client;
  private final ClusterService clusterService;

  public WordDelimiterRunnable(Settings settings, Client client, ClusterService clusterService) {
    this.index = settings.get(
        "plugin.dynamic_word_delimiter.protected_words_index",
        INDEX_NAME);
    this.interval = settings.getAsTime(
        "plugin.dynamic_word_delimiter.refresh_interval",
        REFRESH_INTERVAL).getMillis();
    this.backoffTime = settings.getAsTime(
        "plugin.dynamic_word_delimiter.refresh_interval",
        BACKOFF_TIME).getMillis();
    this.client = client;
    this.clusterService = clusterService;
  }

  public void stopRunning() {
    running = false;
  }

  @Override
  public void onFailure(Exception t) {
    logger.warn(t.getMessage());
  }

  private boolean clusterReady() {
    if (clusterService.lifecycleState() != Lifecycle.State.STARTED) {
      return false;
    }
    try {
      return !clusterService.state()
          .blocks()
          .hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK);
    } catch (AssertionError e) {
      // ClusterApplierService asserts that the initial state has been set;
      // before that we are simply not ready.
      return false;
    }
  }

  private void getAllProtectedWords(WordDelimiterActionListener listener) {
    boolean hasProtectedWordsIndex = clusterService.state().metadata().getProject().hasIndex(index);
    boolean hasProtectedWordsAlias = clusterService.state().metadata().getProject().hasAlias(index);
    boolean hasProtectedWords = hasProtectedWordsIndex || hasProtectedWordsAlias;

    if (!hasProtectedWords) {
      logger.warn("Index [{}] not found", index);
      return;
    }

    SearchRequest request = new SearchRequest(index)
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery())
            .size(RESULTS_SIZE));

    SearchResponse response = client.search(request).actionGet();
    try {
      listener.onResponse(response);
    } finally {
      response.decRef();
    }
  }

  protected void doRun() {
    running = true;

    logger.debug("New thread spawned");

    WordDelimiterActionListener listener = WordDelimiterActionListener.getInstance();
    boolean waitAfterError = false;

    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        if (waitAfterError) {
          Thread.sleep(backoffTime);
          waitAfterError = false;
        }

        if (!clusterReady()) {
          logger.debug("Cluster state not yet recovered, deferring refresh");
          Thread.sleep(NOT_READY_POLL_MS);
          continue;
        }

        getAllProtectedWords(listener);

        logger.debug("Cache updater thread is suspended");
        Thread.sleep(interval);
        logger.debug("Cache updater thread is resumed");
      } catch (InterruptedException e) {
        logger.warn("Interrupted exception: breaking");
        Thread.currentThread().interrupt();
        break;
      } catch (ClusterBlockException e) {
        logger.warn("Cluster blocked, retrying after backoff: " + e.getMessage());
        waitAfterError = true;
      } catch (Exception e) {
        logger.error("Exception fetching protected words: " + e.getMessage());
        waitAfterError = true;
      }
    }
  }
}
