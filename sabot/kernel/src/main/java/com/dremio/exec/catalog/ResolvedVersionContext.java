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
package com.dremio.exec.catalog;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;

/**
 * Represents a version context that has been resolved with the underlying
 * versioned catalog server. Always refers to a specific, existing commit.
 *
 * BRANCH refers to the commit that was at the head of the branch when it was
 * resolved.
 *
 * TAG refers to the commit that the tag pointed to when it was resolved.
 *
 * BARE_COMMIT always points to a specific, immutable commit.
 *
 * There is, however, a chance that the referenced commit (+ branch/tag) has
 * been modified or deleted after the call to resolve the context and before the
 * resolved context is used in another query.
 */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@JsonSerialize(as = ImmutableResolvedVersionContext.class)
@JsonDeserialize(as = ImmutableResolvedVersionContext.class)
public abstract class ResolvedVersionContext {
  public enum Type {
    BRANCH,
    TAG,
    BARE_COMMIT,
  }

  public static final String DETACHED = "DETACHED";

  public abstract Type getType();
  public abstract String getRefName();
  public abstract String getCommitHash();

  public static ResolvedVersionContext ofBranch(String branchName, String commitHash) {
    return ImmutableResolvedVersionContext.builder()
      .type(Type.BRANCH)
      .refName(branchName)
      .commitHash(commitHash)
      .build();
  }

  public static ResolvedVersionContext ofTag(String tagName, String commitHash) {
    return ImmutableResolvedVersionContext.builder()
      .type(Type.TAG)
      .refName(tagName)
      .commitHash(commitHash)
      .build();
  }

  public static ResolvedVersionContext ofBareCommit(String commitHash) {
    return ImmutableResolvedVersionContext.builder()
      .type(Type.BARE_COMMIT)
      .refName(DETACHED)
      .commitHash(commitHash)
      .build();
  }

  @Value.Check
  protected void check() {
    switch (Preconditions.checkNotNull(getType())) {
      case BRANCH: // Intentional fallthrough
      case TAG:
        Preconditions.checkNotNull(getRefName());
        break;
      case BARE_COMMIT:
        Preconditions.checkArgument(getRefName() == DETACHED);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + getType());
    }
    Preconditions.checkNotNull(getCommitHash());
  }

  @JsonIgnore
  public boolean isBranch() {
    return getType() == Type.BRANCH;
  }

  @JsonIgnore
  public boolean isBareCommit() { return getType() == Type.BARE_COMMIT; }

  public String toString() {
    String out;
    switch (getType()) {
      case BRANCH:
        out = String.format("branch %s at commit %s", getRefName(), getCommitHash());
        break;
      case TAG:
        out = String.format("tag %s at commit %s", getRefName(), getCommitHash());
        break;
      case BARE_COMMIT:
        out = String.format("commit %s", getCommitHash());
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + getType());
    }
    return out;
  }
}
