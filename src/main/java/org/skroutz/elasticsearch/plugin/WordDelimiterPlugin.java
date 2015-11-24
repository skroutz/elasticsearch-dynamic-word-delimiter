package org.skroutz.elasticsearch.plugin;

import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.common.component.LifecycleComponent;
import org.skroutz.elasticsearch.module.WordDelimiterService;
import org.skroutz.elasticsearch.index.analysis.WordDelimiterBinderProcessor;

import java.util.Collection;
import static org.elasticsearch.common.collect.Lists.newArrayList;

public class WordDelimiterPlugin extends AbstractPlugin {

    public String name() {
        return "skroutz-word-delimiter";
    }

    public String description() {
        return "Word delimiter customized for the needs of www.skroutz.gr";
    }

	public void onModule(AnalysisModule module) {
		module.addProcessor(new WordDelimiterBinderProcessor());
	}

	@Override
	public Collection<Class<? extends LifecycleComponent>> services() {
		Collection<Class<? extends LifecycleComponent>> services = newArrayList();
		services.add(WordDelimiterService.class);
		return services;
	}
}
