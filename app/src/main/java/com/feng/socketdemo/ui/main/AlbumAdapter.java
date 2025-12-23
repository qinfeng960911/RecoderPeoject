package com.feng.socketdemo.ui.main;

// MainAdapter.java

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.feng.socketdemo.R;
import com.feng.socketdemo.bean.DataItem;
import com.feng.socketdemo.bean.ImageItem;

import java.util.List;

public class AlbumAdapter extends BaseQuickAdapter<DataItem, BaseViewHolder> {

    public AlbumAdapter(List<DataItem> data) {
        super(R.layout.item_main_list, data);
    }


    @Override
    protected void convert(@NonNull BaseViewHolder holder, DataItem item) {
        // 设置日期
        TextView tvDate = holder.getView(R.id.tv_date);
        tvDate.setText(item.getDate());

        // 获取内层RecyclerView
        RecyclerView innerRecyclerView = holder.getView(R.id.rv_inner_list);

        // 设置横向布局管理器
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                innerRecyclerView.getContext(),
                LinearLayoutManager.HORIZONTAL,
                false
        );
        innerRecyclerView.setLayoutManager(layoutManager);

        // 创建内层Adapter
        InnerImageAdapter innerAdapter = new InnerImageAdapter();
        innerRecyclerView.setAdapter(innerAdapter);

        // 设置内层数据
        List<ImageItem> imageItems = convertToImageItems(item.getImageUrls());
        innerAdapter.setList(imageItems);

        // 可选：添加item点击事件
        holder.itemView.setOnClickListener(v -> {
            if (getOnItemClickListener() != null) {
                getOnItemClickListener().onItemClick(this, v,
                        holder.getAdapterPosition());
            }
        });
    }

    private List<ImageItem> convertToImageItems(List<String> imageUrls) {
        List<ImageItem> imageItems = new java.util.ArrayList<>();
        for (String url : imageUrls) {
            // 这里可以根据需要设置具体的时间戳
            imageItems.add(new ImageItem(url, ""));
        }
        return imageItems;
    }


}
