package org.elasticsearch.action.support;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.search.SearchHit;

public class WordDelimiterActionListener implements ActionListener<SearchResponse> {

  private static WordDelimiterActionListener instance = null;
  private static final Logger logger = Loggers.getLogger(
          WordDelimiterActionListener.class,
          "WordDelimiter", "ActionListener"
  );

  private Set<String> protectedWords;

  protected WordDelimiterActionListener() {
    protectedWords = new HashSet<>();
  }

  private static HashSet<String> parseFromSearchResponse(SearchResponse response) {
    HashSet<String> protectedWords = new HashSet<>();
    if (response == null || response.getHits() == null) {
      return protectedWords;
    }
    for (SearchHit hit : response.getHits().getHits()) {
      Map<String, Object> source = hit.getSourceAsMap();
      if (source != null && source.get("word") != null) {
        String word = source.get("word").toString();
        logger.debug("Found protected word: " + word);
        protectedWords.add(word);
      }
    }
    return protectedWords;
  }

  @Override
  public void onResponse(SearchResponse response) {
    logger.debug("Updating protected words in memory");

    protectedWords = parseFromSearchResponse(response);
  }

  @Override
  public void onFailure(Exception e) {
    logger.warn("Failed to fetch protected words: ", e.getMessage());
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
