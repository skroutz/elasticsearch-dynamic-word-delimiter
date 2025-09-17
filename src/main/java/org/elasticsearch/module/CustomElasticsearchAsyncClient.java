package org.elasticsearch.module;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

public class CustomElasticsearchAsyncClient {
    private static final int SEARCH_SIZE = 10000;

    private final RestClient restClient;
    private final JacksonJsonpMapper jsonpMapper;

    public CustomElasticsearchAsyncClient(RestClient restClient, JacksonJsonpMapper jsonpMapper) {
        this.restClient = restClient;
        this.jsonpMapper = jsonpMapper;
    }

    public CompletableFuture<Boolean> indicesExists(String indexName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Request request = new Request("HEAD", "/" + indexName);
        restClient.performRequestAsync(request, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                future.complete(response.getStatusLine().getStatusCode() == 200);
            }

            @Override
            public void onFailure(Exception exception) {
                if (exception instanceof ResponseException) {
                    ResponseException e = (ResponseException) exception;
                    if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                        future.complete(false); // Index not found
                        return;
                    }
                }
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<JsonNode> searchMatchAll(String indexName) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        Request request = new Request("GET", "/" + indexName + "/_search");
        request.setJsonEntity("{\"query\": {\"match_all\": {}}, \"size\": " + SEARCH_SIZE + "}");

        restClient.performRequestAsync(request, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                try {
                    String jsonResponse = EntityUtils.toString(response.getEntity());
                    JsonNode jsonNode = jsonpMapper.objectMapper().readTree(jsonResponse);
                    future.complete(jsonNode);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }
}
