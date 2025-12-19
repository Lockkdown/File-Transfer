package com.drivelite.common.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Utility class để lấy thông tin network interfaces.
 * Hữu ích cho demo khi cần biết IP để client connect.
 */
public class NetworkUtils {

    /**
     * Lấy danh sách tất cả IPv4 addresses có thể dùng được.
     * Bỏ qua loopback (127.0.0.1) và interfaces không hoạt động.
     * 
     * @return List các NetworkAddress (tên adapter + IP)
     */
    public static List<NetworkAddress> getAvailableIPv4Addresses() {
        List<NetworkAddress> result = new ArrayList<>();
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // Bỏ qua interface không hoạt động hoặc loopback
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // Chỉ lấy IPv4
                    if (addr instanceof Inet4Address) {
                        result.add(new NetworkAddress(
                            ni.getDisplayName(),
                            addr.getHostAddress()
                        ));
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[NetworkUtils] Error getting network interfaces: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * In danh sách IPv4 addresses ra console.
     */
    public static void printAvailableAddresses() {
        List<NetworkAddress> addresses = getAvailableIPv4Addresses();
        
        System.out.println("\n┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│             DETECTED IPv4 ADDRESSES                         │");
        System.out.println("├─────────────────────────────────────────────────────────────┤");
        
        if (addresses.isEmpty()) {
            System.out.println("│  (Không tìm thấy network interface nào)                     │");
        } else {
            for (int i = 0; i < addresses.size(); i++) {
                NetworkAddress na = addresses.get(i);
                String line = String.format("│  [%d] %-15s  (%s)", 
                    i + 1, na.getIpAddress(), truncate(na.getAdapterName(), 35));
                System.out.println(padRight(line, 62) + "│");
            }
        }
        
        System.out.println("└─────────────────────────────────────────────────────────────┘");
        System.out.println("  → Client connect đến: <IP>:9000");
        System.out.println();
    }

    /**
     * Lấy IP address đầu tiên không phải loopback (fallback).
     */
    public static String getFirstNonLoopbackIPv4() {
        List<NetworkAddress> addresses = getAvailableIPv4Addresses();
        return addresses.isEmpty() ? "127.0.0.1" : addresses.get(0).getIpAddress();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    /**
     * Data class chứa thông tin network address.
     */
    public static class NetworkAddress {
        private final String adapterName;
        private final String ipAddress;

        public NetworkAddress(String adapterName, String ipAddress) {
            this.adapterName = adapterName;
            this.ipAddress = ipAddress;
        }

        public String getAdapterName() {
            return adapterName;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        @Override
        public String toString() {
            return ipAddress + " (" + adapterName + ")";
        }
    }
}
