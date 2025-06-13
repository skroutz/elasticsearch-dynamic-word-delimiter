package org.skroutz.elasticsearch.index.analysis;

import org.elasticsearch.test.ESIntegTestCase;
import org.hamcrest.MatcherAssert;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.Settings;
import static org.elasticsearch.common.settings.Settings.builder;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.Matchers.instanceOf;

import static org.skroutz.elasticsearch.index.analysis.AnalysisTestsHelper.filterFactory;

public class SimpleWordDelimiterTokenFilterTests extends ESIntegTestCase {

  @Test
  public void testWordDelimiterTokenFilter() throws IOException {
    Settings indexSettings = builder()
        .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .put("path.home", "/")
        .build();

    TokenFilterFactory filterFactory = filterFactory(indexSettings, "my_word_delimiter");

    MatcherAssert.assertThat(filterFactory, instanceOf(WordDelimiterTokenFilterFactory.class));
  }
}
