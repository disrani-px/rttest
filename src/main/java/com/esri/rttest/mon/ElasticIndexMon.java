/*
 * (C) Copyright 2017 David Jennings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     David Jennings
 */
/**
 * Monitors an Elasticsearch Index/Type.
 * Periodically does a count and when count is changing collects samples.
 * After three samples are made outputs rates based on linear regression.
 * After counts stop changing outputs the final rate and last estimated rate.
 *
 * Creator: David Jennings
 */
package com.esri.rttest.mon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author david
 */
public class ElasticIndexMon {

    private static final Logger log = LogManager.getLogger(ElasticIndexMon.class);

    class CheckCount extends TimerTask {

        long cnt1;
        long cnt2;
        long startCount;
        long endCount;
        int numSamples;
        HashMap<Long, Long> samples;        
        long t1;
        long t2;
        SimpleRegression regression;

        public CheckCount() {
            cnt1 = 0;
            cnt2 = -1;
            startCount = 0;
            numSamples = 0;
            t1 = 0L;
            t2 = 0L;
            regression = new SimpleRegression();
        }

        
        boolean inCounting() {
            if (cnt1 > 0) 
                return true;
            else 
                return false;
        }
        
        HashMap<Long, Long> getSamples() {
            return samples;
        }
        
        
        long getStartCount() {
            return startCount;
        }
        
        long getEndCount() {
            return endCount;
        }
                
        
        @Override
        public void run() {
            try {

                log.info("Checking Count");

                // index/type
                String url = "http://" + esServer + "/" + indexType + "/_count";
                SSLContext sslContext = SSLContext.getInstance("SSL");

                CredentialsProvider provider = new BasicCredentialsProvider();
                UsernamePasswordCredentials credentials
                        = new UsernamePasswordCredentials(user, userpw);
                provider.setCredentials(AuthScope.ANY, credentials);

                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        if (sendStdout) System.out.println("getAcceptedIssuers =============");
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs,
                            String authType) {
                        if (sendStdout) System.out.println("checkClientTrusted =============");
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs,
                            String authType) {
                        if (sendStdout) System.out.println("checkServerTrusted =============");
                    }
                }}, new SecureRandom());

                CloseableHttpClient httpclient = HttpClients
                        .custom()
                        .setDefaultCredentialsProvider(provider)
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .build();

                HttpGet request = new HttpGet(url);
                CloseableHttpResponse response = httpclient.execute(request);
                BufferedReader rd = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent()));

                Header contentType = response.getEntity().getContentType();
                String ct = contentType.getValue().split(";")[0];

                int responseCode = response.getStatusLine().getStatusCode();

                String line;
                StringBuilder result = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }

                JSONObject json = new JSONObject(result.toString());
                request.abort();
                response.close();

                cnt1 = json.getInt("count");

                t1 = System.currentTimeMillis();

                if (cnt2 == -1 || cnt1 < cnt2) {
                    cnt2 = cnt1;
                    startCount = cnt1;
                    endCount = cnt1;                    
                    regression = new SimpleRegression();
                    samples = new HashMap<>();
                    numSamples = 0;                    

                } else if (cnt1 > cnt2) {
                    // Add to Linear Regression
                    regression.addData(t1, cnt1);
                    samples.put(t1, cnt1);                    
                    // Increase number of samples
                    numSamples += 1;
                    if (numSamples > 2) {
                        double rcvRate = regression.getSlope() * 1000;
                        System.out.format("%d,%d,%d,%.0f\n", numSamples, t1, cnt1, rcvRate);
                    } else {
                        System.out.format("%d,%d,%d\n", numSamples, t1, cnt1);
                    }

                } else if (cnt1 == cnt2 && numSamples > 0) {
                    numSamples -= 1;
                    // Remove the last sample
                    regression.removeData(t2, cnt2);
                    samples.remove(t2, cnt2);                    
                    if (sendStdout) System.out.println("Removing: " + t2 + "," + cnt2);
                    // Output Results
                    long cnt = cnt2 - startCount;
                    double rcvRate = regression.getSlope() * 1000;  // converting from ms to seconds

                    if (numSamples > 5) {
                        double rateStdErr = regression.getSlopeStdErr();
                        if (sendStdout) System.out.format("%d , %.2f, %.4f\n", cnt, rcvRate, rateStdErr);
                    } else if (numSamples >= 2) {
                        if (sendStdout) System.out.format("%d , %.2f\n", cnt, rcvRate);
                    } else {
                        if (sendStdout) System.out.println("Not enough samples to calculate rate. ");
                    }

                    // Reset 
                    cnt1 = -1;
                    cnt2 = -1;
                    t1 = 0L;
                    t2 = 0L;


                } 

                cnt2 = cnt1;
                t2 = t1;

            } catch (IOException | UnsupportedOperationException | KeyManagementException | NoSuchAlgorithmException | JSONException e) {
                log.error("ERROR", e);

            }

        }

    }

    Timer timer;
    String esServer;
    String indexType;
    String user;
    String userpw;
    int sampleRateSec;
    boolean sendStdout;     

    public ElasticIndexMon(String esServer, String indexType, String user, String userpw, int sampleRateSec, boolean sendStdout) {

//        esServer = "ags:9220";
//        index = "FAA-Stream/FAA-Stream";
//        user = "els_ynrqqnh";
//        userpw = "8jychjwcgn";
        this.esServer = esServer;
        this.indexType = indexType;
        this.user = user;
        this.userpw = userpw;
        this.sampleRateSec = sampleRateSec;
        this.sendStdout = sendStdout;
    }

    public void run() {
        try {

            timer = new Timer();
            timer.schedule(new ElasticIndexMon.CheckCount(), 0, sampleRateSec * 1000);

        } catch (Exception e) {
            log.error("ERROR", e);
        }

    }

    public static void main(String[] args) {

        String elasticSearchServerPort = "";
        String indexType = "";
        String username = "";   // default to empty string
        String password = "";  // default to empty string
        int sampleRateSec = 5; // default to 5 seconds.  
        Boolean sendStdout = true;

        log.info("Entering application.");
        int numargs = args.length;
        if (numargs != 2 && numargs != 4 && numargs != 5) {
            System.err.print("Usage: ElasticIndexMon [ElasticsearchServerPort] [Index/Type] (username) (password) (sampleRateSec) \n");
        } else {
            elasticSearchServerPort = args[0];
            indexType = args[1];

            if (numargs >= 4) {
                username = args[3];
                password = args[4];
            }

            if (numargs == 5) {
                sampleRateSec = Integer.parseInt(args[1]);
            }

        }

        ElasticIndexMon t = new ElasticIndexMon(elasticSearchServerPort, indexType, username, password, sampleRateSec, sendStdout);
        t.run();


    }
}
