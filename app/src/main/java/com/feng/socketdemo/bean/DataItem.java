package com.feng.socketdemo.bean;

import java.util.List;

// DataItem.java - 外层列表项
public class DataItem {
    private String date;  // 日期，如 "2025-01-09"
    private String time;  // 时间，如 "15:21:46"
    private List<String> imageUrls;  // 图片URL列表

    public DataItem() {
    }

    public DataItem(String date, String time, List<String> imageUrls) {
        this.date = date;
        this.time = time;
        this.imageUrls = imageUrls;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }
}
