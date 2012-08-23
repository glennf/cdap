package CountCounts;

import com.continuuity.api.flow.Flow;
import com.continuuity.api.flow.FlowSpecifier;

public class Main implements Flow {
  public void configure(FlowSpecifier specifier) {
    specifier.name("CountCounts");
    specifier.email("andreas@continuuity.com");
    specifier.application("Examples");
    specifier.flowlet("source", StreamSource.class, 1);
    specifier.flowlet("count", WordCounter.class, 1);
    specifier.flowlet("tick", Incrementer.class, 1);
    specifier.input("source");
    specifier.connection("source", "count");
    specifier.connection("count", "tick");
  }
}
