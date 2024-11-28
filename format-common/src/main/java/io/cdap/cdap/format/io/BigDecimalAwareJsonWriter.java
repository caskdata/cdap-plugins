/*
 * Copyright © 2022 Cask Data, Inc.
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

package io.cdap.cdap.format.io;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;

/**
 * JsonWriter instance which handles writing BigDecimal fields.
 */
public class BigDecimalAwareJsonWriter extends JsonWriter {
  public BigDecimalAwareJsonWriter(Writer out) {
    super(out);
  }

  @Override
  public JsonWriter value(Number value) throws IOException {
    if (value == null) {
      return this.nullValue();
    }

    // Wrap BigDecimal fields in a wrapper which handles the conversion to String.
    if (value instanceof BigDecimal) {
      value = new BigDecimalWrapper((BigDecimal) value);
    }

    super.value(value);
    return this;
  }

  /**
   * Wrapper used to ensure that BigDecimals are generated as plain strings.
   */
  private static class BigDecimalWrapper extends Number {
    BigDecimal wrapped;

    protected BigDecimalWrapper(BigDecimal wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public String toString() {
      return wrapped.toPlainString();
    }

    @Override
    public int intValue() {
      return wrapped.intValue();
    }

    @Override
    public long longValue() {
      return wrapped.longValue();
    }

    @Override
    public float floatValue() {
      return wrapped.floatValue();
    }

    @Override
    public double doubleValue() {
      return wrapped.doubleValue();
    }
  }
}
