package io.luna.net.codec;

import static com.google.common.base.Preconditions.checkArgument;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.PooledByteBufAllocator;

/**
 * The {@link ByteBuf} wrapper tailored to the specifications of the RuneScape
 * protocol. These buffers are backed by direct buffers for better performance
 * but are pooled to avoid costly allocation and deallocation of the buffers.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class ByteMessage extends DefaultByteBufHolder {

    /**
     * A buffer pool that will help reduce the overhead from allocating and
     * deallocating direct buffers.
     */
    private static final ByteBufAllocator ALLOC = PooledByteBufAllocator.DEFAULT;

    /**
     * An array of bit masks used for bitwise operations.
     */
    public static final int[] BIT_MASK = new int[32];

    /**
     * The default capacity of this buffer.
     */
    private static final int DEFAULT_CAP = 128;

    /**
     * The backing byte buffer used to read and write data.
     */
    private ByteBuf buf;

    /**
     * The position of the buffer when a variable length message is created.
     */
    private int varLengthIndex = 0;

    /**
     * The current bit position when writing bits.
     */
    private int bitIndex = 0;

    /**
     * Creates a new {@link ByteMessage} with the {@code buf} backing buffer.
     *
     * @param buf The backing buffer used to read and write data.
     */
    private ByteMessage(ByteBuf buf) {
        super(buf);
        this.buf = buf;
    }

    /**
     * A static initialization block that calculates the bit masks.
     */
    static {
        for (int i = 0; i < BIT_MASK.length; i++) {
            BIT_MASK[i] = (1 << i) - 1;
        }
    }

    /**
     * Creates a new {@link ByteMessage} with the {@code buf} backing buffer.
     *
     * @param buf The backing buffer used to read and write data.
     * @return The newly created buffer.
     */
    public static ByteMessage create(ByteBuf buf) {
        return new ByteMessage(buf);
    }

    /**
     * Creates a new {@link ByteMessage} with the {@code cap} as the capacity.
     * The returned buffer will most likely be a direct buffer.
     *
     * @param cap The capacity of the buffer.
     * @return The newly created buffer.
     */
    public static ByteMessage create(int cap) {
        checkArgument(cap >= 0, "cap < 0");
        return ByteMessage.create(ALLOC.buffer(cap));
    }

    /**
     * Creates a new {@link ByteMessage} with the default capacity. The returned
     * buffer will most likely be a direct buffer.
     *
     * @return The newly created buffer.
     */
    public static ByteMessage create() {
        return ByteMessage.create(DEFAULT_CAP);
    }

    /**
     * Prepares the buffer for writing bits.
     */
    public void startBitAccess() {
        bitIndex = buf.writerIndex() * 8;
    }

    /**
     * Prepares the buffer for writing bytes.
     */
    public void endBitAccess() {
        buf.writerIndex((bitIndex + 7) / 8);
    }

    /**
     * Builds a new message header.
     *
     * @param opcode The opcode of the message.
     * @return An instance of this byte message.
     */
    public ByteMessage message(int opcode) {
        put(opcode);
        return this;
    }

    /**
     * Builds a new message header for a variable length message. Note that the
     * corresponding {@code endVarMessage()} method must be called to finish the
     * message.
     *
     * @param opcode The opcode of the message.
     * @return An instance of this byte message.
     */
    public ByteMessage varMessage(int opcode) {
        message(opcode);
        varLengthIndex = buf.writerIndex();
        put(0);
        return this;
    }

    /**
     * Builds a new message header for a variable length message, where the
     * length is written as a {@code short} instead of a {@code byte}. Note that
     * the corresponding {@code endVarShortMessage()} method must be called to
     * finish the message.
     *
     * @param opcode The opcode of the message.
     * @return An instance of this byte message.
     */
    public ByteMessage varShortMessage(int opcode) {
        message(opcode);
        varLengthIndex = buf.writerIndex();
        putShort(0);
        return this;
    }

    /**
     * Finishes a variable message header by writing the actual message length
     * at the length {@code byte} position. Call this when the construction of
     * the actual variable length message is complete.
     *
     * @return An instance of this byte message.
     */
    public ByteMessage endVarMessage() {
        buf.setByte(varLengthIndex, (byte) (buf.writerIndex() - varLengthIndex - 1));
        return this;
    }

    /**
     * Finishes a variable message header by writing the actual message length
     * at the length {@code short} position. Call this when the construction of
     * the actual variable length message is complete.
     *
     * @return An instance of this byte message.
     */
    public ByteMessage endVarShortMessage() {
        buf.setShort(varLengthIndex, (short) (buf.writerIndex() - varLengthIndex - 2));
        return this;
    }

    /**
     * Writes the bytes from the argued buffer into this buffer. This method
     * does not modify the argued buffer, and please do not flip the buffer
     * beforehand.
     *
     * @param from The argued buffer that bytes will be written from.
     * @return An instance of this byte message.
     */
    public ByteMessage putBytes(ByteBuf from) {
        for (int i = 0; i < from.writerIndex(); i++) {
            put(from.getByte(i));
        }
        return this;
    }

    /**
     * Writes the bytes from the argued buffer into this buffer.
     *
     * @param from The argued buffer that bytes will be written from.
     * @return An instance of this byte message.
     */
    public ByteMessage putBytes(byte[] from, int size) {
        buf.writeBytes(from, 0, size);
        return this;
    }

    /**
     * Writes the bytes from the argued byte array into this buffer, in reverse.
     *
     * @param data The data to write to this buffer.
     */
    public ByteMessage putBytesReverse(byte[] data) {
        for (int i = data.length - 1; i >= 0; i--) {
            put(data[i]);
        }
        return this;
    }

    /**
     * Writes the value as a variable amount of bits.
     *
     * @param amount The amount of bits to write.
     * @param value The value of the bits.
     * @return An instance of this byte message.
     * @throws IllegalArgumentException If the number of bits is not between
     *         {@code 1} and {@code 32} inclusive.
     */
    public ByteMessage putBits(int amount, int value) {
        if (amount < 0 || amount > 32)
            throw new IllegalArgumentException("Number of bits must be between 1 and 32 inclusive.");
        int bytePos = bitIndex >> 3;
        int bitOffset = 8 - (bitIndex & 7);
        bitIndex = bitIndex + amount;
        int requiredSpace = bytePos - buf.writerIndex() + 1;
        requiredSpace += (amount + 7) / 8;
        if (buf.writableBytes() < requiredSpace) {
            ByteBuf old = buf;
            buf = ALLOC.buffer(old.capacity() + requiredSpace);
            buf.writeBytes(old);
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
     * Writes a boolean bit flag.
     *
     * @param flag The flag to write.
     * @return An instance of this byte message.
     */
    public ByteMessage putBit(boolean flag) {
        putBits(1, flag ? 1 : 0);
        return this;
    }

    /**
     * Writes a value as a {@code byte}.
     *
     * @param value The value to write.
     * @param type The byte transformation type
     * @return An instance of this byte message.
     */
    public ByteMessage put(int value, ByteTransform type) {
        switch (type) {
        case A:
            value += 128;
            break;
        case C:
            value = -value;
            break;
        case S:
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
     * @param value The value to write.
     * @return An instance of this byte message.
     */
    public ByteMessage put(int value) {
        put(value, ByteTransform.NORMAL);
        return this;
    }

    /**
     * Writes a value as a {@code short}.
     *
     * @param value The value to write.
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return An instance of this byte message.
     * @throws UnsupportedOperationException If middle or inverse-middle value
     *         types are selected.
     */
    public ByteMessage putShort(int value, ByteTransform type, ByteOrder order) {
        switch (order) {
        case BIG:
            put(value >> 8);
            put(value, type);
            break;
        case MIDDLE:
            throw new UnsupportedOperationException("Middle-endian short is impossible.");
        case INVERSE_MIDDLE:
            throw new UnsupportedOperationException("Inversed-middle-endian short is impossible.");
        case LITTLE:
            put(value, type);
            put(value >> 8);
            break;
        }
        return this;
    }

    /**
     * Writes a value as a normal big-endian {@code short}.
     *
     * @param value The value to write.
     * @return An instance of this byte message.
     */
    public ByteMessage putShort(int value) {
        putShort(value, ByteTransform.NORMAL, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a big-endian {@code short}.
     *
     * @param value The value to write.
     * @param type The byte transformation type
     * @return An instance of this byte message.
     */
    public ByteMessage putShort(int value, ByteTransform type) {
        putShort(value, type, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a standard {@code short}.
     *
     * @param value The value to write.
     * @param order The byte endianness type.
     * @return An instance of this byte message.
     */
    public ByteMessage putShort(int value, ByteOrder order) {
        putShort(value, ByteTransform.NORMAL, order);
        return this;
    }

    /**
     * Writes a value as an {@code int}.
     *
     * @param value The value to write.
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return An instance of this byte message.
     */
    public ByteMessage putInt(int value, ByteTransform type, ByteOrder order) {
        switch (order) {
        case BIG:
            put(value >> 24);
            put(value >> 16);
            put(value >> 8);
            put(value, type);
            break;
        case MIDDLE:
            put(value >> 8);
            put(value, type);
            put(value >> 24);
            put(value >> 16);
            break;
        case INVERSE_MIDDLE:
            put(value >> 16);
            put(value >> 24);
            put(value, type);
            put(value >> 8);
            break;
        case LITTLE:
            put(value, type);
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
     * @param value The value to write.
     * @return An instance of this byte message.
     */
    public ByteMessage putInt(int value) {
        putInt(value, ByteTransform.NORMAL, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a big-endian {@code int}.
     *
     * @param value The value to write.
     * @param type The byte transformation type
     * @return An instance of this byte message.
     */
    public ByteMessage putInt(int value, ByteTransform type) {
        putInt(value, type, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a standard {@code int}.
     *
     * @param value The value to write.
     * @param order The byte endianness type.
     * @return An instance of this byte message.
     */
    public ByteMessage putInt(int value, ByteOrder order) {
        putInt(value, ByteTransform.NORMAL, order);
        return this;
    }

    /**
     * Writes a value as a {@code long}.
     *
     * @param value The value to write.
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return An instance of this byte message.
     * @throws UnsupportedOperationException If middle or inverse-middle value
     *         types are selected.
     */
    public ByteMessage putLong(long value, ByteTransform type, ByteOrder order) {
        switch (order) {
        case BIG:
            put((int) (value >> 56));
            put((int) (value >> 48));
            put((int) (value >> 40));
            put((int) (value >> 32));
            put((int) (value >> 24));
            put((int) (value >> 16));
            put((int) (value >> 8));
            put((int) value, type);
            break;
        case MIDDLE:
            throw new UnsupportedOperationException("Middle-endian long is not implemented!");
        case INVERSE_MIDDLE:
            throw new UnsupportedOperationException("Inverse-middle-endian long is not implemented!");
        case LITTLE:
            put((int) value, type);
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
     * @param value The value to write.
     * @return An instance of this byte message.
     */
    public ByteMessage putLong(long value) {
        putLong(value, ByteTransform.NORMAL, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a big-endian {@code long}.
     *
     * @param value The value to write.
     * @param type The byte transformation type
     * @return An instance of this byte message.
     */
    public ByteMessage putLong(long value, ByteTransform type) {
        putLong(value, type, ByteOrder.BIG);
        return this;
    }

    /**
     * Writes a value as a standard {@code long}.
     *
     * @param value The value to write.
     * @param order The byte endianness type. to write.
     * @return An instance of this byte message.
     */
    public ByteMessage putLong(long value, ByteOrder order) {
        putLong(value, ByteTransform.NORMAL, order);
        return this;
    }

    /**
     * Writes a RuneScape {@code String} value.
     *
     * @param string The string to write.
     * @return An instance of this byte message.
     */
    public ByteMessage putString(String string) {
        for (byte value : string.getBytes()) {
            put(value);
        }
        put(10);
        return this;
    }

    /**
     * Reads a value as a {@code byte}.
     *
     * @param signed if the byte is signed.
     * @param type The byte transformation type
     * @return The value of the byte.
     */
    public int get(boolean signed, ByteTransform type) {
        int value = buf.readByte();
        switch (type) {
        case A:
            value = value - 128;
            break;
        case C:
            value = -value;
            break;
        case S:
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
     * @return The value of the byte.
     */
    public int get() {
        return get(true, ByteTransform.NORMAL);
    }

    /**
     * Reads a standard {@code byte}.
     *
     * @param signed If the byte is signed.
     * @return The value of the byte.
     */
    public int get(boolean signed) {
        return get(signed, ByteTransform.NORMAL);
    }

    /**
     * Reads a signed {@code byte}.
     *
     * @param type The byte transformation type
     * @return The value of the byte.
     */
    public int get(ByteTransform type) {
        return get(true, type);
    }

    /**
     * Reads a {@code short} value.
     *
     * @param signed If the short is signed.
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return The value of the short.
     * @throws UnsupportedOperationException if middle or inverse-middle value
     *         types are selected.
     */
    public int getShort(boolean signed, ByteTransform type, ByteOrder order) {
        int value = 0;
        switch (order) {
        case BIG:
            value |= get(false) << 8;
            value |= get(false, type);
            break;
        case MIDDLE:
            throw new UnsupportedOperationException("Middle-endian short is impossible!");
        case INVERSE_MIDDLE:
            throw new UnsupportedOperationException("Inverse-middle-endian short is impossible!");
        case LITTLE:
            value |= get(false, type);
            value |= get(false) << 8;
            break;
        }
        return signed ? value : value & 0xffff;
    }

    /**
     * Reads a standard signed big-endian {@code short}.
     *
     * @return The value of the short.
     */
    public int getShort() {
        return getShort(true, ByteTransform.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a standard big-endian {@code short}.
     *
     * @param signed If the short is signed.
     * @return The value of the short.
     */
    public int getShort(boolean signed) {
        return getShort(signed, ByteTransform.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a signed big-endian {@code short}.
     *
     * @param type The byte transformation type
     * @return The value of the short.
     */
    public int getShort(ByteTransform type) {
        return getShort(true, type, ByteOrder.BIG);
    }

    /**
     * Reads a big-endian {@code short}.
     *
     * @param signed If the short is signed.
     * @param type The byte transformation type
     * @return The value of the short.
     */
    public int getShort(boolean signed, ByteTransform type) {
        return getShort(signed, type, ByteOrder.BIG);
    }

    /**
     * Reads a signed standard {@code short}.
     *
     * @param order The byte endianness type.
     * @return The value of the short.
     */
    public int getShort(ByteOrder order) {
        return getShort(true, ByteTransform.NORMAL, order);
    }

    /**
     * Reads a standard {@code short}.
     *
     * @param signed If the short is signed.
     * @param order The byte endianness type.
     * @return The value of the short.
     */
    public int getShort(boolean signed, ByteOrder order) {
        return getShort(signed, ByteTransform.NORMAL, order);
    }

    /**
     * Reads a signed {@code short}.
     *
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return The value of the short.
     */
    public int getShort(ByteTransform type, ByteOrder order) {
        return getShort(true, type, order);
    }

    /**
     * Reads an {@code int}.
     *
     * @param signed If the integer is signed.
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return The value of the integer.
     */
    public int getInt(boolean signed, ByteTransform type, ByteOrder order) {
        long value = 0;
        switch (order) {
        case BIG:
            value |= get(false) << 24;
            value |= get(false) << 16;
            value |= get(false) << 8;
            value |= get(false, type);
            break;
        case MIDDLE:
            value |= get(false) << 8;
            value |= get(false, type);
            value |= get(false) << 24;
            value |= get(false) << 16;
            break;
        case INVERSE_MIDDLE:
            value |= get(false) << 16;
            value |= get(false) << 24;
            value |= get(false, type);
            value |= get(false) << 8;
            break;
        case LITTLE:
            value |= get(false, type);
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
     * @return The value of the integer.
     */
    public int getInt() {
        return getInt(true, ByteTransform.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a standard big-endian {@code int}.
     *
     * @param signed If the integer is signed.
     * @return The value of the integer.
     */
    public int getInt(boolean signed) {
        return getInt(signed, ByteTransform.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a signed big-endian {@code int}.
     *
     * @param type The byte transformation type
     * @return The value of the integer.
     */
    public int getInt(ByteTransform type) {
        return getInt(true, type, ByteOrder.BIG);
    }

    /**
     * Reads a big-endian {@code int}.
     *
     * @param signed If the integer is signed.
     * @param type The byte transformation type
     * @return The value of the integer.
     */
    public int getInt(boolean signed, ByteTransform type) {
        return getInt(signed, type, ByteOrder.BIG);
    }

    /**
     * Reads a signed standard {@code int}.
     *
     * @param order The byte endianness type.
     * @return The value of the integer.
     */
    public int getInt(ByteOrder order) {
        return getInt(true, ByteTransform.NORMAL, order);
    }

    /**
     * Reads a standard {@code int}.
     *
     * @param signed If the integer is signed.
     * @param order The byte endianness type.
     * @return The value of the integer.
     */
    public int getInt(boolean signed, ByteOrder order) {
        return getInt(signed, ByteTransform.NORMAL, order);
    }

    /**
     * Reads a signed {@code int}.
     *
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return The value of the integer.
     */
    public int getInt(ByteTransform type, ByteOrder order) {
        return getInt(true, type, order);
    }

    /**
     * Reads a signed {@code long} value.
     *
     * @param type The byte transformation type
     * @param order The byte endianness type.
     * @return The value of the long.
     * @throws UnsupportedOperationException if middle or inverse-middle value
     *         types are selected.
     */
    public long getLong(ByteTransform type, ByteOrder order) {
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
            value |= get(false, type);
            break;
        case INVERSE_MIDDLE:
        case MIDDLE:
            throw new UnsupportedOperationException("Middle and inverse-middle value types not supported!");
        case LITTLE:
            value |= get(false, type);
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
     * @return The value of the long.
     */
    public long getLong() {
        return getLong(ByteTransform.NORMAL, ByteOrder.BIG);
    }

    /**
     * Reads a signed big-endian {@code long}.
     *
     * @param type The byte transformation type.
     * @return The value of the long.
     */
    public long getLong(ByteTransform type) {
        return getLong(type, ByteOrder.BIG);
    }

    /**
     * Reads a signed standard {@code long}.
     *
     * @param order The byte endianness type.
     * @return The value of the long.
     */
    public long getLong(ByteOrder order) {
        return getLong(ByteTransform.NORMAL, order);
    }

    /**
     * Reads a RuneScape {@code String} value.
     *
     * @return The value of the string.
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
     * Reads the amount of bytes into the array, starting at the current
     * position.
     *
     * @param amount The amount to read.
     * @return A buffer filled with the data.
     */
    public byte[] getBytes(int amount) {
        return getBytes(amount, ByteTransform.NORMAL);
    }

    /**
     * Reads the amount of bytes into a byte array, starting at the current
     * position.
     *
     * @param amount The amount of bytes.
     * @param type The byte transformation type of each byte.
     * @return A buffer filled with the data.
     */
    public byte[] getBytes(int amount, ByteTransform type) {
        byte[] data = new byte[amount];
        for (int i = 0; i < amount; i++) {
            data[i] = (byte) get(type);
        }
        return data;
    }

    /**
     * Reads the amount of bytes from the buffer in reverse, starting at
     * {@code current_position + amount} and reading in reverse until the
     * current position.
     *
     * @param amount The amount of bytes to read.
     * @param type The byte transformation type of each byte.
     * @return A buffer filled with the data.
     */
    public byte[] getBytesReverse(int amount, ByteTransform type) {
        byte[] data = new byte[amount];
        int dataPosition = 0;
        for (int i = buf.readerIndex() + amount - 1; i >= buf.readerIndex(); i--) {
            int value = buf.getByte(i);
            switch (type) {
            case A:
                value -= 128;
                break;
            case C:
                value = -value;
                break;
            case S:
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
     * Gets the backing byte buffer used to read and write data.
     *
     * @return The backing byte buffer.
     */
    public ByteBuf getBuffer() {
        return buf;
    }
}
