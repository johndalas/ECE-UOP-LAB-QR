package com.ece.uop.labqr;

public class User {
    private String email;
    private String am;

    public User() {
    }

    public User(String email, String am) {
        this.email = email;
        this.am = am;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAm() {
        return am;
    }

    public void setAm(String am) {
        this.am = am;
    }
}
