package org.skroutz.elasticsearch.module;

import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.client.Client;
import org.elasticsearch.ElasticsearchException;

public class WordDelimiterService extends AbstractLifecycleComponent<WordDelimiterService> {
	private final Thread syncWordsThread;

	@Inject
    public WordDelimiterService(Settings settings, Client client) {
        super(settings);
		syncWordsThread = new Thread(new WordDelimiterRunnable(client, settings));
    }

	@Override
    protected void doStart() throws ElasticsearchException {
		syncWordsThread.start();
	}

	@Override
	protected void doStop() throws ElasticsearchException {
	}

	@Override
	protected void doClose() throws ElasticsearchException {
	}
}
