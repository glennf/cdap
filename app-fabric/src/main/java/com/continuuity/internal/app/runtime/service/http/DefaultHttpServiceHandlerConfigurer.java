/*
 * Copyright 2014 Continuuity, Inc.
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

package com.continuuity.internal.app.runtime.service.http;

import com.continuuity.api.service.http.HttpServiceConfigurer;
import com.continuuity.api.service.http.HttpServiceHandler;
import com.continuuity.api.service.http.HttpServiceSpecification;
import com.continuuity.internal.service.http.DefaultHttpServiceSpecification;

import java.util.Map;

/**
 *
 */
public class DefaultHttpServiceHandlerConfigurer implements HttpServiceConfigurer {
  private String name;
  private String description;
  private Map<String, String> arguments;

  public DefaultHttpServiceHandlerConfigurer(HttpServiceHandler handler) {
    this.name = handler.getClass().getSimpleName();
    this.description = "";
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public void setArguments(Map<String, String> arguments) {
    this.arguments = arguments;
  }

  public HttpServiceSpecification createHttpServiceSpec() {
    return new DefaultHttpServiceSpecification(name, description, arguments);
  }
}
