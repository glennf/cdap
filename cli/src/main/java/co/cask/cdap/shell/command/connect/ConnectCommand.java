/*
 * Copyright 2012-2014 Cask Data, Inc.
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

package co.cask.cdap.shell.command.connect;

import co.cask.cdap.client.auth.AuthenticationClient;
import co.cask.cdap.client.auth.rest.BasicAuthenticationClient;
import co.cask.cdap.client.auth.rest.BasicCredentials;
import co.cask.cdap.shell.CLIConfig;
import co.cask.cdap.shell.command.AbstractCommand;
import co.cask.cdap.shell.util.SocketUtil;

import java.io.IOException;
import java.io.PrintStream;
import javax.inject.Inject;

/**
 * Connects to a CDAP instance.
 */
public class ConnectCommand extends AbstractCommand {

  private final CLIConfig cliConfig;
  private final AuthenticationClient<BasicCredentials> authenticationClient;

  @Inject
  public ConnectCommand(CLIConfig cliConfig) {
    super("connect", "<cdap-hostname> [<username> <password>]", "Connects to a CDAP instance. <username> and " +
      "<password> parameters could be used if authentication is enabled in the gateway server.");
    this.cliConfig = cliConfig;
    this.authenticationClient = new BasicAuthenticationClient();
  }

  @Override
  public void process(String[] args, PrintStream output) throws Exception {
    super.process(args, output);

    String hostname = args[0];
    int port = cliConfig.getClientConfig().getPort();

    if (!SocketUtil.isAvailable(hostname, port)) {
      throw new IOException(String.format("Host %s on port %d could not be reached", hostname, port));
    }
    //TODO: Check the provided hostname on HTTP and HTTPS. If HTTPS is available, make ClientConfig.resolve() use HTTPS
    authenticationClient.configure(hostname, port, false);
    if (authenticationClient.isAuthEnabled()) {
      if (args.length != 3) {
        throw new IOException("The authentication is enabled in the server. Please use command: " +
                                "connect <cdap-hostname> <username> <password> to connect successfully.");
      }
      String username = args[1];
      String password = args[2];
      cliConfig.setAccessToken(authenticationClient.getAccessToken(new BasicCredentials(username, password)));
    }

    cliConfig.setHostname(hostname);
    output.printf("Successfully connected CDAP host at %s:%d\n", hostname, port);
  }
}
