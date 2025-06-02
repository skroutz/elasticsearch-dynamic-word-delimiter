package org.elasticsearch.module;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

public class WordDelimiterService extends AbstractLifecycleComponent {
  private static final Logger logger = Loggers.getLogger(
    WordDelimiterRunnable.class, "WordDelimiterService", "Runnable"
  );
  public static final int WAIT_INTERVAL = 100;
  private final Thread syncWordsThread;
  private final WordDelimiterRunnable runnable;

  public WordDelimiterService(Settings settings) {
    logger.error("Service started");
    runnable = new WordDelimiterRunnable(settings);
    logger.error("Created runnable");
    syncWordsThread = new Thread(runnable);
    logger.error("Spawned thread");
  }

  @Override
  protected void doStart() throws ElasticsearchException {
    syncWordsThread.start();
  }

  @Override
  protected void doStop() throws ElasticsearchException {
    runnable.stopRunning();
    syncWordsThread.interrupt();

    try {
      syncWordsThread.join(WAIT_INTERVAL);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void doClose() throws ElasticsearchException {}
}
