package com.feng.socketdemo.bean;

public class DeviceInfoBean {


    private int rval;
    private int Msg_ID;
    private int AudioRecordStatus;
    private int ParkMonitorSensity;
    private int EvtRecordSensity;
    private int Resolution;
    private int Duration;
    private int ParkMonitorTime;
    private int WaterMark;
    private int SysLanguage;
    private String SocVersion;
    private String McuVersion;
    private String HardWareVersion;
    private String SoftWareVersion;
    private String WlanInfo;

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

    public int getAudioRecordStatus() {
        return AudioRecordStatus;
    }

    public void setAudioRecordStatus(int audioRecordStatus) {
        AudioRecordStatus = audioRecordStatus;
    }

    public int getParkMonitorSensity() {
        return ParkMonitorSensity;
    }

    public void setParkMonitorSensity(int parkMonitorSensity) {
        ParkMonitorSensity = parkMonitorSensity;
    }

    public int getEvtRecordSensity() {
        return EvtRecordSensity;
    }

    public void setEvtRecordSensity(int evtRecordSensity) {
        EvtRecordSensity = evtRecordSensity;
    }

    public int getResolution() {
        return Resolution;
    }

    public void setResolution(int resolution) {
        Resolution = resolution;
    }

    public int getDuration() {
        return Duration;
    }

    public void setDuration(int duration) {
        Duration = duration;
    }

    public int getParkMonitorTime() {
        return ParkMonitorTime;
    }

    public void setParkMonitorTime(int parkMonitorTime) {
        ParkMonitorTime = parkMonitorTime;
    }

    public int getWaterMark() {
        return WaterMark;
    }

    public void setWaterMark(int waterMark) {
        WaterMark = waterMark;
    }

    public int getSysLanguage() {
        return SysLanguage;
    }

    public void setSysLanguage(int sysLanguage) {
        SysLanguage = sysLanguage;
    }

    public String getSocVersion() {
        return SocVersion;
    }

    public void setSocVersion(String socVersion) {
        SocVersion = socVersion;
    }

    public String getMcuVersion() {
        return McuVersion;
    }

    public void setMcuVersion(String mcuVersion) {
        McuVersion = mcuVersion;
    }

    public String getHardWareVersion() {
        return HardWareVersion;
    }

    public void setHardWareVersion(String hardWareVersion) {
        HardWareVersion = hardWareVersion;
    }

    public String getSoftWareVersion() {
        return SoftWareVersion;
    }

    public void setSoftWareVersion(String softWareVersion) {
        SoftWareVersion = softWareVersion;
    }

    public String getWlanInfo() {
        return WlanInfo;
    }

    public void setWlanInfo(String wlanInfo) {
        WlanInfo = wlanInfo;
    }

    @Override
    public String toString() {
        return "DeviceInfoBean{" +
                "rval=" + rval +
                ", Msg_ID=" + Msg_ID +
                ", AudioRecordStatus=" + AudioRecordStatus +
                ", ParkMonitorSensity=" + ParkMonitorSensity +
                ", EvtRecordSensity=" + EvtRecordSensity +
                ", Resolution=" + Resolution +
                ", Duration=" + Duration +
                ", ParkMonitorTime=" + ParkMonitorTime +
                ", WaterMark=" + WaterMark +
                ", SysLanguage=" + SysLanguage +
                ", SocVersion='" + SocVersion + '\'' +
                ", McuVersion='" + McuVersion + '\'' +
                ", HardWareVersion='" + HardWareVersion + '\'' +
                ", SoftWareVersion='" + SoftWareVersion + '\'' +
                ", WlanInfo='" + WlanInfo + '\'' +
                '}';
    }
}
