package org.elasticsearch.plugin;

import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.module.WordDelimiterService;
import org.elasticsearch.plugins.Plugin;

import java.util.*;
import java.util.function.Function;

import org.skroutz.elasticsearch.index.analysis.WordDelimiterTokenFilterFactory;

public class WordDelimiterPlugin extends Plugin implements AnalysisPlugin {

  private final Collection<Class<? extends LifecycleComponent>> services =
          new ArrayList();

  public WordDelimiterPlugin() {
    services.add(WordDelimiterService.class);
  }

  @Override
  public Collection<Class<? extends LifecycleComponent>> getGuiceServiceClasses() {
    return Collections.singleton(WordDelimiterService.class);
  }

  @Override
  public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
    return Collections.singletonMap("dynamic_word_delimiter",
            WordDelimiterTokenFilterFactory::new);
  }

  @Override
  public List<Setting<?>> getSettings() {

    List<Setting<?>> settings = Arrays.asList(
            new Setting<>(
                    "plugin.dynamic_word_delimiter.protected_words_index",
                    "protected_words",
                    Function.identity(),
                    Setting.Property.NodeScope),
            new Setting<>(
                    "plugin.dynamic_word_delimiter.protected_words_type",
                    "word",
                    Function.identity(),
                    Setting.Property.NodeScope),
            Setting.timeSetting(
                    "plugin.dynamic_word_delimiter.refresh_interval",
                    TimeValue.timeValueMinutes(5),
                    Setting.Property.NodeScope)
    );

    return settings;
  }

}
