package io.luna.net.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link ByteBuf} wrapper tailored to the specifications of the Runescape protocol.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ByteMessage extends DefaultByteBufHolder {

    /**
     * The default initial buffer size.
     */
    private static final int DEFAULT_SIZE = 128;

    /**
     * An array of bit masks.
     */
    private static final int[] BIT_MASK = new int[32];

    /**
     * Creates a {@link ByteMessage} used to read and encode raw messages.
     *
     * @param size The initial size.
     * @return A new buffer.
     */
    public static ByteMessage raw(int size) {
        return new ByteMessage(pooledBuffer(size), -1, MessageType.RAW);
    }

    /**
     * Creates a {@link ByteMessage} used to read and encode raw messages.
     *
     * @return A new buffer.
     */
    public static ByteMessage raw() {
        return raw(128);
    }

    /**
     * Creates a {@link ByteMessage} used to read and encode game messages.
     *
     * @param opcode The opcode.
     * @param type The message type.
     * @return A new buffer.
     */
    public static ByteMessage message(int opcode, MessageType type) {
        return new ByteMessage(pooledBuffer(), opcode, type);
    }

    /**
     * Creates a fixed type {@link ByteMessage} used to read and encode game messages.
     *
     * @param opcode The opcode.
     * @return A new buffer.
     */
    public static ByteMessage message(int opcode) {
        return message(opcode, MessageType.FIXED);
    }

    /**
     * Creates a raw {@link ByteMessage} wrapped around the specified {@link ByteBuf}.
     *
     * @param buf The buffer to wrap.
     * @return A new buffer.
     */
    public static ByteMessage wrap(ByteBuf buf) {
        return new ByteMessage(buf, -1, MessageType.RAW);
    }

    /**
     * Creates a raw backing pooled {@link ByteBuf}.
     *
     * @param size The initial size.
     * @return A new buffer.
     */
    public static ByteBuf pooledBuffer(int size) {
        return PooledByteBufAllocator.DEFAULT.buffer(size);
    }

    /**
     * Creates a raw backing pooled {@link ByteBuf}.
     *
     * @return A new buffer.
     */
    public static ByteBuf pooledBuffer() {
        return pooledBuffer(DEFAULT_SIZE);
    }

    /**
     * The backing buffer.
     */
    private final ByteBuf buf;

    /**
     * The opcode.
     */
    private final int opcode;

    /**
     * The header type.
     */
    private final MessageType type;

    /**
     * The current bit index.
     */
    private int bitIndex = -1;

    static {
        // Initialize bit masks.
        for (int i = 0; i < BIT_MASK.length; i++) {
            BIT_MASK[i] = (1 << i) - 1;
        }
    }

    /**
     * Creates a new {@link ByteMessage}.
     *
     * @param buf The backing buffer.
     * @param opcode The opcode.
     * @param type The header type.
     */
    private ByteMessage(ByteBuf buf, int opcode, MessageType type) {
        super(buf);
        this.buf = buf;
        this.opcode = opcode;
        this.type = type;
    }

    @Override
    public boolean release() {
        if (buf == Unpooled.EMPTY_BUFFER) {
            return true;
        }
        return buf.release();
    }

    @Override
    public boolean release(int decrement) {
        if (buf == Unpooled.EMPTY_BUFFER) {
            return true;
        }
        return buf.release(decrement);
    }

    /**
     * Releases all references of this {@link ByteBuf}.
     *
     * @see ByteMessage#release(int)
     */

    public boolean releaseAll() {
        return buf.release(buf.refCnt());
    }

    /**
     * Prepares the buffer for writing bits.
     */
    public void startBitAccess() {
        checkState(bitIndex == -1, "This ByteMessage instance is already in bit access mode.");

        bitIndex = buf.writerIndex() << 3;
    }

    /**
     * Prepares the buffer for writing bytes.
     */
    public void endBitAccess() {
        checkState(bitIndex != -1, "This ByteMessage instance is not in bit access mode.");

        buf.writerIndex((bitIndex + 7) >> 3);
        bitIndex = -1;
    }

    /**
     * Writes bytes from the argued buffer into this buffer.
     *
     * @param from The buffer to get bytes from.
     * @return This buffer instance.
     */
    public ByteMessage putBytes(ByteBuf from) {
        buf.writeBytes(from, 0, from.writerIndex());
        return this;
    }

    /**
     * Writes bytes from the argued buffer into this buffer.
     *
     * @param from The buffer to get bytes from.
     * @return This buffer instance.
     */
    public ByteMessage putBytes(ByteMessage from) {
        return putBytes(from.getBuffer());
    }

    /**
     * Writes bytes from the argued array into this buffer.
     *
     * @param from The buffer to get bytes from.
     * @return This buffer instance.
     */
    public ByteMessage putBytes(byte[] from) {
        buf.writeBytes(from, 0, from.length);
        return this;
    }

    /**
     * Writes bytes in reverse from the argued array into this buffer.
     *
     * @param from The buffer to get bytes from.
     * @return This buffer instance.
     */
    public ByteMessage putBytesReverse(byte[] from) {
        for (int i = from.length - 1; i >= 0; i--) {
            put(from[i]);
        }
        return this;
    }

    /**
     * Writes the value as a variable amount of bits.
     *
     * @param amount The bit amount.
     * @param value The value.
     * @return This buffer instance.
     * @throws IllegalArgumentException If {@code amount} is not between {@code 1} and {@code 32} inclusive.
     */
    public ByteMessage putBits(int amount, int value) {
        checkState(amount >= 1 && amount <= 32, "Number of bits must be between 1 and 32 inclusive.");

        int bytePos = bitIndex >> 3;
        int bitOffset = 8 - (bitIndex & 7);

        bitIndex = bitIndex + amount;

        int requiredSpace = bytePos - buf.writerIndex() + 1;
        requiredSpace += (amount + 7) / 8;

        if (buf.writableBytes() < requiredSpace) {
            buf.capacity(buf.capacity() + requiredSpace);
        }

        for (; amount > bitOffset; bitOffset = 8) {
            byte tmp = buf.getByte(bytePos);
            tmp &= ~BIT_MASK[bitOffset];
            tmp |= (value >> (amount - bitOffset)) & BIT_MASK[bitOffset];
            buf.setByte(bytePos++, tmp);
            amount -= bitOffset;
        }

        if (amount == bitOffset) {
            byte tmp = buf.getByte(bytePos);
            tmp &= ~BIT_MASK[bitOffset];
            tmp |= value & BIT_MASK[bitOffset];
            buf.setByte(bytePos, tmp);
        } else {
            byte tmp = buf.getByte(bytePos);
            tmp &= ~(BIT_MASK[amount] << (bitOffset - amount));
            tmp |= (value & BIT_MASK[amount]) << (bitOffset - amount);
            buf.setByte(bytePos, tmp);
        }
        return this;
    }

    /**
     * Writes a boolean bit.
     *
     * @param flag The flag value.
     * @return This buffer instance.
     */
    public ByteMessage putBit(boolean flag) {
        putBits(1, flag ? 1 : 0);
        return this;
    }

    /**
     * Writes a value as a {@code byte}.
     *
     * @param value The value.
     * @param transform The transformation type.
     * @return This buffer instance.
     */
    public ByteMessage put(int value, ValueType transform) {
        switch (transform) {
            case ADD:
                value += 128;
                break;
            case NEGATE:
                value = -value;
                break;
            case SUBTRACT:
                value = 128 - value;
                break;
            case NORMAL:
                break;
        }
        buf.writeByte((byte) value);
        return this;
    }

    /**
     * Writes a value as a normal {@code byte}.
     *
     * @param value The value.
     * @return This buffer instance.
     */
    public ByteMessage put(int value) {
        put(value, ValueType.NORMAL);
        return this;
    }

    /**
     * Writes a value as a {@code short}.
     *
     * @param value The value.
     * @param transform The transformation type.
     * @param order The byte order.
     * @return This buffer instance.
     * @throws UnsupportedOperationException If middle or inverse-middle value types are selected.
     */
    public ByteMessage putShort(int value, ValueType transform, ByteOrder order) {
        switch (order) {
            case BIG:
                put(value >> 8);
                put(value, transform);
                break;
            case MIDDLE:
                throw new UnsupportedOperationException("Middle-endian short is impossible.");
            case INVERSE_MIDDLE:
                throw new UnsupportedOperationException("Inversed-middle-endian short is impossible.");
            case LITTLE:
                put(value, transform);
                put(value >> 8);
                break;
        }
        return this;
    }

    /**
     * Writes a value as a normal big-endian {@code short}.
     *
     * @param value The value.
     * @return This buffer instance.
     */
    public ByteMessage putShort(int value) {
        putShort(value, ValueType.NORMAL, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a big-endian {@code short}.
     *
     * @param value The value.
     * @param transform The transformation type.
     * @return This buffer instance.
     */
    public ByteMessage putShort(int value, ValueType transform) {
        putShort(value, transform, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a standard {@code short}.
     *
     * @param value The value.
     * @param order The byte order.
     * @return This buffer instance.
     */
    public ByteMessage putShort(int value, ByteOrder order) {
        putShort(value, ValueType.NORMAL, order);
        return this;
    }

    /**
     * Writes a value as an {@code int}.
     *
     * @param value The value.
     * @param transform The transformation type.
     * @param order The byte order.
     * @return This buffer instance.
     */
    public ByteMessage putInt(int value, ValueType transform, ByteOrder order) {
        switch (order) {
            case BIG:
                put(value >> 24);
                put(value >> 16);
                put(value >> 8);
                put(value, transform);
                break;
            case MIDDLE:
                put(value >> 8);
                put(value, transform);
                put(value >> 24);
                put(value >> 16);
                break;
            case INVERSE_MIDDLE:
                put(value >> 16);
                put(value >> 24);
                put(value, transform);
                put(value >> 8);
                break;
            case LITTLE:
                put(value, transform);
                put(value >> 8);
                put(value >> 16);
                put(value >> 24);
                break;
        }
        return this;
    }

    /**
     * Writes a value as a standard big-endian {@code int}.
     *
     * @param value The value.
     * @return This buffer instance.
     */
    public ByteMessage putInt(int value) {
        putInt(value, ValueType.NORMAL, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a big-endian {@code int}.
     *
     * @param value The value.
     * @param transform The transformation type.
     * @return This buffer instance.
     */
    public ByteMessage putInt(int value, ValueType transform) {
        putInt(value, transform, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a standard {@code int}.
     *
     * @param value The value.
     * @param order The byte order.
     * @return This buffer instance.
     */
    public ByteMessage putInt(int value, ByteOrder order) {
        putInt(value, ValueType.NORMAL, order);
        return this;
    }

    /**
     * Writes a value as a {@code long}.
     *
     * @param value The value.
     * @param transform The transformation type.
     * @param order The byte order.
     * @return This buffer instance.
     * @throws UnsupportedOperationException If middle or inverse-middle value types are selected.
     */
    public ByteMessage putLong(long value, ValueType transform, ByteOrder order) {
        switch (order) {
            case BIG:
                put((int) (value >> 56));
                put((int) (value >> 48));
                put((int) (value >> 40));
                put((int) (value >> 32));
                put((int) (value >> 24));
                put((int) (value >> 16));
                put((int) (value >> 8));
                put((int) value, transform);
                break;
            case MIDDLE:
                throw new UnsupportedOperationException("Middle-endian long is not implemented!");
            case INVERSE_MIDDLE:
                throw new UnsupportedOperationException("Inverse-middle-endian long is not implemented!");
            case LITTLE:
                put((int) value, transform);
                put((int) (value >> 8));
                put((int) (value >> 16));
                put((int) (value >> 24));
                put((int) (value >> 32));
                put((int) (value >> 40));
                put((int) (value >> 48));
                put((int) (value >> 56));
                break;
        }
        return this;
    }

    /**
     * Writes a value as a standard big-endian {@code long}.
     *
     * @param value The value.
     * @return This buffer instance.
     */
    public ByteMessage putLong(long value) {
        putLong(value, ValueType.NORMAL, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a big-endian {@code long}.
     *
     * @param value The value.
     * @param transform The transformation type.
     * @return This buffer instance.
     */
    public ByteMessage putLong(long value, ValueType transform) {
        putLong(value, transform, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a standard {@code long}.
     *
     * @param value The value.
     * @param order The byte order.
     * @return This buffer instance.
     */
    public ByteMessage putLong(long value, ByteOrder order) {
        putLong(value, ValueType.NORMAL, order);
        return this;
    }

    /**
     * Writes a RuneScape {@code String} value.
     *
     * @param value The value.
     * @return This buffer instance.
     */
    public ByteMessage putString(String value) {
        for (byte charValue : value.getBytes()) {
            put(charValue);
        }
        put(10);
        return this;
    }

    /**
     * Reads a value as a {@code byte}.
     *
     * @param signed If the value is signed.
     * @param transform The transformation type.
     * @return The read value.
     */
    public int get(boolean signed, ValueType transform) {
        int value = buf.readByte();
        switch (transform) {
            case ADD:
                value = value - 128;
                break;
            case NEGATE:
                value = -value;
                break;
            case SUBTRACT:
                value = 128 - value;
                break;
            case NORMAL:
                break;
        }
        return signed ? value : value & 0xff;
    }

    /**
     * Reads a standard signed {@code byte}.
     *
     * @return The read value.
     */
    public int get() {
        return get(true, ValueType.NORMAL);
    }

    /**
     * Reads a standard {@code byte}.
     *
     * @param signed If the value is signed.
     * @return The read value.
     */
    public int get(boolean signed) {
        return get(signed, ValueType.NORMAL);
    }

    /**
     * Reads a signed {@code byte}.
     *
     * @param transform The transformation type.
     * @return The read value.
     */
    public int get(ValueType transform) {
        return get(true, transform);
    }

    /**
     * Reads a {@code short} value.
     *
     * @param signed If the value is signed.
     * @param transform The transformation type.
     * @param order The byte order.
     * @return The read value.
     * @throws UnsupportedOperationException If middle or inverse-middle value types are selected.
     */
    public int getShort(boolean signed, ValueType transform, ByteOrder order) {
        int value = 0;
        switch (order) {
            case BIG:
                value |= get(false) << 8;
                value |= get(false, transform);
                break;
            case MIDDLE:
                throw new UnsupportedOperationException("Middle-endian short is impossible!");
            case INVERSE_MIDDLE:
                throw new UnsupportedOperationException("Inverse-middle-endian short is impossible!");
            case LITTLE:
                value |= get(false, transform);
                value |= get(false) << 8;
                break;
        }
        return signed ? value : value & 0xffff;
    }

    /**
     * Reads a standard signed big-endian {@code short}.
     *
     * @return The read value.
     */
    public int getShort() {
        return getShort(true, ValueType.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a standard big-endian {@code short}.
     *
     * @param signed If the value is signed.
     * @return The read value.
     */
    public int getShort(boolean signed) {
        return getShort(signed, ValueType.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a signed big-endian {@code short}.
     *
     * @param transform The transformation type.
     * @return The read value.
     */
    public int getShort(ValueType transform) {
        return getShort(true, transform, ByteOrder.BIG);
    }

    /**
     * Reads a big-endian {@code short}.
     *
     * @param signed If the value is signed.
     * @param transform The transformation type.
     * @return The read value.
     */
    public int getShort(boolean signed, ValueType transform) {
        return getShort(signed, transform, ByteOrder.BIG);
    }

    /**
     * Reads a signed standard {@code short}.
     *
     * @param order The byte order.
     * @return The read value.
     */
    public int getShort(ByteOrder order) {
        return getShort(true, ValueType.NORMAL, order);
    }

    /**
     * Reads a standard {@code short}.
     *
     * @param signed If the value is signed.
     * @param order The byte order.
     * @return The read value.
     */
    public int getShort(boolean signed, ByteOrder order) {
        return getShort(signed, ValueType.NORMAL, order);
    }

    /**
     * Reads a signed {@code short}.
     *
     * @param transform The transformation type.
     * @param order The byte order.
     * @return The read value.
     */
    public int getShort(ValueType transform, ByteOrder order) {
        return getShort(true, transform, order);
    }

    /**
     * Reads an {@code int}.
     *
     * @param signed If the value is signed.
     * @param transform The transformation type.
     * @param order The byte order.
     * @return The read value.
     */
    public int getInt(boolean signed, ValueType transform, ByteOrder order) {
        long value = 0;
        switch (order) {
            case BIG:
                value |= get(false) << 24;
                value |= get(false) << 16;
                value |= get(false) << 8;
                value |= get(false, transform);
                break;
            case MIDDLE:
                value |= get(false) << 8;
                value |= get(false, transform);
                value |= get(false) << 24;
                value |= get(false) << 16;
                break;
            case INVERSE_MIDDLE:
                value |= get(false) << 16;
                value |= get(false) << 24;
                value |= get(false, transform);
                value |= get(false) << 8;
                break;
            case LITTLE:
                value |= get(false, transform);
                value |= get(false) << 8;
                value |= get(false) << 16;
                value |= get(false) << 24;
                break;
        }
        return (int) (signed ? value : value & 0xffffffffL);
    }

    /**
     * Reads a signed standard big-endian {@code int}.
     *
     * @return The read value.
     */
    public int getInt() {
        return getInt(true, ValueType.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a standard big-endian {@code int}.
     *
     * @param signed If the value is signed.
     * @return The read value.
     */
    public int getInt(boolean signed) {
        return getInt(signed, ValueType.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a signed big-endian {@code int}.
     *
     * @param transform The transformation type.
     * @return The read value.
     */
    public int getInt(ValueType transform) {
        return getInt(true, transform, ByteOrder.BIG);
    }

    /**
     * Reads a big-endian {@code int}.
     *
     * @param signed If the value is signed.
     * @param transform The transformation type.
     * @return The read value.
     */
    public int getInt(boolean signed, ValueType transform) {
        return getInt(signed, transform, ByteOrder.BIG);
    }

    /**
     * Reads a signed standard {@code int}.
     *
     * @param order The byte order.
     * @return The read value.
     */
    public int getInt(ByteOrder order) {
        return getInt(true, ValueType.NORMAL, order);
    }

    /**
     * Reads a standard {@code int}.
     *
     * @param signed If the value is signed.
     * @param order The byte order.
     * @return The read value.
     */
    public int getInt(boolean signed, ByteOrder order) {
        return getInt(signed, ValueType.NORMAL, order);
    }

    /**
     * Reads a signed {@code int}.
     *
     * @param transform The transformation type.
     * @param order The byte order.
     * @return The read value.
     */
    public int getInt(ValueType transform, ByteOrder order) {
        return getInt(true, transform, order);
    }

    /**
     * Reads a signed {@code long} value.
     *
     * @param transform The transformation type.
     * @param order The byte order.
     * @return The read value.
     * @throws UnsupportedOperationException If middle or inverse-middle value types are selected.
     */
    public long getLong(ValueType transform, ByteOrder order) {
        long value = 0;
        switch (order) {
            case BIG:
                value |= (long) get(false) << 56L;
                value |= (long) get(false) << 48L;
                value |= (long) get(false) << 40L;
                value |= (long) get(false) << 32L;
                value |= (long) get(false) << 24L;
                value |= (long) get(false) << 16L;
                value |= (long) get(false) << 8L;
                value |= get(false, transform);
                break;
            case INVERSE_MIDDLE:
            case MIDDLE:
                throw new UnsupportedOperationException("Middle and inverse-middle value types not supported!");
            case LITTLE:
                value |= get(false, transform);
                value |= (long) get(false) << 8L;
                value |= (long) get(false) << 16L;
                value |= (long) get(false) << 24L;
                value |= (long) get(false) << 32L;
                value |= (long) get(false) << 40L;
                value |= (long) get(false) << 48L;
                value |= (long) get(false) << 56L;
                break;
        }
        return value;
    }

    /**
     * Reads a signed standard big-endian {@code long}.
     *
     * @return The read value.
     */
    public long getLong() {
        return getLong(ValueType.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a signed big-endian {@code long}.
     *
     * @param transform The transformation type.
     * @return The read value.
     */
    public long getLong(ValueType transform) {
        return getLong(transform, ByteOrder.BIG);
    }

    /**
     * Reads a signed standard {@code long}.
     *
     * @param order The byte order.
     * @return The read value.
     */
    public long getLong(ByteOrder order) {
        return getLong(ValueType.NORMAL, order);
    }

    /**
     * Reads a RuneScape {@code String} value.
     *
     * @return The read value.
     */
    public String getString() {
        byte temp;
        StringBuilder b = new StringBuilder();
        while ((temp = (byte) get()) != 10) {
            b.append((char) temp);
        }
        return b.toString();
    }

    /**
     * Reads bytes into an array, starting at the current position.
     *
     * @param amount The amount of bytes to read.
     * @return The read bytes.
     */
    public byte[] getBytes(int amount) {
        return getBytes(amount, ValueType.NORMAL);
    }

    /**
     * Reads bytes into an array, starting at the current position.
     *
     * @param amount The amount of bytes to read.
     * @param transform The byte transformation.
     * @return The read bytes.
     */
    public byte[] getBytes(int amount, ValueType transform) {
        byte[] data = new byte[amount];
        for (int i = 0; i < amount; i++) {
            data[i] = (byte) get(transform);
        }
        return data;
    }

    /**
     * Reads bytes in reverse into an array, starting at {@code current_position + amount} until the
     * current position.
     *
     * @param amount The amount of bytes to read.
     * @param transform The byte transformation.
     * @return The read bytes.
     */
    public byte[] getBytesReverse(int amount, ValueType transform) {
        byte[] data = new byte[amount];
        int dataPosition = 0;
        for (int i = buf.readerIndex() + amount - 1; i >= buf.readerIndex(); i--) {
            int value = buf.getByte(i);
            switch (transform) {
                case ADD:
                    value -= 128;
                    break;
                case NEGATE:
                    value = -value;
                    break;
                case SUBTRACT:
                    value = 128 - value;
                    break;
                case NORMAL:
                    break;
            }
            data[dataPosition++] = (byte) value;
        }
        return data;
    }

    /**
     * @return The backing buffer.
     */
    public ByteBuf getBuffer() {
        return buf;
    }

    /**
     * @return The opcode.
     */
    public int getOpcode() {
        return opcode;
    }

    /**
     * @return The header type.
     */
    public MessageType getType() {
        return type;
    }
}
