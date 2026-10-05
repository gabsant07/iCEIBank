package aula.iceibank.service;

import aula.iceibank.config.BankProperties;
import org.springframework.stereotype.Service;
import java.util.Arrays;

@Service
public class VectorClockService {
    private final int agency;
    private final long[] vector = new long[3];
    public VectorClockService(BankProperties properties) {
        agency = properties.getAgencyId();
        if (agency < 0 || agency >= vector.length) throw new IllegalArgumentException("Invalid agency");
    }
    public synchronized long[] localEvent() { vector[agency]++; return vector.clone(); }
    public synchronized long[] sendEvent() { return localEvent(); }
    public synchronized long[] receiveEvent(long[] received) {
        validate(received);
        advanceTo(received);
        return localEvent();
    }
    public synchronized void advanceTo(long[] received) {
        validate(received);
        for (int i = 0; i < vector.length; i++) vector[i] = Math.max(vector[i], received[i]);
    }
    public synchronized long[] current() { return vector.clone(); }
    public static void validate(long[] value) {
        if (value == null || value.length != 3 || Arrays.stream(value).anyMatch(n -> n < 0))
            throw new IllegalArgumentException("Expected three nonnegative vector components");
    }
    public static long rank(long[] value) { return Arrays.stream(value).sum(); }
}
