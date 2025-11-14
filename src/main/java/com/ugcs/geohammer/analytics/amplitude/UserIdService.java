package com.ugcs.geohammer.analytics.amplitude;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserIdService {

    private static final Logger log = LoggerFactory.getLogger(UserIdService.class);

    private static final String USER_ID_KEY = "amplitudeUserId";

    private final UserPropertiesService userPropertiesService;

    private final String deviceId;

    public UserIdService(UserPropertiesService userPropertiesService) {
        this.userPropertiesService = userPropertiesService;
        this.deviceId = generateDeviceId();
    }

    public String getOrCreateUserId() {
        String userId = userPropertiesService.get(USER_ID_KEY);
        if (userId == null) {
            userId = UUID.randomUUID().toString();
            userPropertiesService.put(USER_ID_KEY, userId);
            userPropertiesService.save();
        }
        return userId;
    }

    public String getOrCreateDeviceId() {
        return deviceId;
    }

    private String generateDeviceId() {
        byte[] idByteArray;
        try {
            List<byte[]> macs = Collections.list(NetworkInterface.getNetworkInterfaces())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(this::getHardwareAddress)
                    .filter(Objects::nonNull)
                    .sorted(this::compareByteArray)
                    .toList();
            int macBytesCount = macs.stream().mapToInt(mac -> mac.length).sum();
            ByteBuffer byteBuffer = ByteBuffer.allocate(macBytesCount);
            for (byte[] mac : macs) {
                byteBuffer.put(mac);
            }
            if (byteBuffer.position() != 0)
                idByteArray = encodeMD5(byteBuffer.array());
            else {
                idByteArray = uuidAsBytes();
                log.warn("No mac address found, generating UUID");
            }
        } catch (Exception e) {
            log.error("Error while reading mac-address, generating UUID", e);
            idByteArray = uuidAsBytes();
        }
        return Base64.getEncoder().encodeToString(idByteArray);
    }

    private byte[] getHardwareAddress(NetworkInterface networkInterface) {
        try {
            return networkInterface.getHardwareAddress();
        } catch (SocketException e) {
            log.error("Can't read hardware address ", e);
            return null;
        }
    }

    private byte[] encodeMD5(byte[] bytes) {
        String algorithm = "MD5";
        try {
            MessageDigest md5 = MessageDigest.getInstance(algorithm);
            md5.update(bytes);
            return md5.digest();
        } catch (NoSuchAlgorithmException e) {
            log.error("Algorithm {} not found: ", algorithm, e);
        }
        return new byte[0];
    }

    private byte[] uuidAsBytes() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private int compareByteArray(byte[] one, byte[] two) {
        Preconditions.checkNotNull(one);
        Preconditions.checkNotNull(two);
        if (one.length != two.length)
            return Integer.compare(one.length, two.length);
        for (int i = 0; i < one.length; i++) {
            if (one[i] != two[i])
                return Byte.compare(one[i], two[i]);
        }
        return 0;
    }
}