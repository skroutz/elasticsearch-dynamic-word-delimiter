package org.elasticsearch.action.support;

import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchResponse;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class WordDelimiterActionListener implements ActionListener<SearchResponse> {

  private static WordDelimiterActionListener instance = null;
  private static final Logger logger = Loggers.getLogger(
          WordDelimiterActionListener.class,
          "WordDelimiter", "ActionListener"
  );

  private Set<String> protectedWords;

  protected WordDelimiterActionListener() {
    protectedWords = new HashSet<String>();
  }

  public void onResponse(SearchResponse response) {
    SearchHit[] hits = response.getHits().getHits();
    Set<String> localProtectedWords = new HashSet<String>();

    String word;
    for (SearchHit hit : hits) {
      word = hit.getSourceAsMap().get("word").toString();
      localProtectedWords.add(word);
    }

    protectedWords = localProtectedWords;
  }

  @Override
  public void onFailure(Exception e) {
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
