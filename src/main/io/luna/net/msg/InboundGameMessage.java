package io.luna.net.msg;

import io.luna.game.model.mobile.Player;
import io.luna.net.msg.in.ReceiveChatMessage;
import io.luna.net.msg.in.ReceivePickupItemMessage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import plugin.Plugin;

import static com.google.common.base.Preconditions.checkState;

/**
 * An inbound logic handler for {@link GameMessage}s that also contains a static array of message sizes and logic handlers.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class InboundGameMessage {

    // TODO: Parse message sizes and handlers

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(InboundGameMessage.class);

    /**
     * An array representation of the sizes of all the inbound messages.
     */
    public static final int SIZES[] = new int[257];

    /**
     * An array representation of all of the inbound {@link GameMessage} handlers.
     */
    public static final InboundGameMessage[] HANDLERS = new InboundGameMessage[257];

    static {
        addInboundMessage(4, ReceiveChatMessage.class);
        addInboundMessage(236, ReceivePickupItemMessage.class);
    }

    static {
        SIZES[0] = 0;
        SIZES[1] = 0;
        SIZES[2] = 0;
        SIZES[3] = 1;
        SIZES[4] = -1;
        SIZES[5] = 0;
        SIZES[6] = 0;
        SIZES[7] = 0;
        SIZES[8] = 0;
        SIZES[9] = 0;
        SIZES[10] = 0;
        SIZES[11] = 0;
        SIZES[12] = 0;
        SIZES[13] = 0;
        SIZES[14] = 8;
        SIZES[15] = 0;
        SIZES[16] = 6;
        SIZES[17] = 2;
        SIZES[18] = 2;
        SIZES[19] = 0;
        SIZES[20] = 0;
        SIZES[21] = 2;
        SIZES[22] = 0;
        SIZES[23] = 6;
        SIZES[24] = 0;
        SIZES[25] = 12;
        SIZES[26] = 0;
        SIZES[27] = 0;
        SIZES[28] = 0;
        SIZES[29] = 0;
        SIZES[30] = 0;
        SIZES[31] = 0;
        SIZES[32] = 0;
        SIZES[33] = 0;
        SIZES[34] = 0;
        SIZES[35] = 8;
        SIZES[36] = 4;
        SIZES[37] = 0;
        SIZES[38] = 0;
        SIZES[39] = 2;
        SIZES[40] = 2;
        SIZES[41] = 6;
        SIZES[42] = 0;
        SIZES[43] = 6;
        SIZES[44] = 0;
        SIZES[45] = -1;
        SIZES[46] = 0;
        SIZES[47] = 0;
        SIZES[48] = 0;
        SIZES[49] = 0;
        SIZES[50] = 0;
        SIZES[51] = 0;
        SIZES[52] = 0;
        SIZES[53] = 12;
        SIZES[54] = 0;
        SIZES[55] = 0;
        SIZES[56] = 0;
        SIZES[57] = 0;
        SIZES[58] = 8;
        SIZES[59] = 0;
        SIZES[60] = 0;
        SIZES[61] = 8;
        SIZES[62] = 0;
        SIZES[63] = 0;
        SIZES[64] = 0;
        SIZES[65] = 0;
        SIZES[66] = 0;
        SIZES[67] = 0;
        SIZES[68] = 0;
        SIZES[69] = 0;
        SIZES[70] = 6;
        SIZES[71] = 0;
        SIZES[72] = 2;
        SIZES[73] = 2;
        SIZES[74] = 8;
        SIZES[75] = 6;
        SIZES[76] = 0;
        SIZES[77] = -1;
        SIZES[78] = 0;
        SIZES[79] = 6;
        SIZES[80] = 0;
        SIZES[81] = 0;
        SIZES[82] = 0;
        SIZES[83] = 0;
        SIZES[84] = 0;
        SIZES[85] = 1;
        SIZES[86] = 4;
        SIZES[87] = 6;
        SIZES[88] = 0;
        SIZES[89] = 0;
        SIZES[90] = 0;
        SIZES[91] = 0;
        SIZES[92] = 0;
        SIZES[93] = 0;
        SIZES[94] = 0;
        SIZES[95] = 3;
        SIZES[96] = 0;
        SIZES[97] = 0;
        SIZES[98] = -1;
        SIZES[99] = 0;
        SIZES[100] = 0;
        SIZES[101] = 13;
        SIZES[102] = 0;
        SIZES[103] = -1;
        SIZES[104] = 0;
        SIZES[105] = 0;
        SIZES[106] = 0;
        SIZES[107] = 0;
        SIZES[108] = 0;
        SIZES[109] = 0;
        SIZES[110] = 0;
        SIZES[111] = 0;
        SIZES[112] = 0;
        SIZES[113] = 0;
        SIZES[114] = 0;
        SIZES[115] = 0;
        SIZES[116] = 0;
        SIZES[117] = 6;
        SIZES[118] = 0;
        SIZES[119] = 0;
        SIZES[120] = 1;
        SIZES[121] = 0;
        SIZES[122] = 6;
        SIZES[123] = 0;
        SIZES[124] = 0;
        SIZES[125] = 0;
        SIZES[126] = -1;
        SIZES[127] = 0;
        SIZES[128] = 2;
        SIZES[129] = 6;
        SIZES[130] = 0;
        SIZES[131] = 4;
        SIZES[132] = 6;
        SIZES[133] = 8;
        SIZES[134] = 0;
        SIZES[135] = 6;
        SIZES[136] = 0;
        SIZES[137] = 0;
        SIZES[138] = 0;
        SIZES[139] = 2;
        SIZES[140] = 0;
        SIZES[141] = 0;
        SIZES[142] = 0;
        SIZES[143] = 0;
        SIZES[144] = 0;
        SIZES[145] = 6;
        SIZES[146] = 0;
        SIZES[147] = 0;
        SIZES[148] = 0;
        SIZES[149] = 0;
        SIZES[150] = 0;
        SIZES[151] = 0;
        SIZES[152] = 1;
        SIZES[153] = 2;
        SIZES[154] = 0;
        SIZES[155] = 2;
        SIZES[156] = 6;
        SIZES[157] = 0;
        SIZES[158] = 0;
        SIZES[159] = 0;
        SIZES[160] = 0;
        SIZES[161] = 0;
        SIZES[162] = 0;
        SIZES[163] = 0;
        SIZES[164] = -1;
        SIZES[165] = -1;
        SIZES[166] = 0;
        SIZES[167] = 0;
        SIZES[168] = 0;
        SIZES[169] = 0;
        SIZES[170] = 0;
        SIZES[171] = 0;
        SIZES[172] = 0;
        SIZES[173] = 0;
        SIZES[174] = 0;
        SIZES[175] = 0;
        SIZES[176] = 0;
        SIZES[177] = 0;
        SIZES[178] = 0;
        SIZES[179] = 0;
        SIZES[180] = 0;
        SIZES[181] = 8;
        SIZES[182] = 0;
        SIZES[183] = 3;
        SIZES[184] = 0;
        SIZES[185] = 2;
        SIZES[186] = 0;
        SIZES[187] = 0;
        SIZES[188] = 8;
        SIZES[189] = 1;
        SIZES[190] = 0;
        SIZES[191] = 0;
        SIZES[192] = 12;
        SIZES[193] = 0;
        SIZES[194] = 0;
        SIZES[195] = 0;
        SIZES[196] = 0;
        SIZES[197] = 0;
        SIZES[198] = 0;
        SIZES[199] = 0;
        SIZES[200] = 2;
        SIZES[201] = 0;
        SIZES[202] = 0;
        SIZES[203] = 0;
        SIZES[204] = 0;
        SIZES[205] = 0;
        SIZES[206] = 0;
        SIZES[207] = 0;
        SIZES[208] = 4;
        SIZES[209] = 0;
        SIZES[210] = 4;
        SIZES[211] = 0;
        SIZES[212] = 0;
        SIZES[213] = 0;
        SIZES[214] = 7;
        SIZES[215] = 8;
        SIZES[216] = 0;
        SIZES[217] = 0;
        SIZES[218] = 10;
        SIZES[219] = 0;
        SIZES[220] = 0;
        SIZES[221] = 0;
        SIZES[222] = 0;
        SIZES[223] = 0;
        SIZES[224] = 0;
        SIZES[225] = 0;
        SIZES[226] = -1;
        SIZES[227] = 0;
        SIZES[228] = 6;
        SIZES[229] = 0;
        SIZES[230] = 1;
        SIZES[231] = 0;
        SIZES[232] = 0;
        SIZES[233] = 0;
        SIZES[234] = 6;
        SIZES[235] = 0;
        SIZES[236] = 6;
        SIZES[237] = 8;
        SIZES[238] = 1;
        SIZES[239] = 0;
        SIZES[240] = 0;
        SIZES[241] = 4;
        SIZES[242] = 0;
        SIZES[243] = 0;
        SIZES[244] = 0;
        SIZES[245] = 0;
        SIZES[246] = -1;
        SIZES[247] = 0;
        SIZES[248] = -1;
        SIZES[249] = 4;
        SIZES[250] = 0;
        SIZES[251] = 0;
        SIZES[252] = 6;
        SIZES[253] = 6;
        SIZES[254] = 0;
        SIZES[255] = 0;
        SIZES[256] = 0;
    }

    /**
     * Read the {@code msg} and return the {@code Object} event that will be forwarded to the {@link PluginManager}, if any.
     * This is only used for the decoding, validation, basic logic stages of an incoming message. All event type logic should
     * be handled within {@link Plugin}s.
     *
     * @param player The player.
     * @param msg The message to read.
     * @return The {@code Object} that will be forwarded to a {@link Plugin}, {@code null} if no {@code Object} should be
     * forwarded.
     * @throws Exception If any exceptions are thrown. Will later be caught by the session logger.
     */
    public abstract Object readMessage(Player player, GameMessage msg) throws Exception;

    /**
     * Adds a handler to the static array of logic handlers.
     *
     * @param opcode The opcode of the logic handler.
     * @param clazz The {@link Class} Object of the logic handler.
     */
    public static void addInboundMessage(int opcode, Class<? extends InboundGameMessage> clazz) {
        checkState(HANDLERS[opcode] == null, "Opcode [" + opcode + "] already taken");

        try {
            HANDLERS[opcode] = clazz.newInstance();
        } catch (Exception e) {
            LOGGER.catching(Level.WARN, e);
        }
    }
}
