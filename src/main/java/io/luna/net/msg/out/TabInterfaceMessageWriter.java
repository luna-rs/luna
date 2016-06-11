package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundMessageWriter;

/**
 * An {@link OutboundMessageWriter} implementation that displays an interface on a sidebar tab.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class TabInterfaceMessageWriter extends OutboundMessageWriter {

    /**
     * The identifier for the tab to send the interface on.
     * <p>
     * <table BORDER CELLPADDING=3 CELLSPACING=1> <tr> <td ALIGN=CENTER><em>Tab</em></td> <td
     * ALIGN=CENTER><em>Identifier</em></td> </tr> <tr> <td>Combat</td> <td>0</td> </tr> <tr> <td>Skills</td> <td>1</td>
     * </tr> <tr> <td>Quest</td> <td>2</td> </tr> <tr> <td>Inventory</td> <td>3</td> </tr> <tr> <td>Equipment</td> <td>4</td>
     * </tr> <tr> <td>Prayer</td> <td>5</td> </tr> <tr> <td>Magic</td> <td>6</td> </tr> <tr> <td>Quest</td> <td>7</td> </tr>
     * <tr> <td>Friends</td> <td>8</td> </tr> <tr> <td>Ignores</td> <td>9</td> </tr> <tr> <td>Logout</td> <td>10</td> </tr>
     * <tr> <td>Settings</td> <td>11</td> </tr> <td>Emotes</td> <td>12</td> </tr> <td>Music</td> <td>13</td> </tr> </table>
     */
    private final int tabId;

    /**
     * The identifier for the interface to send on the tab.
     */
    private final int interfaceId;

    /**
     * Creates a new {@link TabInterfaceMessageWriter}.
     *
     * @param tabId The identifier for the tab to send the interface on.
     * @param interfaceId The identifier for the interface to send on the tab.
     */
    public TabInterfaceMessageWriter(int tabId, int interfaceId) {
        this.tabId = tabId;
        this.interfaceId = interfaceId;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(71);
        msg.putShort(interfaceId);
        msg.put(tabId, ByteTransform.A);
        return msg;
    }
}
