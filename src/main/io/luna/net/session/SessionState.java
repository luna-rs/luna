package io.luna.net.session;


/**
 * An enumerated type whose elements represent all of the states a
 * {@link Session} can be in.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public enum SessionState {

    /**
     * The session has just been initialized.
     */
    CONNECTED,

    /**
     * The session is decoding the various states of the login protocol.
     */
    DECODE_LOGIN,

    /**
     * The session has just been queued for login, is idly awaiting login in the
     * login queue.
     */
    LOGIN_QUEUE,

    /**
     * The session has successfully been logged in.
     */
    LOGGED_IN,

    /**
     * The session has been disconnected.
     */
    DISCONNECTED
}
