/*
 * Copyright © 2024 Cask Data, Inc.
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

package io.cdap.plugin.format.xls.input;


import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link XlsInputFormatUtils}
 */
public class XlsInputFormatUtilsTest {

  @Test
  public void testGetSafeColumnNames() {
    List<String> columnNames = ImmutableList.of(
            "column_A", "column_B", "column_C",
            "column_A", "column_B", "column_C",
            "\"column_A\"", "\"column_B\"", "\"column_C\"",
            "1st column", "2nd column", "3rd column",
            "column-1", "1column", "1234", "column#a",
            "column", "column_1", "_column", "Column", "_COLUMN_1_2_",
            "column_1", "column_1", "column_1_2", "s p a c e s", "1!)@#*$%&!@",
            "1234", "\"", ",", " ", "_", " column#a"
    );
    List<String> expectedColumnNames = ImmutableList.of(
            "column_A", "column_B", "column_C",
            "column_A_2", "column_B_2", "column_C_2",
            "_column_A_", "_column_B_", "_column_C_",
            "col_1st_column", "col_2nd_column", "col_3rd_column",
            "column_1", "col_1column", "col_1234", "column_a_3",
            "column", "column_1_2", "_column", "Column_2",
            "_COLUMN_1_2_", "column_1_3", "column_1_4", "column_1_2_2",
            "s_p_a_c_e_s", "col_1_", "col_1234_2", "_", "__2",
            "BLANK", "__3", "column_a_4"
    );
    List<String> actualColumnNames = XlsInputFormatUtils.getSafeColumnNames(columnNames);
    Assert.assertEquals(expectedColumnNames.size(), actualColumnNames.size());
    for (int i = 0; i < expectedColumnNames.size(); i++) {
      Assert.assertEquals(expectedColumnNames.get(i), actualColumnNames.get(i));
    }
  }
}
