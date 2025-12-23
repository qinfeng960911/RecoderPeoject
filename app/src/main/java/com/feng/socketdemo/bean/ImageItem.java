package com.feng.socketdemo.bean;

// ImageItem.java - 内层图片项
public class ImageItem {
    private String imageUrl;
    private String timestamp;  // 可选：每张图片的具体时间

    public ImageItem() {
    }

    public ImageItem(String imageUrl, String timestamp) {
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}