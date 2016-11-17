/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.skroutz.elasticsearch.index.analysis;

import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;

public class AnalysisTestsHelper {

  public static TokenFilterFactory filterFactory(Settings indexSettings, String filterName) {
    Index index = new Index("test");

    Injector parentInjector = new ModulesBuilder()
        .add(new SettingsModule(indexSettings),
            new EnvironmentModule(new Environment(indexSettings)),
            new IndicesAnalysisModule()).createInjector();
    Injector injector = new ModulesBuilder()
        .add(new IndexSettingsModule(index, indexSettings),
            new IndexNameModule(index),
            new AnalysisModule(indexSettings,
                parentInjector.getInstance(IndicesAnalysisService.class))
            .addProcessor(new WordDelimiterBinderProcessor()))
        .createChildInjector(parentInjector);

    AnalysisService analysisService = injector.getInstance(AnalysisService.class);
    return analysisService.tokenFilter(filterName);
  }
}
