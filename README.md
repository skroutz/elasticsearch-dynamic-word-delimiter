Dynamic Word Delimiter plugin for ElasticSearch
==============================================

This plugin is based on the
[Word Delimiter Token Filter](https://github.com/elastic/elasticsearch/blob/v1.5.2/docs/reference/analysis/tokenfilters/word-delimiter-tokenfilter.asciidoc)
that is included in Elasticsearch, which in turn uses the [Lucene implementation](https://lucene.apache.org/core/4_10_4/analyzers-common/org/apache/lucene/analysis/miscellaneous/WordDelimiterFilter.html)
. For a list of available settings and their function, visit the above link.

The main advantage over the original token filter, is the ability to dynamically
specify protected words. These words are safe from any transformation and will
remain intact. The user can index documents on a dedicated index and type. The
structure of the documents is very minimal, a single field which holds the word
that should not be delimited. When the plugin is loaded, it will start a service
that runs on the background and is responsible for querying the index and
updating an in-memory data structure that holds the protected words. This data
structure is used in the word delimiter token filter to decide whether the
current word should be delimited or not. The used type is a `HashSet`, so lookup
for each word runs in `O(1)`.
Note: The `protected_words` setting described in the reference above is still
respected from the filter and can be used simultaneously with the dynamically
defined words.

Versions
---------

Dynamic Word Delimiter Plugin | ElasticSearch | Branch |
------------------------------|---------------|--------|
5.4.2.1                       | 5.4.2         | 5.4.2  |
5.4.0.1                       | 5.4.0         | 5.4.0  |
1.0.1                         | 1.5.2         | 1.5.2  |
1.0.0                         | 1.5.2         | 1.5.2  |

Installation
-------------

To list all plugins in current installation:

    sudo bin/elasticsearch-plugin list

In order to install the latest version of the plugin, simply run:

    sudo bin/elasticsearch-plugin install gr.skroutz:elasticsearch-dynamic-word-delimiter:5.4.2.1

In order to install a previous (1.x.x) version of the plugin, simply run:

    sudo bin/plugin -install gr.skroutz/elasticsearch-dynamic-word-delimiter/1.0.1

To remove a plugin (5.x.x):

    sudo bin/elasticsearch-plugin remove <plugin_name>

### YML configuration example

There are three available settings that you can override:

- `protected_words_index` (index name),
- `protected_words_type` (index type),
- `refresh_interval` (interval for updating the list of dynamic protected words)

Add the settings below in your `elasticsearch.yml` config file.

    plugin.dynamic_word_delimiter.protected_words_index: protected_words
    plugin.dynamic_word_delimiter.protected_words_type: word
    plugin.dynamic_word_delimiter.refresh_interval: 5m

These are the default values, you can omit the above step if you do not wish to
change them.
Note: the plugin runs independently on each node. That means that the nodes may
be out of sync for the maximum amount in `refresh_interval`.

Example Usage
-------------
```bash
    # Create the protected_words index
    $ curl -XPUT 'http://localhost:9200/protected_words/'
    {"acknowledged":true}

    # Index a few protected words (view the Notes section on why we index both lowercase/uppercase form)
    $ curl -XPOST 'http://localhost:9200/protected_words/word' -d '{"word": "4g"}'
    {"_index":"protected_words","_type":"word","_id":"AViHOwEsHo94o8jBG6vN","_version":1,"created":true}

    $ curl -XPOST 'http://localhost:9200/protected_words/word' -d '{"word": "4G"}'
    {"_index":"protected_words","_type":"word","_id":"AViHO5pxHo94o8jBG6vO","_version":1,"created":true}

    # View the documents on our index
    $ curl 'http://localhost:9200/protected_words/word/_search?pretty=true' -d '{"query": {"match_all": {}}}'
    {
      "took" : 1,
      "timed_out" : false,
      "_shards" : {
        "total" : 1,
        "successful" : 1,
        "failed" : 0
      },
      "hits" : {
        "total" : 2,
        "max_score" : 1.0,
        "hits" : [ {
          "_index" : "protected_words",
          "_type" : "word",
          "_id" : "AViHOwEsHo94o8jBG6vN",
          "_score" : 1.0,
          "_source":{"word": "4g"}
        }, {
          "_index" : "protected_words",
          "_type" : "word",
          "_id" : "AViHO5pxHo94o8jBG6vO",
          "_score" : 1.0,
          "_source":{"word": "4G"}
        } ]
      }
    }

    # Create a test index with the new dynamic word delimiter filter
    $ curl -XPUT 'http://localhost:9200/test_index' -d '{
      "settings": {
        "analysis": {
          "analyzer": {
            "query_analyzer": {
              "type": "custom",
              "tokenizer": "standard",
              "filter": ["query_splitter"]
            }
          },
          "filter": {
            "query_splitter": {
              "type": "dynamic_word_delimiter",
              "generate_word_parts": true,
              "generate_number_parts": true,
              "catenate_words": false,
              "catenate_numbers": false,
              "catenate_all": false,
              "preserve_original": false,
              "split_on_case_change": false,
              "split_on_numerics": true,
              "stem_english_possesive": true
            }
          }
        }
      }
    }'
    {"acknowledged":true}

    # Test token filter

    # Output before indexing '4g'
    $ curl 'http://localhost:9200/test_index/_analyze?analyzer=query_analyzer&pretty=true' -d '4g connection'
    {
      "tokens" : [ {
        "token" : "4",
        "start_offset" : 0,
        "end_offset" : 1,
        "type" : "<ALPHANUM>",
        "position" : 1
      }, {
        "token" : "g",
        "start_offset" : 1,
        "end_offset" : 2,
        "type" : "<ALPHANUM>",
        "position" : 2
      }, {
        "token" : "connection",
        "start_offset" : 3,
        "end_offset" : 13,
        "type" : "<ALPHANUM>",
        "position" : 3
        } ]
    }

    # Output after indexing '4g' as a protected word
    $ curl 'http://localhost:9200/test_index/_analyze?analyzer=query_analyzer&pretty=true' -d '4g connection'
    {
      "tokens" : [ {
        "token" : "4g",
        "start_offset" : 0,
        "end_offset" : 2,
        "type" : "<ALPHANUM>",
        "position" : 1
      }, {
        "token" : "connection",
        "start_offset" : 3,
        "end_offset" : 13,
        "type" : "<ALPHANUM>",
        "position" : 2
        } ]
    }

    # Output before indexing '4G'
    $ curl 'http://localhost:9200/test_index/_analyze?analyzer=query_analyzer&pretty=true' -d 'router 4G'
    {
      "tokens" : [ {
        "token" : "router",
        "start_offset" : 0,
        "end_offset" : 6,
        "type" : "<ALPHANUM>",
        "position" : 1
      }, {
        "token" : "4",
        "start_offset" : 7,
        "end_offset" : 8,
        "type" : "<ALPHANUM>",
        "position" : 2
      }, {
        "token" : "G",
        "start_offset" : 8,
        "end_offset" : 9,
        "type" : "<ALPHANUM>",
        "position" : 3
      } ]
    }

    # Output after indexing '4G' as a protected word
    $ curl 'http://localhost:9200/test_index/_analyze?analyzer=query_analyzer&pretty=true' -d 'router 4G'
    {
      "tokens" : [ {
        "token" : "router",
        "start_offset" : 0,
        "end_offset" : 6,
        "type" : "<ALPHANUM>",
        "position" : 1
      }, {
        "token" : "4G",
        "start_offset" : 7,
        "end_offset" : 9,
        "type" : "<ALPHANUM>",
        "position" : 2
        } ]
    }
```

Notes
-----
When we indexed words in the `protected_words` index we added both
forms, lowercase and uppercase. This is required if you wish to treat both
`4g` and `4G` as protected words, in case the `word delimiter` filter is applied
before the `lowercase` filter.

## Authors

* [Bill Kolokithas](https://github.com/freestyl3r)
* [Peter Markou](https://github.com/m-Peter)

## License

elasticsearch-dynamic-word-delimiter is licensed under the Apache Software License, Version 2.0.
