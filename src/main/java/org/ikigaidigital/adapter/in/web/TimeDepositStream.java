package org.ikigaidigital.adapter.in.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.ikigaidigital.api.model.TimeDeposit;

import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Response body of {@code GET /time-deposits}, mapped to the {@code TimeDepositList} schema via
 * openapi-generator {@code schemaMappings}. It is serialized as a JSON array element by element while
 * the response is written, so arbitrarily many deposits are never materialized in memory at once.
 * The underlying stream is closed once serialization completes.
 */
@JsonSerialize(using = TimeDepositStream.Serializer.class)
public final class TimeDepositStream {

    private final Stream<TimeDeposit> timeDeposits;

    public TimeDepositStream(Stream<TimeDeposit> timeDeposits) {
        this.timeDeposits = timeDeposits;
    }

    static final class Serializer extends StdSerializer<TimeDepositStream> {

        Serializer() {
            super(TimeDepositStream.class);
        }

        @Override
        public void serialize(TimeDepositStream value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeStartArray();
            try (Stream<TimeDeposit> timeDeposits = value.timeDeposits) {
                Iterator<TimeDeposit> iterator = timeDeposits.iterator();
                while (iterator.hasNext()) {
                    provider.defaultSerializeValue(iterator.next(), generator);
                }
            }
            generator.writeEndArray();
        }
    }
}
