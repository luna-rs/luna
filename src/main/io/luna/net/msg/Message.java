package io.luna.net.msg;

/**
 * The interface that gives the implementing class the ability to be sent and
 * received as data through {@linkplain io.netty.channel.ChannelHandler channel
 * handlers}. All subtype classes of a message are themselves messages, and
 * retain the ability to be manipulated as such. The {@code Message} interface
 * has no methods or fields and serves only to identify the semantics of being a
 * message.
 *
 * @author lare96 <http://github.com/lare96>
 */
public interface Message {}
