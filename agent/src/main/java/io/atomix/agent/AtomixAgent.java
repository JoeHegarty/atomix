/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.agent;

import io.atomix.cluster.Node;
import io.atomix.cluster.NodeConfig;
import io.atomix.cluster.NodeId;
import io.atomix.core.Atomix;
import io.atomix.rest.ManagedRestService;
import io.atomix.rest.RestService;
import io.atomix.utils.net.Address;
import io.atomix.utils.net.MalformedAddressException;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.ArgumentType;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Atomix agent runner.
 */
public class AtomixAgent {
  private static final Logger LOGGER = LoggerFactory.getLogger(AtomixAgent.class);

  public static void main(String[] args) throws Exception {
    ArgumentType<NodeConfig> nodeArgumentType = (ArgumentParser argumentParser, Argument argument, String value) -> {
      return new NodeConfig()
          .setId(parseNodeId(value))
          .setType(Node.Type.CORE)
          .setAddress(parseAddress(value));
    };

    ArgumentType<Node.Type> typeArgumentType = (ArgumentParser argumentParser, Argument argument, String value) -> Node.Type.valueOf(value.toUpperCase());
    ArgumentType<File> fileArgumentType = (ArgumentParser argumentParser, Argument argument, String value) -> new File(value);

    ArgumentParser parser = ArgumentParsers.newArgumentParser("AtomixServer")
        .defaultHelp(true)
        .description("Atomix server");
    parser.addArgument("node")
        .type(nodeArgumentType)
        .nargs("?")
        .metavar("NAME:HOST:PORT")
        .required(false)
        .help("The local node info");
    parser.addArgument("--type", "-t")
        .type(typeArgumentType)
        .metavar("TYPE")
        .choices(Node.Type.CORE, Node.Type.DATA, Node.Type.CLIENT)
        .setDefault(Node.Type.CORE)
        .help("Indicates the local node type");
    parser.addArgument("--config", "-c")
        .metavar("FILE|JSON|YAML")
        .required(false)
        .help("The Atomix configuration file");
    parser.addArgument("--core-nodes", "-n")
        .nargs("*")
        .type(nodeArgumentType)
        .metavar("NAME:HOST:PORT")
        .required(false)
        .help("Sets the core cluster configuration");
    parser.addArgument("--bootstrap-nodes", "-b")
        .nargs("*")
        .type(nodeArgumentType)
        .metavar("NAME:HOST:PORT")
        .required(false)
        .help("Sets the bootstrap nodes");
    parser.addArgument("--data-dir", "-d")
        .type(fileArgumentType)
        .metavar("FILE")
        .required(false)
        .help("The data directory");
    parser.addArgument("--http-port", "-p")
        .type(Integer.class)
        .metavar("PORT")
        .required(false)
        .setDefault(5678)
        .help("An optional HTTP server port");

    Namespace namespace = null;
    try {
      namespace = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.exit(1);
    }

    String configString = namespace.get("config");

    Atomix.Builder builder = Atomix.builder(configString)
        .withShutdownHook(true);

    NodeConfig localNode = namespace.get("node");
    if (localNode != null) {
      Node.Type type = namespace.get("type");
      localNode.setType(type);
      Node node = new Node(localNode);
      builder.withLocalNode(node);
      LOGGER.info("node: {}", node);
    }

    List<NodeConfig> coreNodes = namespace.getList("core_nodes");
    List<NodeConfig> bootstrapNodes = namespace.getList("bootstrap_nodes");

    if (coreNodes != null || bootstrapNodes != null) {
      List<Node> nodes = Stream.concat(
          coreNodes != null ? coreNodes.stream() : Stream.empty(),
          bootstrapNodes != null ? bootstrapNodes.stream() : Stream.empty())
          .map(node -> Node.builder(node.getId())
              .withType(node.getType())
              .withAddress(node.getAddress())
              .build())
          .collect(Collectors.toList());
      builder.withNodes(nodes);
    }

    File dataDir = namespace.get("data_dir");
    Integer httpPort = namespace.getInt("http_port");

    if (dataDir != null) {
      builder.withDataDirectory(dataDir);
    }

    Atomix atomix = builder.build();

    atomix.start().join();

    LOGGER.info("Atomix listening at {}:{}", atomix.clusterService().getLocalNode().address().host(), atomix.clusterService().getLocalNode().address().port());

    ManagedRestService rest = RestService.builder()
        .withAtomix(atomix)
        .withAddress(Address.from(atomix.clusterService().getLocalNode().address().host(), httpPort))
        .build();

    rest.start().join();

    LOGGER.info("HTTP server listening at {}:{}", atomix.clusterService().getLocalNode().address().address().getHostAddress(), httpPort);

    synchronized (Atomix.class) {
      while (atomix.isRunning()) {
        Atomix.class.wait();
      }
    }
  }

  static NodeId parseNodeId(String address) {
    int endIndex = address.indexOf('@');
    if (endIndex > 0) {
      return NodeId.from(address.substring(0, endIndex));
    } else {
      try {
        return NodeId.from(Address.from(address).host());
      } catch (MalformedAddressException e) {
        return NodeId.from(address);
      }
    }
  }

  static Address parseAddress(String address) {
    int startIndex = address.indexOf('@');
    if (startIndex == -1) {
      try {
        return Address.from(address);
      } catch (MalformedAddressException e) {
        return Address.from("0.0.0.0");
      }
    } else {
      return Address.from(address.substring(startIndex + 1));
    }
  }
}
