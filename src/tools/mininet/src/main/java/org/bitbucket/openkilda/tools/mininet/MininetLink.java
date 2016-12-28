package org.bitbucket.openkilda.tools.mininet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "node1",
  "node2",
  "name",
  "status"
})

public class MininetLink {
  private static final Logger logger = LogManager.getLogger(MininetLink.class.getName());
  @JsonProperty("node1")
  private String node1;
  @JsonProperty("node2")
  private String node2;
  @JsonProperty("name")
  private String name;
  @JsonProperty("status")
  private String status;
  
  public MininetLink() {
    
  }
  
  public MininetLink(String node1, String node2) {
    this.node1 = node1;
    this.node2 = node2;
  }
  
  @JsonProperty("node1")
  public String getNode1() {
    return node1;
  }
  
  @JsonProperty("node1")
  public MininetLink setNode1(String node1) {
    this.node1 = node1;
    return this;
  }
  
  @JsonProperty("node2")
  public String getNode2() {
    return node2;
  }
  
  @JsonProperty("node2")
  public MininetLink setNode2(String node2) {
    this.node2 = node2;
    return this;
  }
  
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  
  @JsonProperty("name")
  public MininetLink setName(String name) {
    this.name = name;
    return this;
  }
  
  @JsonProperty("status")
  public String getStatus() {
    return status;
  }
  
  @JsonProperty("status")
  public MininetLink setStatus(String status) {
    this.status = status;
    return this;
  }
  
  @JsonIgnore
  public boolean isUp() {
    logger.debug("link " + name + " status is " + status);
    logger.debug("isUp " + status.equalsIgnoreCase("(OK OK)"));
    return status.equalsIgnoreCase("(OK OK)");
  }
}
