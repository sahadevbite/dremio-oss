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
package com.dremio.exec.store.iceberg.deletes;

import java.util.List;

import com.dremio.io.file.Path;
import com.dremio.sabot.exec.context.OperatorContext;

/**
 * A storage format agnostic factory interface for creating PositionalDeleteFileReader instances.
 */
public interface PositionalDeleteFileReaderFactory {
  PositionalDeleteFileReader create(OperatorContext context, Path deleteFilePath, List<String> dataFilePaths);
}
