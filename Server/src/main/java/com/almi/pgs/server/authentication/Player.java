package com.almi.pgs.server.authentication;

/**
 * Created by Almi on 2016-12-30.
 */
public class Player {
    private String login;
    private String password;
    private byte playerID;

    public Player(byte id, String login, String password) {
        this.login = login;
        this.password = password;
        playerID = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public byte getPlayerID() {
        return playerID;
    }
}
