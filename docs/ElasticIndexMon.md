### com.esri.rttest.mon.ElasticIndexMon

- Monitors a Elasticsearch Index count and measures and reports rate of change in count.  
- When the tool starts it gets the current count and starts sampling count every sampleRateSec seconds (defaults to 5 seconds).
- When count changes the tool starts collecting sample points. 
- After collecting three points the output will use linear regression to estimate the rate of change.
- After count stops changing the final line will give the count received and the best fit linear approximation of the rate.  The last sample is excluded from the final rate calculation.
- After reporting the final count and rate the tool will continue monitoring for count changes.  Use **Ctrl-C** to stop.

<pre>
java -cp target/rttest.jar com.esri.rttest.mon.ElasticIndexMon
Usage: ElasticIndexMon (ElasticsearchServerPort) (Index/Type) [sampleRateSec=5] [username=""] [password==""] 
</pre>

Example:

<pre>
java -cp target/rttest.jar com.esri.rttest.mon.ElasticIndexMon 172.17.2.5:9200 satellites/satellites 60

- Elasticsearch running on 172.17.2.5 on default port of 9200
- The index name is satellites and type name is satellites (satellites/satellites)
- If the system doesn't require a password you can use dash
- Sample every 60 seconds
</pre>

Example Output:
<pre>
1,1518057198604,4252762
2,1518057258692,5190695
3,1518057318586,5366873,9289
4,1518057378587,7404803,16059
5,1518057438602,7581889,14790
6,1518057498623,9640049,17215
7,1518057558585,10017645,16912
Removing: 1518057558585,10017645
7462604 , 17215.24, 2.1803
</pre>

- Sample Lines: Sample Number,System Time in Milliseconds,Count,(Rate /s)
- Final Line: Total Count Change, Rate, Rate Std Deviation 

### DC/OS

For DC/OS if you deployed Elastic name "sats-ds01" then you can access via an endpoint like: `data.sats-ds01.l4lb.thisdcos.directory`

If you used the default username/password.

<pre>
curl -u elastic:changeme data.sats-ds01.l4lb.thisdcos.directory:9200
curl -u elastic:changeme data.sats-ds01.l4lb.thisdcos.directory:9200/_aliases?pretty
</pre>

Example Command:
<pre>
java -cp target/rttest.jar com.esri.rttest.mon.ElasticIndexMon data.sats-ds01.l4lb.thisdcos.directory:9200 planes-bat/planes-bat 60 elastic changeme 
</pre>

**Note:** You may need to use larger sampleRateSec; to prevent false endings for slow loading data. This often happens if the index refresh is disabled for bulk loading.

You will get false readings if the count goes down during loading.  For example the index is deleted before it is loaded. 


### GeoEvent

**NOTE:** For GeoEvent you can get the username/password for the spatiotemportal datastore using Datastore tool "listadmins". 


