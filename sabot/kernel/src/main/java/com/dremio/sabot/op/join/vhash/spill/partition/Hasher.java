/*
 * Copyright (C) 2017-2019 Dremio Corporation
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
 */
package com.dremio.sabot.op.join.vhash.spill.partition;

import static com.dremio.sabot.op.join.vhash.spill.partition.Partition.INITIAL_VAR_FIELD_AVERAGE_SIZE;

import java.util.Random;

import com.dremio.common.AutoCloseables;
import com.dremio.exec.ExecConstants;
import com.dremio.sabot.op.join.vhash.spill.JoinSetupParams;

/**
 * Hasher for an incoming record batch.
 */
public class Hasher implements AutoCloseable {
  private final JoinSetupParams setupParams;
  private final JoinTable table;
  private long seed = new Random().nextLong();

  Hasher(JoinSetupParams setupParams) {
    this.setupParams = setupParams;
    this.table = new BlockJoinTable(setupParams.getBuildKeyPivot(), setupParams.getOpAllocator(), setupParams.getComparator(),
      (int)setupParams.getOptions().getOption(ExecConstants.MIN_HASH_TABLE_SIZE), INITIAL_VAR_FIELD_AVERAGE_SIZE,
      setupParams.getSabotConfig(), setupParams.getOptions());
  }

  void hashPivoted(int records, long hashoutAddr8B) {
    table.hashPivoted(records,
      setupParams.getPivotedFixedBlock().getMemoryAddress(),
      setupParams.getPivotedVariableBlock().getMemoryAddress(), seed, hashoutAddr8B);
  }

  void reseed() {
    seed = new Random().nextLong();
  }

  @Override
  public void close() throws Exception {
    AutoCloseables.close(table);
  }
}
