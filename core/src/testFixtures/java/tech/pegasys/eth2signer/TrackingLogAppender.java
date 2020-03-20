/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.pegasys.eth2signer;


import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

public class TrackingLogAppender extends AbstractAppender
{
  final List<LogEvent> logMessagesReceived = new ArrayList<>();

  public TrackingLogAppender() {
    super("TestAppender", null , null, false, null);
  }

  @Override
  public void append(final LogEvent event) {
    logMessagesReceived.add(event.toImmutable());
  }

  public List<LogEvent> getLogMessagesReceived() {
    return logMessagesReceived;
  }
}