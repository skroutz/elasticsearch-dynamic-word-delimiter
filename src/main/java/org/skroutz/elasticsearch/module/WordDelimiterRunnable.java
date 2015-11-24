package org.skroutz.elasticsearch.module;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.skroutz.elasticsearch.action.support.WordDelimiterActionListener;

import java.util.concurrent.TimeUnit;

public class WordDelimiterRunnable extends AbstractRunnable {

	private final Client client;
	private final String index;
	private final long interval;
	private final ESLogger logger = ESLoggerFactory.getLogger(WordDelimiterRunnable.class.getSimpleName());

	public WordDelimiterRunnable(Client client, Settings settings) {
		this.client = client;
		this.index = settings.get("plugin.skroutz_word_delimiter.protected_words_index", "protected_words");
		TimeValue temp = settings.getAsTime("plugin.skroutz_word_delimiter.refresh_interval", TimeValue.timeValueMinutes(1));
		this.interval = temp.getMillis();
	}

    public void doRun() throws Exception {
		while (true) {
			Thread.sleep(interval);
			SearchRequest searchRequest = new SearchRequest(index);
			searchRequest.listenerThreaded(false);
			client.search(searchRequest, new WordDelimiterActionListener());
		}
	}

    public void onFailure(Throwable t) {
		logger.error(t.getMessage());
	}
}
