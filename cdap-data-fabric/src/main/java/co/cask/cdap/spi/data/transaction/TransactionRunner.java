/*
 * Copyright © 2019 Cask Data, Inc.
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

package co.cask.cdap.spi.data.transaction;

import co.cask.cdap.spi.data.StructuredTable;
import co.cask.cdap.spi.data.StructuredTableContext;

/**
 * An object that executes submitted {@link TxRunnable} tasks. Each task submitted will be executed inside
 * a transaction.
 */
public interface TransactionRunner {

  /**
   * Executes a set of operations via a {@link TxRunnable} that are committed as a single transaction.
   * The {@link TxRunnable} can gain access to a {@link StructuredTable} through the
   * provided {@link StructuredTableContext}.
   *
   * @param runnable the runnable to be executed in the transaction
   * @throws TransactionException if failed to execute the given {@link TxRunnable} in a transaction
   */
  void run(TxRunnable runnable) throws TransactionException;
}
