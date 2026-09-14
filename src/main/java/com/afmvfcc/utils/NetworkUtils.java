package com.afmvfcc.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Finds the IP address the Android app should connect to. Windows Explorer /
 * ipconfig label the adapter Mobile Hotspot creates as something like
 * "Wireless LAN adapter Local Area Connection* 2" - but that's a Windows
 * Control Panel-level friendly name, not something Java's NetworkInterface
 * API exposes at all. What Java actually reports for that same adapter is
 * its driver description, "Microsoft Wi-Fi Direct Virtual Adapter" (with a
 * "#2", "#3", ... suffix if more than one has been created over time) - so
 * that's what we match on instead. Confirmed against a real machine with
 * Mobile Hotspot on: ipconfig showed "Local Area Connection* 2" at
 * 192.168.137.1, Java's NetworkInterface showed the same adapter (same IP)
 * as "Microsoft Wi-Fi Direct Virtual Adapter #2".
 */
public class NetworkUtils {

    private static final String HOTSPOT_ADAPTER_HINT = "wi-fi direct virtual adapter";

    public static class ServerAddress {
        public final String ip;
        public final String adapterName;
        public final boolean isHotspotAdapter;

        ServerAddress(String ip, String adapterName, boolean isHotspotAdapter) {
            this.ip = ip;
            this.adapterName = adapterName;
            this.isHotspotAdapter = isHotspotAdapter;
        }
    }

    /** Best-guess IP for the Android app to connect to, or null if no usable
     * network interface is up at all. */
    public static ServerAddress findServerAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            ServerAddress fallback = null;

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!isUsable(iface)) continue;

                String displayName = iface.getDisplayName() != null ? iface.getDisplayName() : iface.getName();
                String ip = firstIPv4(iface);
                if (ip == null) continue;

                boolean isHotspot = displayName.toLowerCase().contains(HOTSPOT_ADAPTER_HINT);
                if (isHotspot) {
                    return new ServerAddress(ip, displayName, true);
                }
                if (fallback == null) {
                    fallback = new ServerAddress(ip, displayName, false);
                }
            }
            return fallback;
        } catch (Exception e) {
            System.err.println("[NetworkUtils] Could not enumerate network interfaces: " + e.getMessage());
            return null;
        }
    }

    private static boolean isUsable(NetworkInterface iface) throws Exception {
        // Deliberately NOT excluding "virtual" interfaces - Windows' Mobile
        // Hotspot adapter ("Local Area Connection* 2") is exactly the kind
        // of standalone virtual NIC we're looking for, and Java's isVirtual()
        // only means "sub-interface of another interface" (e.g. VLAN tags),
        // a different concept that would incorrectly risk hiding it.
        return iface.isUp() && !iface.isLoopback();
    }

    private static String firstIPv4(NetworkInterface iface) {
        for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                return addr.getHostAddress();
            }
        }
        return null;
    }
}
