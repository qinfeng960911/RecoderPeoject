package com.feng.socketdemo.ui.main;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.feng.socketdemo.R;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

public class PhotoViewPagerAdapter extends BaseQuickAdapter<String, BaseViewHolder> {

    private OnPhotoClickListener onPhotoClickListener;

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.onPhotoClickListener = listener;
    }

    public PhotoViewPagerAdapter(@Nullable List<String> data) {
        super(R.layout.item_photo_view, data);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, String item) {

        int position = getItemPosition(item);

        PhotoView photoView = holder.getView(R.id.photoView);

        // 使用Glide加载图片
        Glide.with(holder.itemView.getContext())
                .load(item)
                .transition(DrawableTransitionOptions.withCrossFade())
                .error(R.mipmap.ic_launcher)
                .placeholder(R.mipmap.ic_launcher)
                .into(photoView);

        // 设置图片点击监听
        photoView.setOnPhotoTapListener((view, x, y) -> {
            if (onPhotoClickListener != null) {
                onPhotoClickListener.onPhotoClick(position);
            }
        });

        // 设置缩放监听
        photoView.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
            // 可以在这里处理缩放事件
        });
    }

    public interface OnPhotoClickListener {
        void onPhotoClick(int position);
    }
}
