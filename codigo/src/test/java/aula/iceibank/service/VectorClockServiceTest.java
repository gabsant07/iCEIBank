package aula.iceibank.service;

import aula.iceibank.config.BankProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VectorClockServiceTest {
    @Test void appliesRulesAndCopiesSnapshots() {
        BankProperties properties = new BankProperties();
        properties.setAgencyId(1);
        VectorClockService clock = new VectorClockService(properties);
        long[] local = clock.localEvent();
        assertArrayEquals(new long[]{0,1,0}, local);
        local[1] = 99;
        assertArrayEquals(new long[]{0,2,0}, clock.sendEvent());
        assertArrayEquals(new long[]{3,3,0}, clock.receiveEvent(new long[]{3,1,0}));
        assertThrows(IllegalArgumentException.class, () -> clock.receiveEvent(new long[]{1}));
        assertArrayEquals(new long[]{3,3,0}, clock.current());
    }
}
