package com.feng.socketdemo.bean;

public class LoginBean {

    private int rval;
    private int Msg_ID;
    private int Param;

    public int getRval() {
        return rval;
    }

    public void setRval(int rval) {
        this.rval = rval;
    }

    public int getMsg_ID() {
        return Msg_ID;
    }

    public void setMsg_ID(int msg_ID) {
        Msg_ID = msg_ID;
    }

    public int getParam() {
        return Param;
    }

    public void setParam(int param) {
        Param = param;
    }

    @Override
    public String toString() {
        return "LoginBean{" +
                "rval=" + rval +
                ", Msg_ID=" + Msg_ID +
                ", Param=" + Param +
                '}';
    }
}
