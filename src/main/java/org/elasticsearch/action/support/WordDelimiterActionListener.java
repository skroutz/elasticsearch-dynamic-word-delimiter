package org.elasticsearch.action.support;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.logging.Loggers;

import com.fasterxml.jackson.databind.JsonNode;

public class WordDelimiterActionListener implements ActionListener<JsonNode> {

  private static WordDelimiterActionListener instance = null;
  private static final Logger logger = Loggers.getLogger(
          WordDelimiterActionListener.class,
          "WordDelimiter", "ActionListener"
  );

  private Set<String> protectedWords;

  protected WordDelimiterActionListener() {
    protectedWords = new HashSet<>();
  }

  private static HashSet<String> parseFromJsonNode(JsonNode response) {
    HashSet<String> protectedWords = new HashSet<>();
    if (response != null && response.has("hits") && response.path("hits").has("hits")) {
      for (JsonNode hit : response.path("hits").path("hits")) {
        JsonNode source = hit.path("_source");
        if (source != null && source.has("word")) {
          String word = source.path("word").asText();
          logger.error("Found protected word: " + word);
          protectedWords.add(word);
        }
      }
    }
    return protectedWords;
  }

  @Override
  public void onResponse(JsonNode response) {
    logger.error("Updating protected words in memory");

    protectedWords = parseFromJsonNode(response);
  }

  @Override
  public void onFailure(Exception e) {
    logger.error("`onFailure` called");
    logger.error(e.getMessage());
  }

  public Set<String> getProtectedWords() {
    return protectedWords;
  }

  public synchronized static WordDelimiterActionListener getInstance() {
    if(instance == null) {
      instance = new WordDelimiterActionListener();
    }

    return instance;
  }
}
