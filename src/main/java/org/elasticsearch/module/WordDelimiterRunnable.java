package org.elasticsearch.module;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WordDelimiterActionListener;

public class WordDelimiterRunnable extends AbstractRunnable {
  public static final TimeValue REFRESH_INTERVAL = TimeValue.timeValueMinutes(5);
  public static final String INDEX_NAME = "protected_words";
  public static final String INDEX_TYPE = "word";
  public static final int RESULTS_SIZE = 150000;

  private volatile boolean running;
  private final Client client;
  private final String index;
  private final long interval;
  private final String type;
  private final ESLogger logger = ESLoggerFactory.getLogger(WordDelimiterRunnable.class.getSimpleName());

  public WordDelimiterRunnable(Client client, Settings settings) {
    this.client = client;
    this.index = settings.get("plugin.dynamic_word_delimiter.protected_words_index", INDEX_NAME);
    this.type = settings.get("plugin.dynamic_word_delimiter.protected_words_type", INDEX_TYPE);
    this.interval = settings.getAsTime("plugin.dynamic_word_delimiter.refresh_interval", REFRESH_INTERVAL).getMillis();
  }

  public void stopRunning() {
    running = false;
  }

  public void onFailure(Throwable t) {
    logger.error(t.getMessage());
  }

  protected void doRun() {
    running = true;
    WordDelimiterActionListener listener = WordDelimiterActionListener.getInstance();
    SearchRequest searchRequest = client.prepareSearch().setSearchType(SearchType.QUERY_THEN_FETCH)
        .setIndices(index).setTypes(type).setSize(RESULTS_SIZE).request();
    searchRequest.listenerThreaded(false);

    while (running) {
      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        logger.error(e.getMessage());
      }

      if (client.admin().indices().prepareExists(index).get().isExists())
        client.search(searchRequest, listener);
    }
  }
}
