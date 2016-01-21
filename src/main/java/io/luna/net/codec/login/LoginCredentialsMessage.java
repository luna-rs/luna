package io.luna.net.codec.login;

import io.luna.net.codec.IsaacCipher;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

/**
 * An immutable upstream Netty message that contains the decoded data from the login protocol.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LoginCredentialsMessage {

    /**
     * The username of the player.
     */
    private final String username;

    /**
     * The password of the player.
     */
    private final String password;

    /**
     * The encryptor for encrypting game messages.
     */
    private final IsaacCipher encryptor;

    /**
     * The decryptor for decrypting game messages.
     */
    private final IsaacCipher decryptor;

    /**
     * The pipeline for the underlying {@link Channel}.
     */
    private final ChannelPipeline pipeline;

    /**
     * Creates a new {@link LoginCredentialsMessage}.
     *
     * @param username The username of the player.
     * @param password The password of the player.
     * @param encryptor The encryptor for encrypting game messages.
     * @param decryptor The decryptor for decrypting game messages.
     */
    public LoginCredentialsMessage(String username, String password, IsaacCipher encryptor, IsaacCipher decryptor, ChannelPipeline pipeline) {
        this.username = username;
        this.password = password;
        this.encryptor = encryptor;
        this.decryptor = decryptor;
        this.pipeline = pipeline;
    }

    /**
     * @return The username of the player.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The password of the player.
     */
    public String getPassword() {
        return password;
    }

    /**
     * @return The encryptor for encrypting game messages.
     */
    public IsaacCipher getEncryptor() {
        return encryptor;
    }

    /**
     * @return The decryptor for decrypting game messages.
     */
    public IsaacCipher getDecryptor() {
        return decryptor;
    }

    /**
     * @return The pipeline for the underlying {@link Channel}.
     */
    public ChannelPipeline getPipeline() {
        return pipeline;
    }
}
