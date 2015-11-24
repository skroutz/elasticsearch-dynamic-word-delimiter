package org.skroutz.elasticsearch.action.support;

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchResponse;

import java.util.HashSet;

public class WordDelimiterActionListener implements ActionListener<SearchResponse> {

	public static HashSet<String> protectedWords = new HashSet<String>();

	@Override
	public final void onResponse(SearchResponse response) {
		Object obj;
		HashSet<String> localProtectedWords = new HashSet<String>();

		for (SearchHit hit : response.getHits().hits()) {
			obj = hit.getSource().get("word");
			if (obj instanceof String)
				localProtectedWords.add(obj.toString());
		}

		protectedWords = localProtectedWords;
	}

	@Override
	public final void onFailure(Throwable e) {
		protectedWords = new HashSet<String>();
	}
}
