package org.skroutz.elasticsearch.index.analysis;

import org.elasticsearch.index.analysis.AnalysisModule;

public class WordDelimiterBinderProcessor extends AnalysisModule.AnalysisBinderProcessor {

  @Override
  public void processTokenFilters(TokenFiltersBindings tokenFiltersBindings) {
    tokenFiltersBindings.processTokenFilter("dynamic_word_delimiter", WordDelimiterTokenFilterFactory.class);
  }
}
