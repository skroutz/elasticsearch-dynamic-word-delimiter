package org.skroutz.elasticsearch.module;

import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.client.Client;
import org.elasticsearch.ElasticsearchException;

public class WordDelimiterService extends AbstractLifecycleComponent<WordDelimiterService> {

	private final Client client;
	private final Settings settings;

	@Inject
    public WordDelimiterService(Settings settings, Client client) {
        super(settings);
		this.client = client;
		this.settings = settings;
    }

	@Override
    protected void doStart() throws ElasticsearchException {
		new Thread(new WordDelimiterRunnable(client, settings)).start();
	}

	@Override
	protected void doStop() throws ElasticsearchException {
	}

	@Override
	protected void doClose() throws ElasticsearchException {
	}
}
