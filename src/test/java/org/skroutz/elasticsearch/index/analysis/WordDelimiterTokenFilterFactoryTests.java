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

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.elasticsearch.test.ESTokenStreamTestCase;

import java.io.IOException;
import java.io.StringReader;

import static org.skroutz.elasticsearch.index.analysis.AnalysisTestsHelper.filterFactory;

public class WordDelimiterTokenFilterFactoryTests extends ESTokenStreamTestCase {

  private final static String TYPE_NAME = "dynamic_word_delimiter";
  private final static String FILTER_NAME = "my_word_delimiter";

  public void testDefault() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    String source = "PowerShot 500-42 wi-fi wi-fi-4000 j2se O'Neil's";
    String[] expected = new String[]{"Power", "Shot", "500", "42", "wi", "fi", "wi", "fi", "4000", "j", "2", "se", "O", "Neil"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  public void testCatenateWords() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .put("index.analysis.filter.my_word_delimiter.catenate_words", "true")
        .put("index.analysis.filter.my_word_delimiter.generate_word_parts", "false")
        .build();

    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    String source = "PowerShot 500-42 wi-fi wi-fi-4000 j2se O'Neil's";
    String[] expected = new String[]{"PowerShot", "500", "42", "wifi", "wifi", "4000", "j", "2", "se", "ONeil"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));

    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  public void testCatenateNumbers() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .put("index.analysis.filter.my_word_delimiter.generate_number_parts", "false")
        .put("index.analysis.filter.my_word_delimiter.catenate_numbers", "true")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    String source = "PowerShot 500-42 wi-fi wi-fi-4000 j2se O'Neil's";
    String[] expected = new String[]{"Power", "Shot", "50042", "wi", "fi", "wi", "fi", "4000", "j", "2", "se", "O", "Neil"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  public void testCatenateAll() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .put("index.analysis.filter.my_word_delimiter.generate_word_parts", "false")
        .put("index.analysis.filter.my_word_delimiter.generate_number_parts", "false")
        .put("index.analysis.filter.my_word_delimiter.catenate_all", "true")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    String source = "PowerShot 500-42 wi-fi wi-fi-4000 j2se O'Neil's";
    String[] expected = new String[]{"PowerShot", "50042", "wifi", "wifi4000", "j2se", "ONeil"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  public void testSplitOnCaseChange() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .put("index.analysis.filter.my_word_delimiter.split_on_case_change", "false")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    String source = "PowerShot";
    String[] expected = new String[]{"PowerShot"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(source));
    tokenizer.setReader(new StringReader(source));
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  public void testPreserveOriginal() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .put("index.analysis.filter.my_word_delimiter.preserve_original", "true")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    StringReader source = new StringReader("PowerShot 500-42 wi-fi wi-fi-4000 " +
            "j2se O'Neil's");
    String[] expected = new String[]{"PowerShot", "Power", "Shot", "500-42", "500", "42", "wi-fi", "wi", "fi", "wi-fi-4000", "wi", "fi", "4000", "j2se", "j", "2", "se", "O'Neil's", "O", "Neil"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(source);
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  public void testStemEnglishPossessive() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .put("index.analysis.filter.my_word_delimiter.stem_english_possessive", "false")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    StringReader source = new StringReader("PowerShot 500-42 wi-fi wi-fi-4000 " +
            "j2se O'Neil's");
    String[] expected = new String[]{"Power", "Shot", "500", "42", "wi", "fi", "wi", "fi", "4000", "j", "2", "se", "O", "Neil", "s"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
      tokenizer.setReader(source);
    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  /** Correct offset order when doing both parts and concatenation: PowerShot is a synonym of Power */
  public void testPartsAndCatenate() throws IOException {
    Settings indexSettings = Settings.builder()
        .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
        .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
        .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
        .put("index.analysis.filter.my_word_delimiter.catenate_words", "true")
        .put("index.analysis.filter.my_word_delimiter.generate_word_parts", "true")
        .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    StringReader source = new StringReader("PowerShot");
    String[] expected = new String[]{"Power", "PowerShot", "Shot" };
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(source);

    assertTokenStreamContents(filterFactory.create(tokenizer), expected);
  }

  // Test that it doesn't split a 2-chars string
  public void testTwoChars() throws IOException {
    Settings indexSettings = Settings.builder()
            .put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
            .put("index.analysis.filter.my_word_delimiter.type", TYPE_NAME)
            .put("index.analysis.filter.my_word_delimiter.split_on_numerics", true)
            .put("index.analysis.filter.my_word_delimiter.split_on_case_change", "true")
            .build();
    TokenFilterFactory filterFactory = filterFactory(indexSettings, FILTER_NAME);

    String alphanum = "b1";
    String greek = "α4";
    String nonAlphanum = "a.";
    String[] expectedAlphanum = new String[]{"b1"};
    String[] expectedGreek = new String[]{"α4"};
    String[] expectedNonAlphanum = new String[]{"a"};
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(alphanum));
    assertTokenStreamContents(filterFactory.create(tokenizer), expectedAlphanum);
    tokenizer.setReader(new StringReader(greek));
    assertTokenStreamContents(filterFactory.create(tokenizer), expectedGreek);
    tokenizer.setReader(new StringReader(nonAlphanum));
    assertTokenStreamContents(filterFactory.create(tokenizer), expectedNonAlphanum);
  }
}
