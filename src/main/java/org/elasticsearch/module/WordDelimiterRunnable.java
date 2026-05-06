package org.elasticsearch.module;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.support.WordDelimiterActionListener;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.plugin.WordDelimiterPlugin;

import javax.net.ssl.SSLContext;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import org.apache.http.ssl.SSLContextBuilder;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

public class WordDelimiterRunnable extends AbstractRunnable {
  public static final TimeValue REFRESH_INTERVAL = TimeValue.timeValueMinutes(5);
  public static final TimeValue BACKOFF_TIME = TimeValue.timeValueSeconds(2);
  public static final String INDEX_NAME = "protected_words";
  public static final int RESULTS_SIZE = 10000;
  public static final int PORT = 9200;

  private volatile boolean running;
  private final String index;
  private final long interval;
  private final long backoffTime;
  private String httpHostName;
  private final int httpPort;
  private final String httpScheme;
  private static final Logger logger = Loggers.getLogger(WordDelimiterRunnable.class, "WordDelimiter", "Runnable");

  private final RestClient restClient;
  private final CustomElasticsearchClient customEsClient;
  private final CustomElasticsearchAsyncClient customEsAsyncClient;

  private BasicCredentialsProvider credentialsProvider;
  private SSLContext sslContext;

  public WordDelimiterRunnable(Settings settings) {
    this.index = settings.get(
        "plugin.dynamic_word_delimiter.protected_words_index",
        INDEX_NAME);
    this.interval = settings.getAsTime(
        "plugin.dynamic_word_delimiter.refresh_interval",
        REFRESH_INTERVAL).getMillis();
    this.backoffTime = settings.getAsTime(
        "plugin.dynamic_word_delimiter.refresh_interval",
        BACKOFF_TIME).getMillis();
    this.httpPort = settings.getAsInt(
        "plugin.dynamic_word_delimiter.http_port",
        PORT);
    this.httpHostName = System.getenv("ES_CLUSTER_NAME");
    this.httpScheme = settings.get(
        "plugin.dynamic_word_delimiter.http_scheme",
        "http");

    this.httpHostName = (this.httpHostName != null) ? this.httpHostName + "-es-http" : "localhost";

    HttpHost httpHost = new HttpHost(httpHostName, httpPort, httpScheme);
    JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
    RestClientBuilder restClientBuilder = RestClient.builder(httpHost);

    try (SecureString username = WordDelimiterPlugin.USERNAME.get(settings);
        SecureString password = WordDelimiterPlugin.PASSWORD.get(settings)) {

      if (!username.toString().isBlank() && !password.toString().isBlank()) {
        this.credentialsProvider = new BasicCredentialsProvider();
        this.credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(username.toString(), password.toString()));
      }
    }

    if (httpScheme.equalsIgnoreCase("https")) {
      CertificateFactory certFactory = null;
      InputStream certStream = null;
      Certificate caCert = null;
      KeyStore trustStore = null;

      try {
        certFactory = CertificateFactory.getInstance("X.509");
      } catch (CertificateException e) {
        e.printStackTrace();
      }

      try {
        certStream = Files.newInputStream(Paths.get("/usr/share/elasticsearch/config/http-certs/ca.crt"));
      } catch (IOException e) {
        e.printStackTrace();
      }

      try {
        caCert = certFactory.generateCertificate(certStream);
      } catch (CertificateException e) {
        e.printStackTrace();
      }

      try {
        certStream.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

      try {
        trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca-anchor", caCert);
      } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
        e.printStackTrace();
      }

      SSLContextBuilder sslBuilder = SSLContextBuilder.create();
      try {
        sslBuilder.loadTrustMaterial(trustStore, null);
      } catch (NoSuchAlgorithmException | KeyStoreException e) {
        e.printStackTrace();
      }
      try {
        this.sslContext = sslBuilder.build();
      } catch (KeyManagementException | NoSuchAlgorithmException e) {
        e.printStackTrace();
      }
    }

    restClientBuilder.setHttpClientConfigCallback(new HttpClientConfigCallback() {
      @Override
      public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
        if (credentialsProvider != null) {
          httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        if (sslContext != null) {
          httpClientBuilder.setSSLContext(sslContext);
        }

        return httpClientBuilder;
      }
    });

    this.restClient = restClientBuilder.build();
    customEsClient = new CustomElasticsearchClient(restClient, jsonpMapper);
    customEsAsyncClient = new CustomElasticsearchAsyncClient(restClient, jsonpMapper);
  }

  public void stopRunning() {
    running = false;
  }

  public void close() throws IOException {
    if (restClient != null) {
      restClient.close();
    }
  }

  @Override
  public void onFailure(Exception t) {
    logger.warn(t.getMessage());
  }

  private void getAllProtectedWords(WordDelimiterActionListener listener) throws IOException {
    if (customEsClient.indicesExists(index)) {
      customEsAsyncClient.searchMatchAll(index)
          .whenComplete((response, exception) -> {
            if (exception == null) {
              listener.onResponse(response);
            } else {
              if (exception.getCause() instanceof ConnectException) {
                logger.error("Error connecting to Elasticsearch: " + exception.getMessage());
              } else {
                logger.error("Error fetching protected words: " + exception.getMessage());
              }
              listener.onFailure((Exception) exception);
            }
          });
    } else {
      logger.warn("Index [{}] not found", index);
    }
  }

  protected void doRun() {
    running = true;

    logger.debug("New thread spawned");

    WordDelimiterActionListener listener = WordDelimiterActionListener.getInstance();
    Boolean waitAfterIOError = false;

    while (running && !Thread.currentThread().isInterrupted()) {
      try {

        if (waitAfterIOError) {
          Thread.sleep(backoffTime);
          waitAfterIOError = false;
        }
        getAllProtectedWords(listener);

        logger.debug("Cache updater thread is suspended");
        Thread.sleep(interval);
        logger.debug("Cache updater thread is resumed");
      } catch (InterruptedException e) {
        logger.warn("Interrupted exception: breaking");
        Thread.currentThread().interrupt();
        break;
      } catch (IllegalStateException e) {
        logger.error("Illegal state exception: supressing: " + e.getMessage());
      } catch (ConnectException e) {
        logger.error("Connect exception: supressing: " + e.getMessage());
      } catch (IOException e) {
        logger.error("IO exception: supressing: " + e.getMessage());
        waitAfterIOError = true;
      } catch (Exception e) {
        logger.error("Exception: supressing: " + e.getMessage());
      }
    }
  }
}
