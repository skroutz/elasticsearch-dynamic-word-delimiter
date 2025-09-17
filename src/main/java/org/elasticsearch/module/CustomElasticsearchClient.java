package org.elasticsearch.module;

import java.io.IOException;

import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

public class CustomElasticsearchClient {
    private static final int SEARCH_SIZE = 10000;

    private final RestClient restClient;
    private final JacksonJsonpMapper jsonpMapper;

    public CustomElasticsearchClient(RestClient restClient, JacksonJsonpMapper jsonpMapper) {
        this.restClient = restClient;
        this.jsonpMapper = jsonpMapper;
    }

    public boolean indicesExists(String indexName) throws IOException {
        Request request = new Request("HEAD", "/" + indexName);

        try {
            Response response = restClient.performRequest(request);
            return response.getStatusLine().getStatusCode() == 200;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public JsonNode searchMatchAll(String indexName) throws IOException {
        Request request = new Request("GET", "/" + indexName + "/_search");
        request.setJsonEntity("{\"query\": {\"match_all\": {}}, \"size\": " + SEARCH_SIZE + "}");

        Response response = restClient.performRequest(request);

        String jsonResponse = EntityUtils.toString(response.getEntity());

        return jsonpMapper.objectMapper().readTree(jsonResponse);
    }
}
