package org.skroutz.elasticsearch.module;

import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.client.Client;
import org.elasticsearch.ElasticsearchException;

public class WordDelimiterService extends AbstractLifecycleComponent<WordDelimiterService> {
	public static final int WAIT_INTERVAL = 100;
	private final Thread syncWordsThread;
	private final WordDelimiterRunnable runnable;

	@Inject
    public WordDelimiterService(Settings settings, Client client) {
        super(settings);
        runnable = new WordDelimiterRunnable(client, settings);
		syncWordsThread = new Thread(runnable);
    }

	protected void doStart() throws ElasticsearchException {
		syncWordsThread.start();
	}

	protected void doStop() throws ElasticsearchException {
		runnable.stopRunning();
		syncWordsThread.interrupt();
		
		try {
			syncWordsThread.join(WAIT_INTERVAL);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected void doClose() throws ElasticsearchException {}
}
