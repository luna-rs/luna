package io.luna.net.msg.custom;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.luna.net.msg.GameMessage;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Inspects command frames without consuming bytes and strictly decodes bounded custom envelopes.
 *
 * @author lare96
 */
public final class CustomPacketCodec {

    /**
     * Maximum ASCII envelope length before the native newline terminator.
     */
    public static final int MAX_ENVELOPE_BYTES = 254;

    /**
     * Maximum number of values in the initial flat-array format.
     */
    public static final int MAX_VALUES = 8;

    private static final String TOKEN = "/packet";
    private static final String PREFIX = TOKEN + " #";
    private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]*)");

    private CustomPacketCodec() {
    }

    /**
     * Recognizes the reserved command token even when the rest of its frame is malformed. Case variants
     * are reserved as well, so ordinary command lowercasing cannot turn rejected requests into commands.
     *
     * @param msg The native command message.
     * @return Whether this message targets the reserved token.
     */
    public static boolean isReserved(GameMessage msg) {
        if (msg.getOpcode() != 56 || msg.getPayload().refCnt() == 0) {
            return false;
        }
        ByteBuf buffer = msg.getPayload().getBuffer();
        int length = Math.min(msg.getSize(), buffer.readableBytes());
        if (length < TOKEN.length()) {
            return false;
        }
        int start = buffer.readerIndex();
        for (int index = 0; index < TOKEN.length(); index++) {
            int value = buffer.getUnsignedByte(start + index);
            if (value >= 'A' && value <= 'Z') {
                value += 'a' - 'A';
            }
            if (value != TOKEN.charAt(index)) {
                return false;
            }
        }
        if (length == TOKEN.length()) {
            return true;
        }
        int delimiter = buffer.getUnsignedByte(start + TOKEN.length());
        return delimiter <= ' ' || delimiter >= 0x7f;
    }

    /**
     * Decodes an entire bounded command frame. Expected framing and JSON failures are local silent drops.
     * The buffer and its reader index remain unchanged.
     *
     * @param msg The native command message.
     * @return The decoded request, or an empty optional for an invalid envelope.
     */
    public static Optional<CustomPacketRequest> decode(GameMessage msg) {
        if (!isReserved(msg)) {
            return Optional.empty();
        }
        ByteBuf buffer = msg.getPayload().getBuffer();
        int length = msg.getSize();
        if (length < 1 || length > MAX_ENVELOPE_BYTES + 1 || buffer.readableBytes() != length) {
            return Optional.empty();
        }
        int start = buffer.readerIndex();
        if (buffer.getUnsignedByte(start + length - 1) != '\n') {
            return Optional.empty();
        }
        for (int index = 0; index < length - 1; index++) {
            int value = buffer.getUnsignedByte(start + index);
            if (value < ' ' || value > '~') {
                return Optional.empty();
            }
        }
        String envelope = buffer.toString(start, length - 1, StandardCharsets.US_ASCII);
        if (!envelope.startsWith(PREFIX)) {
            return Optional.empty();
        }
        int idEnd = envelope.indexOf(' ', PREFIX.length());
        if (idEnd == -1 || idEnd == PREFIX.length()) {
            return Optional.empty();
        }
        String idText = envelope.substring(PREFIX.length(), idEnd);
        for (int index = 0; index < idText.length(); index++) {
            if (idText.charAt(index) < '0' || idText.charAt(index) > '9') {
                return Optional.empty();
            }
        }
        try {
            int id = Integer.parseInt(idText);
            List<Object> values = new ArrayList<>(MAX_VALUES);
            try (JsonReader reader = new JsonReader(new StringReader(envelope.substring(idEnd + 1)))) {
                reader.setStrictness(Strictness.STRICT);
                reader.beginArray();
                while (reader.hasNext()) {
                    if (values.size() == MAX_VALUES) {
                        return Optional.empty();
                    }
                    JsonToken token = reader.peek();
                    switch (token) {
                        case STRING -> values.add(reader.nextString());
                        case BOOLEAN -> values.add(reader.nextBoolean());
                        case NUMBER -> {
                            String integer = reader.nextString();
                            if (!INTEGER.matcher(integer).matches()) {
                                return Optional.empty();
                            }
                            values.add(Long.parseLong(integer));
                        }
                        default -> {
                            return Optional.empty();
                        }
                    }
                }
                reader.endArray();
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    return Optional.empty();
                }
            }
            return Optional.of(new CustomPacketRequest(id, values));
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
    }
}
