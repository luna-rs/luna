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
     * The session is decoding the various stages of the login protocol.
     */
    LOGGING_IN,

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
     * The session has just been queued for logout, is idly awaiting logout in
     * the logout queue.
     */
    LOGOUT_QUEUE,

    /**
     * The session has successfully been logged out.
     */
    LOGGED_OUT
}
