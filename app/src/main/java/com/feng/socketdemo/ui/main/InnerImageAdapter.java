package com.feng.socketdemo.ui.main;

// InnerImageAdapter.java
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.feng.socketdemo.R;
import com.feng.socketdemo.bean.ImageItem;

public class InnerImageAdapter extends BaseQuickAdapter<ImageItem, BaseViewHolder> {

    public InnerImageAdapter() {
        super(R.layout.item_inner_image);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, ImageItem item) {
        ImageView imageView = holder.getView(R.id.iv_image);

        // 使用Glide加载图片
        Glide.with(getContext())
                .load(item.getImageUrl())
                .placeholder(R.mipmap.ic_launcher)
                .error(R.mipmap.ic_launcher)
                .into(imageView);

        // 如果item有具体时间，可以显示
        if (item.getTimestamp() != null && !item.getTimestamp().isEmpty()) {
            // 可以在这里显示图片的具体时间
        }

        // 图片点击事件
        holder.itemView.setOnClickListener(v -> {
            int position = holder.getAdapterPosition();
            if (position != RecyclerView.NO_POSITION && getOnItemClickListener() != null) {
                getOnItemClickListener().onItemClick(this, v, position);
            }
        });
    }
}
