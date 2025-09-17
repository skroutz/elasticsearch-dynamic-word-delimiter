package org.elasticsearch.module;

import java.util.Arrays;
import java.util.Collection;

import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.plugin.WordDelimiterPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.transport.netty4.Netty4Plugin;

import com.fasterxml.jackson.databind.JsonNode;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;

import static org.elasticsearch.common.settings.Settings.builder;

public class CustomElasticsearchClientTests extends ESSingleNodeTestCase {

    private CustomElasticsearchClient customClient;
    private RestClient restClient;

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Arrays.asList(Netty4Plugin.class, WordDelimiterPlugin.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost("localhost", 9200));
        this.restClient = restClientBuilder.build();
        this.customClient = new CustomElasticsearchClient(this.restClient, new JacksonJsonpMapper());
    }

    @Override
    public void tearDown() throws Exception {
        if (this.restClient != null) {
            this.restClient.close();
        }
        super.tearDown();
    }

    @Override
    protected org.elasticsearch.common.settings.Settings nodeSettings() {
        return builder()
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

    public void testSearchMatchAllReturnsMoreThanDefaultLimit() throws Exception {
        String indexName = "test-search-limit";

        createIndex(indexName);
        ensureGreen();

        for (int i = 1; i <= 15; i++) {
            client().index(new IndexRequest()
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .index(indexName)
                .id(String.valueOf(i))
                .source("field", "value" + i, "number", i)).get();
        }

        JsonNode result = customClient.searchMatchAll(indexName);

        JsonNode hits = result.get("hits").get("hits");
        assertTrue("Should return more than default 10 results", hits.size() > 10);
        assertEquals("Should return all 15 documents", 15, hits.size());
    }

    public void testRegressionMoreThan50Documents() throws Exception {
        String indexName = "test-regression-50";

        createIndex(indexName);
        ensureGreen();

        for (int i = 1; i <= 50; i++) {
            client().index(new IndexRequest()
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .index(indexName)
                .id(String.valueOf(i))
                .source("product", "item" + i, "category", "electronics")).get();
        }

        JsonNode result = customClient.searchMatchAll(indexName);

        JsonNode hits = result.get("hits").get("hits");
        assertEquals("Should return all 50 documents", 50, hits.size());

        assertTrue("REGRESSION: Without SEARCH_SIZE=10000, this would fail", hits.size() > 40);
    }
}
