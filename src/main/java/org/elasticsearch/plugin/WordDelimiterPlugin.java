package org.elasticsearch.plugin;

import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.module.WordDelimiterService;
import org.skroutz.elasticsearch.index.analysis.WordDelimiterBinderProcessor;

import java.util.Collection;
import static org.elasticsearch.common.collect.Lists.newArrayList;

public class WordDelimiterPlugin extends AbstractPlugin {
  @SuppressWarnings("rawtypes")
  private final Collection<Class<? extends LifecycleComponent>> services = newArrayList();

  public WordDelimiterPlugin() {
    services.add(WordDelimiterService.class);
  }

  public String name() {
    return "dynamic-word-delimiter";
  }

  public String description() {
    return "Dynamic word delimiter customized for the needs of www.skroutz.gr";
  }

  public void onModule(AnalysisModule module) {
    module.addProcessor(new WordDelimiterBinderProcessor());
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Collection<Class<? extends LifecycleComponent>> services() {
    return services;
  }
}
