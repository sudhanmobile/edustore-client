package org.edustore.app.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.edustore.app.Preferences;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public class DnsWithCacheTest {

    private static final String URL_1 = "locaihost";
    private static final String URL_2 = "fdroid.org";
    private static final String URL_3 = "fdroid.net";

    private static final InetAddress IP_1;
    private static final InetAddress IP_2;
    private static final InetAddress IP_3;

    static {
        try {
            IP_1 = InetAddress.getByName("127.0.0.1");
            IP_2 = InetAddress.getByName("127.0.0.2");
            IP_3 = InetAddress.getByName("127.0.0.3");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static final List<InetAddress> LIST_1 = Arrays.asList(IP_1, IP_2, IP_3);
    private static final List<InetAddress> LIST_2 = Arrays.asList(IP_2);
    private static final List<InetAddress> LIST_3 = Arrays.asList(IP_3);

    @Test
    public void basicCacheTest() throws IOException, InterruptedException {
        // test setup
        Preferences prefs = Preferences.get();
        prefs.setDnsCacheEnabledValue(true);
        DnsCache testObject = DnsCache.get();

        // populate cache
        testObject.insert(URL_1, LIST_1);
        testObject.insert(URL_2, LIST_2);
        testObject.insert(URL_3, LIST_3);

        // check for cached lookup results
        List<InetAddress> testList = testObject.lookup(URL_1);
        assertEquals(3, testList.size());
        assertEquals(IP_1.getHostAddress(), testList.get(0).getHostAddress());
        assertEquals(IP_2.getHostAddress(), testList.get(1).getHostAddress());
        assertEquals(IP_3.getHostAddress(), testList.get(2).getHostAddress());

        // toggle preference (false)
        prefs.setDnsCacheEnabledValue(false);

        // attempt non-cached lookup
        testList = testObject.lookup(URL_1);
        assertNull(testList);

        // toggle preference (true)
        prefs.setDnsCacheEnabledValue(true);

        // confirm lookup results remain in cache
        testList = testObject.lookup(URL_2);
        assertEquals(1, testList.size());
        assertEquals(IP_2.getHostAddress(), testList.get(0).getHostAddress());

        // test removal
        testList = testObject.remove(URL_2);
        assertEquals(1, testList.size());
        assertEquals(IP_2.getHostAddress(), testList.get(0).getHostAddress());
        testList = testObject.lookup(URL_2);
        assertNull(testList);
    }
}
