package org.elasticsearch.module;

import java.io.IOException;
import java.net.ConnectException;

import org.apache.http.HttpHost;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.support.WordDelimiterActionListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.core.TimeValue;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

public class WordDelimiterRunnable extends AbstractRunnable {
  public static final TimeValue REFRESH_INTERVAL = TimeValue.timeValueSeconds(5);
  public static final TimeValue BACKOFF_TIME = TimeValue.timeValueSeconds(2);
  public static final String INDEX_NAME = "protected_words";
  public static final int RESULTS_SIZE = 10000;

  private volatile boolean running;
  private final String index;
  private final long interval;
  private final long backoffTime;
  private final int httpPort = 9200;
  private static final Logger logger = Loggers.getLogger(WordDelimiterRunnable.class, "WordDelimiter", "Runnable");

  private final CustomElasticsearchClient customEsClient;
  private final CustomElasticsearchAsyncClient customEsAsyncClient;

  public WordDelimiterRunnable(Settings settings) {
    this.index = settings.get(
      "plugin.dynamic_word_delimiter.protected_words_index",
      INDEX_NAME
    );
    this.interval = settings.getAsTime(
      "plugin.dynamic_word_delimiter.refresh_interval",
      REFRESH_INTERVAL
    ).getMillis();
    this.backoffTime = settings.getAsTime(
      "plugin.dynamic_word_delimiter.refresh_interval",
      BACKOFF_TIME
    ).getMillis();

    HttpHost httpHost = new HttpHost("localhost", httpPort, "http");
    JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
    RestClient restClient = RestClient.builder(httpHost).build();
    customEsClient = new CustomElasticsearchClient(restClient, jsonpMapper);
    customEsAsyncClient = new CustomElasticsearchAsyncClient(restClient, jsonpMapper);
  }

  public void stopRunning() {
    running = false;
  }

  @Override
  public void onFailure(Exception t) {
    logger.error(t.getMessage());
  }

  private void getAllProtectedWords(WordDelimiterActionListener listener) throws IOException {
    if (customEsClient.indicesExists(index)) {
      customEsAsyncClient.searchMatchAll(index)
        .whenComplete((response, exception) -> {
          if (exception == null) {
            listener.onResponse(response);
          } else {
            if (exception.getCause() instanceof ConnectException) {
              logger.error("Error connecting to Elasticsearch: " + exception.getMessage());
            } else {
              logger.error("Error fetching protected words: " + exception.getMessage());
            }
            listener.onFailure((Exception)exception);
          }
        });
    } else {
      logger.error("Index [{}] not found", index);
    }
  }

  protected void doRun() {
    running = true;

    logger.error("New thread spawned");

    WordDelimiterActionListener listener = WordDelimiterActionListener.getInstance();
    Boolean waitAfterIOError = false;

    while(running && !Thread.currentThread().isInterrupted()) {
      try {

        if (waitAfterIOError) {
          Thread.sleep(backoffTime);
          waitAfterIOError = false;
        }
        getAllProtectedWords(listener);

        logger.error("Cache updater thread is suspended");
        Thread.sleep(interval);
        logger.error("Cache updater thread is resumed");
      } catch (InterruptedException e) {
        logger.error("Interrupted exception: breaking");
        Thread.currentThread().interrupt();
        break;
      } catch (IllegalStateException e) {
        logger.error("Illegal state exception: supressing: " + e.getMessage());
      } catch (ConnectException e) {
        logger.error("Connect exception: supressing: " + e.getMessage());
      } catch (IOException e) {
        logger.error("IO exception: supressing: " + e.getMessage());
        waitAfterIOError = true;
      } catch (Exception e) {
        logger.error("Exception: supressing: " + e.getMessage());
      }
    }
  }
}
