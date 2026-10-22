package vn.vietroute.common.traffic;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/** Chỉ đọc X-Forwarded-For khi peer thực sự thuộc CIDR proxy được cấu hình; chống tự giả IP để né quota. */
public final class ClientAddressResolver {

    private final List<Network> networks = new ArrayList<>();

    public ClientAddressResolver(String cidrs) {
        if (cidrs.isBlank()) {
            return;
        }
        for (String cidr : cidrs.split(",")) {
            String[] parts = cidr.trim().split("/", -1);
            byte[] address = literal(parts[0]);
            int bits = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
            if (parts.length > 2 || bits < 0 || bits > address.length * 8) {
                throw new IllegalStateException("VIETROUTE_TRUSTED_PROXY_CIDRS is invalid");
            }
            networks.add(new Network(address, bits));
        }
    }

    public String resolve(HttpServletRequest request) {
        String current = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 1024) {
            return current;
        }
        String[] chain = forwarded.split(",");
        if (chain.length > 16) {
            return current;
        }
        for (int index = chain.length - 1; index >= 0 && trusted(current); index--) {
            String next = chain[index].trim();
            try {
                literal(next);
            } catch (IllegalArgumentException exception) {
                return request.getRemoteAddr();
            }
            current = next;
        }
        return current;
    }

    private boolean trusted(String value) {
        try {
            byte[] address = literal(value);
            return networks.stream().anyMatch(network -> network.contains(address));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] literal(String value) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) {
            throw new IllegalArgumentException("Invalid IP literal");
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP literal");
        }
    }

    private record Network(byte[] address, int bits) {
        boolean contains(byte[] candidate) {
            if (address.length != candidate.length) {
                return false;
            }
            for (int index = 0; index < bits; index++) {
                int mask = 1 << (7 - (index % 8));
                if ((address[index / 8] & mask) != (candidate[index / 8] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
