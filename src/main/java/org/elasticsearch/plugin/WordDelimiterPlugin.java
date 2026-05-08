package org.elasticsearch.plugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.module.WordDelimiterRunnable;
import org.elasticsearch.module.WordDelimiterService;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.skroutz.elasticsearch.index.analysis.WordDelimiterTokenFilterFactory;

public class WordDelimiterPlugin extends Plugin implements AnalysisPlugin {

  private final Settings settings;

  public WordDelimiterPlugin(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
    return Collections.singletonMap("dynamic_word_delimiter",
            WordDelimiterTokenFilterFactory::new);
  }

  @Override
  public Collection<Object> createComponents(PluginServices services) {
    WordDelimiterService wordDelimiterService = new WordDelimiterService(
        this.settings,
        services.client(),
        services.clusterService()
    );

    return List.of(wordDelimiterService);
  }

  @Override
  public List<Setting<?>> getSettings() {
    return Arrays.asList(
      new Setting<>(
        "plugin.dynamic_word_delimiter.protected_words_index",
        WordDelimiterRunnable.INDEX_NAME,
        Function.identity(),
        Setting.Property.NodeScope),
      Setting.timeSetting(
        "plugin.dynamic_word_delimiter.refresh_interval",
        WordDelimiterRunnable.REFRESH_INTERVAL,
        Setting.Property.NodeScope)
    );
  }

}
