package org.skroutz.elasticsearch.index.analysis;

import org.hamcrest.MatcherAssert;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.junit.Test;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.hamcrest.Matchers.instanceOf;
import static org.skroutz.elasticsearch.index.analysis.AnalysisTestsHelper.filterFactory;

public class SimpleWordDelimiterTokenFilterTests {

  @Test
  public void testWordDelimiterTokenFilter() {
    Settings indexSettings = settingsBuilder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", "dynamic_word_delimiter")
        .build();

    TokenFilterFactory filterFactory = filterFactory(indexSettings, "my_word_delimiter");

    MatcherAssert.assertThat(filterFactory, instanceOf(WordDelimiterTokenFilterFactory.class));
  }
}
