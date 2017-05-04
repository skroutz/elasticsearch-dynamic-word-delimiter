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

import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugin.WordDelimiterPlugin;

import static org.elasticsearch.test.ESTestCase.TestAnalysis;

import java.io.IOException;

import static org.elasticsearch.test.ESTestCase.createTestAnalysis;

public class AnalysisTestsHelper {

  public static TokenFilterFactory filterFactory(Settings indexSettings, String filterName) throws IOException {

      TestAnalysis analysis = createTestAnalysis(new Index("test", "_na_"),
              indexSettings, new WordDelimiterPlugin());

      TokenFilterFactory filterFactory = analysis.tokenFilter.get(filterName);

      return filterFactory;
  }
}
