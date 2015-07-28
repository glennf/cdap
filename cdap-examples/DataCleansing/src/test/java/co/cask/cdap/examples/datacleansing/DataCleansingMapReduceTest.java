/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.examples.datacleansing;

import co.cask.cdap.api.dataset.lib.cube.AggregationFunction;
import co.cask.cdap.api.dataset.lib.cube.TimeValue;
import co.cask.cdap.api.metrics.MetricDataQuery;
import co.cask.cdap.api.metrics.MetricTimeSeries;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.test.ApplicationManager;
import co.cask.cdap.test.MapReduceManager;
import co.cask.cdap.test.RuntimeStats;
import co.cask.cdap.test.ServiceManager;
import co.cask.cdap.test.TestBase;
import co.cask.common.http.HttpRequest;
import co.cask.common.http.HttpRequests;
import co.cask.common.http.HttpResponse;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests that a MapReduce job can incrementally process the partitions of a PartitionedFileSet, using a small sample of
 * data with the DataCleansing MapReduce job.
 */
public class DataCleansingMapReduceTest extends TestBase {
  private static final Set<String> RECORDS1 =
    ImmutableSet.of("{\"pid\":223986723,\"name\":\"bob\",\"dob\":\"02-12-1983\",\"zip\":\"84125\"}",
                    "{\"pid\":198637201,\"name\":\"timothy\",\"dob\":\"06-21-1995\",\"zip\":\"84125q\"}");
  private static final Set<String> RECORDS2 =
    ImmutableSet.of("{\"pid\":001058370,\"name\":\"jill\",\"dob\":\"12-12-1963\",\"zip\":\"84125\"}",
                    "{\"pid\":000150018,\"name\":\"wendy\",\"dob\":\"06-19-1987\",\"zip\":\"84125\"}");
  private static final Set<String> RECORDS3 =
    ImmutableSet.of("{\"pid\":013587810,\"name\":\"john\",\"dob\":\"10-10-1991\",\"zip\":\"84125\"}",
                    "{\"pid\":811638015,\"name\":\"samantha\",\"dob\":\"04-20-1965\",\"zip\":\"84125\"}");

  private static final String schemaJson = DataCleansingMapReduce.SchemaMatchingFilter.DEFAULT_SCHEMA.toString();
  private static final SimpleSchemaMatcher schemaMatcher =
    new SimpleSchemaMatcher(DataCleansingMapReduce.SchemaMatchingFilter.DEFAULT_SCHEMA);

  @Test
  public void testPartitionConsuming() throws Exception {
    ApplicationManager applicationManager = deployApplication(DataCleansing.class);

    ServiceManager serviceManager = applicationManager.getServiceManager(DataCleansingService.NAME).start();
    serviceManager.waitForStatus(true);
    URL serviceURL = serviceManager.getServiceURL();

    // write a set of records to one partition and run the DataCleansingMapReduce job on that one partition
    createPartition(serviceURL, RECORDS1);

    // before starting the MR, there are 0 invalid records, according to metrics
    Assert.assertEquals(0, getInvalidDataCount());
    Long now = System.currentTimeMillis();
    ImmutableMap<String, String> args = ImmutableMap.of(DataCleansingMapReduce.OUTPUT_PARTITION_KEY, now.toString(),
                                                        DataCleansingMapReduce.SCHEMA_KEY, schemaJson);
    MapReduceManager mapReduceManager = applicationManager.getMapReduceManager(DataCleansingMapReduce.NAME).start(args);
    mapReduceManager.waitForFinish(5, TimeUnit.MINUTES);

    Assert.assertEquals(filterInvalidRecords(RECORDS1), getCleanData(serviceURL, now));
    // assert that some of the records have indeed been filtered
    Assert.assertNotEquals(filterInvalidRecords(RECORDS1), RECORDS1);

    // verify this via metrics
    Assert.assertEquals(1, getInvalidDataCount());

    // create two additional partitions
    createPartition(serviceURL, RECORDS2);
    createPartition(serviceURL, RECORDS3);

    // running the MapReduce job now processes these two new partitions (RECORDS1 and RECORDS2) and creates a new
    // partition with with the output
    now = System.currentTimeMillis();
    args = ImmutableMap.of(DataCleansingMapReduce.OUTPUT_PARTITION_KEY, now.toString(),
                           DataCleansingMapReduce.SCHEMA_KEY, schemaJson);

    mapReduceManager = applicationManager.getMapReduceManager(DataCleansingMapReduce.NAME).start(args);
    mapReduceManager.waitForFinish(5, TimeUnit.MINUTES);

    ImmutableSet<String> records2and3 = ImmutableSet.<String>builder().addAll(RECORDS2).addAll(RECORDS3).build();
    Assert.assertEquals(filterInvalidRecords(records2and3), getCleanData(serviceURL, now));

    // running the MapReduce job without adding new partitions does creates no additional output
    now = System.currentTimeMillis();
    args = ImmutableMap.of(DataCleansingMapReduce.OUTPUT_PARTITION_KEY, now.toString(),
                           DataCleansingMapReduce.SCHEMA_KEY, schemaJson);

    mapReduceManager = applicationManager.getMapReduceManager(DataCleansingMapReduce.NAME).start(args);
    mapReduceManager.waitForFinish(5, TimeUnit.MINUTES);

    Assert.assertEquals(Collections.<String>emptySet(), getCleanData(serviceURL, now));
  }

  private void createPartition(URL serviceUrl, Set<String> records) throws IOException {
    URL url = new URL(serviceUrl, "v1/records/raw");
    HttpRequest request = HttpRequest.post(url).withBody(joinRecords(records)).build();
    HttpResponse response = HttpRequests.execute(request);
    Assert.assertEquals(200, response.getResponseCode());
  }

  private Set<String> getCleanData(URL serviceUrl, Long time) throws IOException {
    HttpResponse response =
      HttpRequests.execute(HttpRequest.get(new URL(serviceUrl, "v1/records/clean/" + time)).build());
    if (response.getResponseCode() == HttpResponseStatus.OK.code()) {
      return splitToRecords(response.getResponseBodyAsString());
    }
    if (response.getResponseCode() == HttpResponseStatus.NOT_FOUND.code()) {
      return Collections.emptySet();
    }
    Assert.fail("Expected to either receive a 200 or 404.");
    return null;
  }

  private String joinRecords(Set<String> records) {
    return Joiner.on("\n").join(records) + "\n";
  }

  private Set<String> splitToRecords(String content) {
    Set<String> records = new HashSet<>();
    Iterable<String> splitContent = Splitter.on("\n").omitEmptyStrings().split(content);
    for (String record : splitContent) {
      records.add(record);
    }
    return records;
  }

  private Set<String> filterInvalidRecords(Set<String> records) {
    Set<String> filteredSet = new HashSet<>();
    for (String record : records) {
      if (schemaMatcher.matches(record)) {
        filteredSet.add(record);
      }
    }
    return filteredSet;
  }

  private long getInvalidDataCount() throws Exception {
    Map<String, String> tags = ImmutableMap.of(Constants.Metrics.Tag.NAMESPACE, Constants.DEFAULT_NAMESPACE,
                                               Constants.Metrics.Tag.APP, DataCleansing.NAME,
                                               Constants.Metrics.Tag.MAPREDUCE, DataCleansingMapReduce.NAME);
    MetricDataQuery metricQuery = new MetricDataQuery(0, Integer.MAX_VALUE, Integer.MAX_VALUE, "user.data.invalid",
                                                      AggregationFunction.SUM, tags, ImmutableList.<String>of());
    Collection<MetricTimeSeries> result = RuntimeStats.metricStore.query(metricQuery);

    if (result.isEmpty()) {
      return 0;
    }

    // since it is totals query and not groupBy specified, we know there's one time series
    List<TimeValue> timeValues = result.iterator().next().getTimeValues();
    if (timeValues.isEmpty()) {
      return 0;
    }

    // since it is totals, we know there's one value only
    return timeValues.get(0).getValue();
  }
}
