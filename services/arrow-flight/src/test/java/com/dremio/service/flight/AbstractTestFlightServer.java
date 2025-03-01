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
package com.dremio.service.flight;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.flight.CallOption;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStatusCode;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.SchemaResult;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Field;
import org.junit.Assert;
import org.junit.Test;

import com.dremio.service.flight.FlightClientUtils.FlightClientWrapper;

/**
 * Test functionality of the FlightClient communicating to the Flight endpoint.
 */
public abstract class AbstractTestFlightServer extends BaseFlightQueryTest {
  private static final String SELECT_QUERY = "SELECT 1 AS col_int, 'foobar' AS col_string";
  private static final String SELECT_QUERY_10K = "select * from cp.\"/10k_rows.parquet\"";
  private static final int TOTAL_ROWS_SELECT_QUERY_10K = 10001;

  protected abstract String getAuthMode();

  @Test
  public void testServerStartedOnPort() {
    super.assertThatServerStartedOnPort();
  }

  @Test
  public void testConnection() throws Exception {
    final FlightClient client = openFlightClient(DUMMY_USER, DUMMY_PASSWORD, getAuthMode());
    client.close();
  }

  @Test(expected = FlightRuntimeException.class)
  public void testFlightClientEncryptedServerUnencrypted() throws Exception {
    openEncryptedFlightClient(DUMMY_USER, DUMMY_PASSWORD, null, getAuthMode());
  }

  @Test(expected = FlightRuntimeException.class)
  public void testBadCredentials() throws Exception {
    openFlightClient(DUMMY_USER, "bad password", getAuthMode());
  }

  @Test
  public void testSelectStringAndIntLiteral() throws Exception {
    // Sanity check for verifying the test framework functions.
    flightTestBuilder()
      .sqlQuery("SELECT 1 AS col_int, 'foobar' AS col_string")
      .baselineColumns("col_int", "col_string")
      .baselineValues(1, "foobar")
      .go();
  }

  @Test
  public void testSelectNull() throws Exception {
    // Sanity check for verifying the test framework functions.
    flightTestBuilder()
      .sqlQuery("SELECT CAST(NULL AS INT) AS col_int")
      .baselineColumns("col_int")
      .baselineValues(null)
      .go();
  }

  @Test
  public void testSelectEmptyResultSet() throws Exception {
    // Sanity check for verifying the test framework functions.
    flightTestBuilder()
      .sqlQuery("SELECT 1 AS col_int FROM (VALUES(1)) WHERE 0 = 1")
      .expectsEmptyResultSet()
      .go();
  }

  @Test
  public void testFlightClientQueryCancellationAfterStreamIsRetrieved() throws Exception {
    try (final FlightStream stream = executeQuery(SELECT_QUERY)) {
      //CHECKSTYLE:OFF EmptyStatement|EmptyBlock
      while (stream.next()) {
        // Draining the stream before cancellation.
      }
      //CHECKSTYLE:ON EmptyStatement|EmptyBlock
      stream.cancel("Query is cancelled after stream is retrieved.",
        new Exception("Testing query data retrieval cancellation."));
      stream.getRoot().clear();
    }
  }

  @Test
  public void testFlightClientQueryCancellationBeforeStreamIsRetrieved() throws Exception {
    try (FlightStream stream = executeQuery(SELECT_QUERY)) {
      stream.cancel("Query is cancelled", new Exception("Testing query data retrieval cancellation."));
    }
  }

  @Test
  public void testFlightClientCloseBeforeStreamIsRetrieved() throws Exception {
    FlightStream stream = executeQuery(SELECT_QUERY);
    stream.close();
  }

  @Test
  public void testFlightClientCloseAfterStreamIsRetrieved() throws Exception {
    FlightStream stream = executeQuery(SELECT_QUERY);
    //CHECKSTYLE:OFF EmptyStatement|EmptyBlock
    while (stream.next()) {
      // Draining the stream before closing.
    }
    //CHECKSTYLE:ON EmptyStatement|EmptyBlock
    stream.close();
  }

  @Test
  public void testFlightClientWithInvalidQuery() {
    assertThatThrownBy(() -> executeQuery("SELECT * from non_existent_table"))
      .isInstanceOf(FlightRuntimeException.class)
      .extracting("status")
      .extracting("code")
      .isEqualTo(FlightStatusCode.INVALID_ARGUMENT);
  }

  @Test
  public void testDataRetrievalFor10kRows() throws Exception {
    // Act
    final List<String> actualStringResults = executeQueryWithStringResults(getFlightClientWrapper(), SELECT_QUERY_10K);

    // Assert
    assertEquals(TOTAL_ROWS_SELECT_QUERY_10K, actualStringResults.size());
  }

  @Test
  public void testDirectCommandWithExplainPlanRetrieval() throws Exception {
    // Act
    final List<String> actualStringResults = executeQueryWithStringResults(getFlightClientWrapper(), "EXPLAIN PLAN FOR " + SELECT_QUERY);

    // Assert
    assertEquals(actualStringResults.size(), 1);
    assertTrue(actualStringResults.get(0).contains(
      "Project(col_int=[1], col_string=['foobar']) : rowType =" +
        " RecordType(INTEGER col_int, VARCHAR(6) col_string): rowcount = 1.0"));
  }

  @Test
  public void testGetSchemasUsingLegacyProducer() {
    FlightDescriptor descriptor = FlightDescriptor.command(SELECT_QUERY_10K.getBytes(StandardCharsets.UTF_8));

    SchemaResult schema = getSchema(descriptor);
    Assert.assertEquals(schema.getSchema().getFields().size(), 1);
  }

  private FlightStream executeQuery(FlightClientWrapper flightClientWrapper, String query) throws SQLException {
    // Assumption is that we have exactly one endpoint returned.
    Ticket ticket = getFlightInfo(flightClientWrapper, query).getEndpoints().get(0).getTicket();
    return flightClientWrapper.getSqlClient().getStream(ticket, flightClientWrapper.getOptions());
  }

  public SchemaResult getSchema(FlightDescriptor descriptor) {
    FlightClientWrapper wrapper = getFlightClientWrapper();

    return wrapper.getClient().getSchema(descriptor, getCallOptions());
  }

  protected FlightStream executeQuery(String query) throws SQLException {
    // Assumption is that we have exactly one endpoint returned.
    return executeQuery(getFlightClientWrapper(), query);
  }

  protected List<String> executeQueryWithStringResults(FlightClientWrapper flightClientWrapper, String query) throws Exception {
    try (final FlightStream stream = executeQuery(flightClientWrapper, query)) {
      final List<String> actualStringResults = new ArrayList<>();

      while (stream.next()) {
        final VectorSchemaRoot root = stream.getRoot();
        final long rowCount = root.getRowCount();

        for (final Field field : root.getSchema().getFields()) {
          final FieldVector fieldVector = root.getVector(field.getName());

          if (fieldVector instanceof VarCharVector) {
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
              actualStringResults.add(fieldVector.getObject(rowIndex).toString());
            }
          }
        }
      }
      return actualStringResults;
    }
  }

  /**
   * Return an array of {@link CallOption} used in all calls to Flight Server (getFlightInfo, getStream, etc.).
   */
  abstract CallOption[] getCallOptions();

  /**
   * Returns a FlightInfo for executing given query.
   */
  public FlightInfo getFlightInfo(FlightClientWrapper flightClientWrapper, String query) throws SQLException {
    final FlightDescriptor command = FlightDescriptor.command(query.getBytes(StandardCharsets.UTF_8));
    return flightClientWrapper.getClient().getInfo(command, flightClientWrapper.getOptions());
  }
}
