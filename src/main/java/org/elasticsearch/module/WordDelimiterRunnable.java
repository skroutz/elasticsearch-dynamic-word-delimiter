package org.elasticsearch.module;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WordDelimiterActionListener;

public class WordDelimiterRunnable extends AbstractRunnable {
  public static final TimeValue REFRESH_INTERVAL = TimeValue.timeValueMinutes(5);
  public static final String INDEX_NAME = "protected_words";
  public static final int RESULTS_SIZE = 10000;

  private volatile boolean running;
  private final Client client;
  private final String index;
  private final long interval;
  private static final Logger logger = Loggers.getLogger(WordDelimiterRunnable.class, "WordDelimiter", "Runnable");

  public WordDelimiterRunnable(Client client, Settings settings) {
    this.client = client;
    this.index = settings.get("plugin.dynamic_word_delimiter.protected_words_index", INDEX_NAME);
    this.interval = settings.getAsTime("plugin.dynamic_word_delimiter.refresh_interval", REFRESH_INTERVAL).getMillis();
  }

  public void stopRunning() {
    running = false;
  }

  @Override
  public void onFailure(Exception t) {
    logger.error(t.getMessage());
  }

  protected void doRun() {
    running = true;
    WordDelimiterActionListener listener = WordDelimiterActionListener.getInstance();
    SearchRequest searchRequest = client.prepareSearch().setSearchType(SearchType.QUERY_THEN_FETCH)
        .setIndices(index).setSize(RESULTS_SIZE).request();

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
