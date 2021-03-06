/**
 * Copyright 2015 StreamSets Inc.
 * <p>
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.jdbc.cdc.oracle;

import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import plsql.plsqlLexer;
import plsql.plsqlParser;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.streamsets.pipeline.lib.jdbc.JdbcErrors.JDBC_43;

@RunWith(Parameterized.class)
public class TestSQLListener {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {

    return Arrays.asList(new Object[][]
        {
            {
                "update \"HR\".\"EMPLOYEES\" " +
                    "  set " +
                    "    \"SALARY\" = 17595.97 " +
                    "  where " +
                    "    \"EMPLOYEE_ID\" = 201 and " +
                    "    \"FIRST_NAME\" = 'Michael' and " +
                    "    \"LAST_NAME\" = 'Hartstein' and " +
                    "    \"EMAIL\" = 'MHARTSTE' and " +
                    "    \"PHONE_NUMBER\" = '515.123.5555' and " +
                    "    \"HIRE_DATE\" = '17-FEB-96' and " +
                    "    \"JOB_ID\" = 'MK_MAN' and " +
                    "    \"SALARY\" = 16591.67 and " +
                    "    \"COMMISSION_PCT\" IS NULL and " +
                    "    \"MANAGER_ID\" = 100 and " +
                    "    \"DEPARTMENT_ID\" = 20 ",
                ImmutableMap.builder().put("SALARY", "17595.97")
                    .put("EMPLOYEE_ID", "201")
                    .put("FIRST_NAME", "Michael")
                    .put("LAST_NAME", "Hartstein")
                    .put("EMAIL", "MHARTSTE")
                    .put("PHONE_NUMBER", "515.123.5555")
                    .put("HIRE_DATE", "17-FEB-96")
                    .put("JOB_ID", "MK_MAN")
                    .put("MANAGER_ID", "100")
                    .put("DEPARTMENT_ID", "20")
                    .build()
            },
            {
                "insert into \"HR\".\"COUNTRIES\" " +
                    " values " +
                    "    \"COUNTRY_ID\" = 'bt', " +
                    "    \"COUNTRY_NAME\" = 'bottle', " +
                    "    \"REGION_ID\" IS NULL",
                ImmutableMap.builder().put("COUNTRY_ID", "bt")
                    .put("COUNTRY_NAME", "bottle")
                    .build()
            },
            {
                "insert into \"HR\".\"PEOPLE\" " +
                    " values " +
                    "    \"ID\" = 100, " +
                    "    \"NAME\" = 'spock' ",
                ImmutableMap.builder().put("ID", "100")
                    .put("NAME", "spock")
                    .build()
            },
            {
                "delete from \"HR\".\"COUNTRIES\" " +
                    " where " +
                    "    \"COUNTRY_ID\" = 'bt' and " +
                    "    \"COUNTRY_NAME\" = 'bottle' and " +
                    "    \"REGION_ID\" IS NULL ",
                ImmutableMap.builder().put("COUNTRY_ID", "bt")
                    .put("COUNTRY_NAME", "bottle")
                    .build()
            }
        }
    );
  }

  private final String sql;
  private final Map<String, String> expected;

  public TestSQLListener(String sql, Map<String, String> expected) {
    this.sql = sql;
    this.expected = expected;
  }

  @Test
  public void testSQL() {
    plsqlLexer l = new plsqlLexer(new ANTLRInputStream(sql));
    CommonTokenStream str = new CommonTokenStream(l);
    plsqlParser parser = new plsqlParser(str);
    ParserRuleContext c;
    if (sql.startsWith("insert")) {
      c = parser.insert_statement();
    } else if (sql.startsWith("delete")) {
      c = parser.delete_statement();
    } else {
      c = parser.update_statement();
    }
    SQLListener sqlListener = new SQLListener();
    ParseTreeWalker parseTreeWalker = new ParseTreeWalker();
    // Walk it and attach our sqlListener
    parseTreeWalker.walk(sqlListener, c);
    Assert.assertEquals(expected, sqlListener.getColumns());
  }

  @Test
  public void testFormat() {
    SQLListener listener = new SQLListener();

    Assert.assertEquals("Mithrandir", listener.format("Mithrandir"));
    Assert.assertEquals("Greyhame", listener.format("\'Greyhame\'"));
    Assert.assertEquals("Stormcrow", listener.format("\"Stormcrow\""));
    Assert.assertEquals("Lathspell", listener.format("\"\'Lathspell\'\""));
  }
}