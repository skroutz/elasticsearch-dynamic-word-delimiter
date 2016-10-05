package org.skroutz.elasticsearch.action.support;

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchResponse;

import java.util.HashSet;
import java.util.Set;

public class WordDelimiterActionListener implements ActionListener<SearchResponse> {
	private static Set<String> protectedWords = new HashSet<String>();

	public void onResponse(SearchResponse response) {
		SearchHit[] hits = response.getHits().hits();
		Set<String> localProtectedWords = new HashSet<String>();

		String word;
		for (SearchHit hit : hits) {
			word = hit.getSource().get("word").toString();
			localProtectedWords.add(word);
		}

		protectedWords = localProtectedWords;
	}

	public void onFailure(Throwable e) {
		protectedWords = new HashSet<String>();
	}
	
	public static Set<String> protectedWords() {
		return protectedWords;
	}
}
