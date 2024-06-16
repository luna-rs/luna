package io.luna.net.msg.login;

import io.luna.net.codec.IsaacCipher;
import io.netty.channel.ChannelPipeline;

/**
 * An immutable model representing data that will be used to prepare the Player for gameplay.
 *
 * @author lare96
 */
public final class LoginRequestMessage {

    /**
     * The username.
     */
    private final String username;

    /**
     * The password.
     */
    private final String password;

    /**
     * The encryptor.
     */
    private final IsaacCipher encryptor;

    /**
     * The decryptor.
     */
    private final IsaacCipher decryptor;

    /**
     * The channel pipeline.
     */
    private final ChannelPipeline pipeline;

    /**
     * Creates a new {@link LoginRequestMessage}.
     *
     * @param username The username.
     * @param password The password.
     * @param encryptor The encryptor.
     * @param decryptor The decryptor.
     * @param pipeline The channel pipeline.
     */
    public LoginRequestMessage(String username, String password, IsaacCipher encryptor, IsaacCipher decryptor,
                               ChannelPipeline pipeline) {
        this.username = username;
        this.password = password;
        this.encryptor = encryptor;
        this.decryptor = decryptor;
        this.pipeline = pipeline;
    }

    /**
     * @return The username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * @return The encryptor.
     */
    public IsaacCipher getEncryptor() {
        return encryptor;
    }

    /**
     * @return The decryptor.
     */
    public IsaacCipher getDecryptor() {
        return decryptor;
    }

    /**
     * @return The channel pipeline.
     */
    public ChannelPipeline getPipeline() {
        return pipeline;
    }
}
