package aula.iceibank.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.stream.Collectors;

@Converter
public class VectorConverter implements AttributeConverter<long[], String> {
    public String convertToDatabaseColumn(long[] value) {
        return value == null ? null : Arrays.stream(value).mapToObj(Long::toString).collect(Collectors.joining(","));
    }
    public long[] convertToEntityAttribute(String value) {
        return value == null ? null : Arrays.stream(value.split(",")).mapToLong(Long::parseLong).toArray();
    }
}
